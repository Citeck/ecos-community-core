package ru.citeck.ecos.icase.activity.service.eproc.importer;

import com.hazelcast.util.ConcurrentHashSet;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.dictionary.ClassDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.cmmn.CMMNUtils;
import ru.citeck.ecos.cmmn.model.Case;
import ru.citeck.ecos.cmmn.model.Definitions;
import ru.citeck.ecos.cmmn.model.Stage;
import ru.citeck.ecos.cmmn.service.util.CaseElementImport;
import ru.citeck.ecos.cmmn.service.util.CaseRolesImport;
import ru.citeck.ecos.icase.activity.service.eproc.EProcActivityService;
import ru.citeck.ecos.icase.activity.service.eproc.importer.pojo.OptimizedProcessDefinition;
import ru.citeck.ecos.icase.element.CaseElementService;
import ru.citeck.ecos.model.RequirementModel;
import ru.citeck.ecos.records.RecordsUtils;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.utils.NodeUtils;

import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
public class EProcCaseImporter {

    private final EProcActivityService eprocActivityService;

    private final NodeService nodeService;
    private final DictionaryService dictionaryService;
    private final AuthorityService authorityService;
    private final CaseElementService caseElementService;
    private final CMMNUtils utils;

    private final Set<RecordRef> allowedEcosTypes = new ConcurrentHashSet<>();
    private final Set<QName> allowedAlfTypes = new ConcurrentHashSet<>();

    @Autowired
    public EProcCaseImporter(EProcActivityService eprocActivityService,
                             NodeService nodeService,
                             DictionaryService dictionaryService,
                             AuthorityService authorityService,
                             CaseElementService caseElementService,
                             CMMNUtils utils) {

        this.eprocActivityService = eprocActivityService;
        this.nodeService = nodeService;
        this.dictionaryService = dictionaryService;
        this.authorityService = authorityService;
        this.caseElementService = caseElementService;
        this.utils = utils;
    }

    public boolean importCase(RecordRef caseRef) {
        Optional<Pair<String, OptimizedProcessDefinition>> def =
            eprocActivityService.getOptimizedDefinitionWithRevisionId(caseRef);
        if (def.isPresent()) {
            importCaseImpl(caseRef, def.get());
            return true;
        } else {
            return false;
        }
    }

    private void importCaseImpl(RecordRef caseRef, Pair<String, OptimizedProcessDefinition> data) {

        NodeRef caseNodeRef = null;
        if (caseRef.getId().contains(NodeUtils.WORKSPACE_PREFIX)) {
            caseNodeRef = RecordsUtils.toNodeRef(caseRef);
        }

        String revisionId = data.getFirst();
        OptimizedProcessDefinition optimizedProcessDefinition = data.getSecond();

        Definitions definitions = optimizedProcessDefinition.getXmlProcessDefinition();
        if (definitions == null || CollectionUtils.isEmpty(definitions.getCase())) {
            throw new RuntimeException("Definition is absent for caseRef = " + caseRef);
        }

        Case caseItem = definitions.getCase().get(0);

        if (caseNodeRef != null) {

            CaseRolesImport caseRolesImport = new CaseRolesImport(nodeService, authorityService, utils);
            caseRolesImport.importRoles(caseNodeRef, caseItem.getCaseRoles());

            CaseElementImport caseElementImport = new CaseElementImport(caseElementService);
            caseElementImport.importCaseElementTypes(caseNodeRef, caseItem);

            importCaseCompletenessLevels(caseNodeRef, caseItem.getCasePlanModel());
        }

        eprocActivityService.createDefaultState(caseRef, revisionId, optimizedProcessDefinition);
    }

    private void importCaseCompletenessLevels(NodeRef caseNodeRef, Stage casePlanModel) {
        if (casePlanModel.getOtherAttributes().get(CMMNUtils.QNAME_COMPLETNESS_LEVELS) != null) {
            String completnessLevelsString = casePlanModel.getOtherAttributes().get(CMMNUtils.QNAME_COMPLETNESS_LEVELS);
            String[] completnessLevelsArray = completnessLevelsString.split(",");
            for (String comletnessLevel : completnessLevelsArray) {
                NodeRef nodeRef = utils.extractNodeRefFromCmmnId(comletnessLevel);
                if (!nodeService.exists(nodeRef)) {
                    log.error("Completness level with nodeRef = " + nodeRef + " doesn't exists! CaseRef=" + caseNodeRef);
                    continue;
                }
                nodeService.createAssociation(caseNodeRef, nodeRef, RequirementModel.ASSOC_COMPLETENESS_LEVELS);
            }
        }
    }

    public void registerEcosType(RecordRef typeRef) {
        allowedEcosTypes.add(typeRef);
    }

    public void registerAlfrescoType(QName typeQName) {
        allowedAlfTypes.add(typeQName);
    }

    private boolean isAlfrescoTypeEnabled(QName caseType) {
        ClassDefinition typeDef = dictionaryService.getClass(caseType);
        while (typeDef != null) {
            if (allowedAlfTypes.contains(typeDef.getName())) {
                return true;
            }
            typeDef = typeDef.getParentClassDefinition();
        }
        return false;
    }

}
