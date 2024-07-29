package ru.citeck.ecos.behavior

import mu.KotlinLogging
import org.alfresco.repo.node.NodeServicePolicies.*
import org.alfresco.repo.policy.Behaviour.NotificationFrequency.EVERY_EVENT
import org.alfresco.service.cmr.dictionary.AssociationDefinition
import org.alfresco.service.cmr.repository.AssociationRef
import org.alfresco.service.cmr.repository.ChildAssociationRef
import org.alfresco.service.cmr.repository.NodeRef
import org.alfresco.service.cmr.repository.StoreRef
import org.alfresco.service.namespace.NamespaceService
import org.alfresco.service.namespace.QName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.DependsOn
import ru.citeck.ecos.behavior.base.AbstractBehaviour
import ru.citeck.ecos.behavior.base.PolicyMethod
import ru.citeck.ecos.context.lib.auth.AuthContext
import ru.citeck.ecos.domain.model.alf.service.AlfAutoModelService
import ru.citeck.ecos.records.type.TypesManager
import ru.citeck.ecos.records2.RecordConstants
import ru.citeck.ecos.records2.RecordRef
import ru.citeck.ecos.records3.RecordsService
import ru.citeck.ecos.utils.DictUtils
import ru.citeck.ecos.utils.NodeUtils
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.constants.AppName
import ru.citeck.ecos.webapp.api.entity.EntityRef
import java.io.Serializable
import java.util.*
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashSet

@DependsOn("idocs.dictionaryBootstrap")
class AssocMirroringBehaviour : AbstractBehaviour(),
    OnCreateNodePolicy,
    OnUpdatePropertiesPolicy,
    OnCreateAssociationPolicy,
    OnDeleteAssociationPolicy
{
    companion object {
        private const val ATT_SYS_NODE_UUID = "sys:node-uuid"

        private val log = KotlinLogging.logger {}
    }

    private lateinit var typesManager: TypesManager
    private lateinit var alfAutoModelService: AlfAutoModelService
    private lateinit var webAppApi: EcosWebAppApi
    private lateinit var namespaceService: NamespaceService
    private lateinit var nodeUtils: NodeUtils
    private lateinit var dictUtils: DictUtils
    private lateinit var recordsService: RecordsService

    private var newAssocName: String = ""
    private var createOldIfNotExists: Boolean = false
    private var oldToNewAttsMapping: Map<String, String> = emptyMap()

    private var ecosType: EntityRef = EntityRef.EMPTY

    private var newAttsToQNamesMapping: Map<String, QName> = Collections.emptyMap()
    private val attsToQNameLastLoadingTime = AtomicLong()

    @Synchronized
    private fun getNewAttsToQNamesMapping(): Map<String, QName> {

        if (System.currentTimeMillis() - attsToQNameLastLoadingTime.get() < 60_000) {
            return newAttsToQNamesMapping
        }

        val attsForMapping = LinkedHashSet<String>()
        attsForMapping.add(newAssocName)

        val mapping = alfAutoModelService.getPropsMapping(
            RecordRef.valueOf(getEcosType()),
            attsForMapping,
            true
        )

        val resultMap = LinkedHashMap<String, QName>()
        for (att in attsForMapping) {
            resultMap[att] = QName.resolveToQName(namespaceService, mapping[att] ?: att)
        }

        newAttsToQNamesMapping = resultMap
        attsToQNameLastLoadingTime.set(System.currentTimeMillis())

        return newAttsToQNamesMapping
    }

    private fun getEcosType(): EntityRef {
        if (ecosType.isEmpty()) {
            ecosType = typesManager.getEcosTypeByAlfType(className)
            if (ecosType == RecordRef.EMPTY) {
                error("ECOS Type can't be calculated from alfresco type $className")
            }
        }
        return ecosType
    }

    override fun beforeInit() {
        if (assocName == null) {
            error("assocName is null")
        }
        if (newAssocName.isBlank()) {
            error("newAssocName is blank")
        }
    }

    @PolicyMethod(policy = OnCreateNodePolicy::class, frequency = EVERY_EVENT, runAsSystem = true)
    override fun onCreateNode(childAssocRef: ChildAssociationRef) {
        if (!webAppApi.isReady()) {
            return
        }
        syncAttributes(childAssocRef.childRef, null, null)
    }

    @PolicyMethod(policy = OnUpdatePropertiesPolicy::class, frequency = EVERY_EVENT, runAsSystem = true)
    override fun onUpdateProperties(
        nodeRef: NodeRef,
        before: MutableMap<QName, Serializable>,
        after: MutableMap<QName, Serializable>
    ) {
        if (!webAppApi.isReady()) {
            return
        }
        val newAssocName = getNewAttsToQNamesMapping()[newAssocName]
        if (before[newAssocName] != after[newAssocName]) {
            syncAttributes(nodeRef, null, after[newAssocName] as? String ?: "" )
        }
    }

    @PolicyMethod(policy = OnCreateAssociationPolicy::class, frequency = EVERY_EVENT, runAsSystem = true)
    override fun onCreateAssociation(nodeAssocRef: AssociationRef) {
        if (!webAppApi.isReady()) {
            return
        }
        if (nodeAssocRef.typeQName == assocName) {
            syncAttributes(nodeAssocRef.sourceRef, nodeAssocRef.targetRef.toString(), null)
        }
    }

    @PolicyMethod(policy = OnDeleteAssociationPolicy::class, frequency = EVERY_EVENT, runAsSystem = true)
    override fun onDeleteAssociation(nodeAssocRef: AssociationRef) {
        if (!webAppApi.isReady()) {
            return
        }
        if (nodeAssocRef.typeQName == assocName) {
            syncAttributes(nodeAssocRef.sourceRef, "", null)
        }
    }

    private fun syncAttributes(
        nodeRef: NodeRef,
        // null if assoc is unknown. empty string if assoc is empty
        assocValue: String?,
        // null if prop is unknown. empty string if prop is empty
        propValue: String?
    ) {
        val newAttsToQNameMapping = getNewAttsToQNamesMapping()
        val newAssocQName = newAttsToQNameMapping[newAssocName] ?: error("QName doesn't found for $newAssocName")
        val nnPropValue = propValue ?: nodeService.getProperty(nodeRef, newAssocQName) as? String ?: ""
        val nnAssocValue = assocValue ?: nodeUtils.getAssocTarget(
            nodeRef,
            newAssocQName
        ).map { v -> v.toString() }.orElse("")

        val propRef = EntityRef.valueOf(nnPropValue)
        val assocRef = EntityRef.create(AppName.ALFRESCO, "", nnAssocValue)

        if (assocRef.getLocalId() == propRef.getLocalId()) {
            return
        }
        if (propRef.getLocalId().isNotEmpty()) {
            val expectedAssocRef = NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, propRef.getLocalId())
            if (!nodeUtils.isValidNode(expectedAssocRef)) {
                if (!createOldIfNotExists) {
                    error("New ref '$propRef' can't be mapped to old '$expectedAssocRef' because nodeRef doesn't exists")
                }
                val attsToLoad = oldToNewAttsMapping.values.associateWith {
                    if (!it.contains('?')) "$it?raw" else it
                }
                val loadedAtts = AuthContext.runAsSystem {
                    recordsService.getAtts(propRef, attsToLoad).getAtts()
                }
                val assocDef = dictUtils.getAttDefinition(assocName.toString()) as? AssociationDefinition
                    ?: error("Unknown assoc: $assocName")

                val assocTargetEcosType = typesManager.getEcosTypeByAlfType(assocDef.targetClass.name)
                if (assocTargetEcosType == EntityRef.EMPTY) {
                    error("Assoc target class '${assocDef.targetClass.name}' doesn't have registered ECOS type")
                }

                val props = HashMap<String, Any?>()
                props[ATT_SYS_NODE_UUID] = propRef.getLocalId()
                props[RecordConstants.ATT_TYPE] = assocTargetEcosType

                for ((k, v) in oldToNewAttsMapping) {
                    val loadedAtt = loadedAtts[v]
                    if (loadedAtt.isNotEmpty()) {
                        props[k] = loadedAtt
                    }
                }
                AuthContext.runAsSystem {
                    recordsService.mutate(EntityRef.create(AppName.ALFRESCO, "", ""), props)
                }
            }
            log.debug {
                val oldAssocShortName = assocName.toPrefixString(namespaceService)
                val newAssocShortName = newAssocQName.toPrefixString(namespaceService)
                "Mirror assoc $newAssocShortName=$propRef to $oldAssocShortName=$expectedAssocRef for ref $nodeRef"
            }
            AuthContext.runAsSystem {
                nodeUtils.createAssoc(nodeRef, expectedAssocRef, assocName)
            }
        }
    }

    fun setNewAssocName(newAssocName: String) {
        this.newAssocName = newAssocName
    }

    fun setOldToNewAttsMapping(oldToNewAttsMapping: Map<String, String>) {
        this.oldToNewAttsMapping = oldToNewAttsMapping
    }

    fun setEcosTypeId(typeId: String) {
        this.ecosType = EntityRef.create(AppName.EMODEL, "type", typeId)
    }

    fun setCreateOldIfNotExists(createOldIfNotExists: Boolean) {
        this.createOldIfNotExists = createOldIfNotExists
    }

    @Autowired
    fun setDictUtils(dictUtils: DictUtils) {
        this.dictUtils = dictUtils
    }

    @Autowired
    fun setTypesManager(typesManager: TypesManager) {
        this.typesManager = typesManager
    }

    @Autowired
    fun setNamespaceService(namespaceService: NamespaceService) {
        this.namespaceService = namespaceService
    }

    @Autowired
    fun setAlfAutoModelService(alfAutoModelService: AlfAutoModelService) {
        this.alfAutoModelService = alfAutoModelService
    }

    @Autowired
    fun setWebAppApi(webAppApi: EcosWebAppApi) {
        this.webAppApi = webAppApi
    }

    @Autowired
    fun setNodeUtils(nodeUtils: NodeUtils) {
        this.nodeUtils = nodeUtils
    }

    @Autowired
    fun setRecordsService(recordsService: RecordsService) {
        this.recordsService = recordsService
    }
}
