package ru.citeck.ecos.node;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.alfresco.service.cmr.site.SiteVisibility;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.ClassificationModel;
import ru.citeck.ecos.model.EcosTypeModel;
import ru.citeck.ecos.model.lib.ModelServiceFactory;
import ru.citeck.ecos.records.type.NumTemplateDto;
import ru.citeck.ecos.records.type.TypeDto;
import ru.citeck.ecos.records.type.TypesManager;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.Predicates;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.utils.DictUtils;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;

@Service("ecosTypeService")
@Slf4j
public class EcosTypeService {

    public static final QName QNAME = QName.createQName("", "ecosTypeService");
    private static final RecordRef DEFAULT_TYPE = RecordRef.create("emodel", "type", "base");

    private static final String ECOS_TYPES_DOCS_ROOT_NAME = "documentLibrary";

    private EvaluatorsByAlfNode<RecordRef> evaluators;
    private PermissionService permissionService;
    private ModelServiceFactory modelServices;
    private RecordsService recordsService;
    private SearchService searchService;
    private SiteService siteService;
    private NodeService nodeService;
    private DictUtils dictUtils;

    private TypesManager typesManager;

    @Autowired
    public EcosTypeService(PermissionService permissionService,
                           ServiceRegistry serviceRegistry,
                           RecordsService recordsService,
                           SearchService searchService,
                           NodeService nodeService,
                           SiteService siteService) {

        evaluators = new EvaluatorsByAlfNode<>(serviceRegistry, node -> DEFAULT_TYPE);
        this.permissionService = permissionService;
        this.recordsService = recordsService;
        this.searchService = searchService;
        this.nodeService = nodeService;
        this.siteService = siteService;
    }

    public void register(QName nodeType, Function<AlfNodeInfo, RecordRef> evaluator) {
        evaluators.register(nodeType, evaluator);
    }

    @Nullable
    public TypeDto getTypeDef(RecordRef typeRef) {
        if (typesManager == null) {
            return null;
        }
        return typesManager.getType(typeRef);
    }

    public RecordRef getEcosType(NodeRef nodeRef) {
        RecordRef result = evaluators.eval(nodeRef);
        return result != null ? result : RecordRef.EMPTY;
    }

    public RecordRef getEcosType(AlfNodeInfo nodeInfo) {
        RecordRef result = evaluators.eval(nodeInfo);
        return result != null ? result : RecordRef.EMPTY;
    }

    public RecordRef getEcosType(String alfrescoType) {
        PropertyDefinition propDef = dictUtils.getPropDef(alfrescoType, ClassificationModel.PROP_DOCUMENT_TYPE);
        if (propDef == null) {
            return null;
        }

        String value = propDef.getDefaultValue();
        if (!StringUtils.isNotBlank(value) || !NodeRef.isNodeRef(value)) {
            return null;
        }

        NodeRef typeNodeRef = new NodeRef(value);
        return RecordRef.create("emodel", "type", typeNodeRef.getId());
    }

    public List<RecordRef> getDescendantTypes(RecordRef typeRef) {
        List<RecordRef> result = new ArrayList<>();
        forEachDesc(typeRef, type -> {
            result.add(RecordRef.create("emodel", "type", type.getId()));
            return false;
        });
        return result;
    }

    public NodeRef getRootForType(RecordRef typeRef, boolean createIfNotExists) {
        return AuthenticationUtil.runAsSystem(() -> getRootForTypeImpl(typeRef, createIfNotExists));
    }

    public <T> T getEcosTypeConfig(RecordRef configRef, Class<T> configClass) {
        EcosTypeConfig typeConfig = recordsService.getMeta(configRef, EcosTypeConfig.class);
        return typeConfig.getData().getAs(configClass);
    }

    public <T> T getEcosTypeConfig(NodeRef documentRef, Class<T> configClass) {
        RecordRef ecosType = getEcosType(documentRef);
        return getEcosTypeConfig(ecosType, configClass);
    }

    @Nullable
    public Long getNumberForDocument(@NotNull RecordRef docRef) {
        return getNumberForDocument(docRef, null);
    }

    @Nullable
    public Long getNumberForDocument(@NotNull RecordRef docRef, @Nullable RecordRef numTemplateRef) {

        if (typesManager == null) {
            throw new IllegalStateException("typesManager is null");
        }

        if (numTemplateRef == null) {
            numTemplateRef = getNumTemplateByRecord(docRef);
            if (RecordRef.isEmpty(numTemplateRef)) {
                return null;
            }
        }

        NumTemplateDto numTemplate = typesManager.getNumTemplate(numTemplateRef);
        if (numTemplate == null) {
            throw new IllegalStateException("Number template is not found for ref: '" + numTemplateRef + "'");
        }

        ObjectData model;
        if (numTemplate.getModelAttributes() != null) {
            model = recordsService.getAttributes(docRef, numTemplate.getModelAttributes()).getAttributes();
        } else {
            model = ObjectData.create();
        }

        return typesManager.getNextNumber(numTemplateRef, model);
    }

    @Nullable
    public RecordRef getNumTemplateByTypeRef(@Nullable RecordRef typeRef) {
        if (typeRef == null || RecordRef.isEmpty(typeRef)) {
            return null;
        }
        return modelServices.getTypeDefService().getNumTemplate(typeRef);
    }

    @Nullable
    private RecordRef getNumTemplateByRecord(RecordRef recordRef) {
        if (RecordRef.isEmpty(recordRef)) {
            return null;
        }
        RecordRef typeRef = recordsService.getAttribute(recordRef, "_type?id").getAs(RecordRef.class);
        return getNumTemplateByTypeRef(typeRef);
    }

    public void forEachDesc(RecordRef typeRef, Function<TypeDto, Boolean> action) {

        if (RecordRef.isEmpty(typeRef) || typesManager == null) {
            return;
        }
        forEachDesc(Collections.singletonList(typeRef), action);
    }

    public void forEachAscRef(RecordRef typeRef, Function<RecordRef, Boolean> action) {
        forEachAsc(typeRef, dto -> {
            RecordRef ref = RecordRef.create("emodel", "type", dto.getId());
            return action.apply(ref);
        });
    }

    public List<RecordRef> getChildren(RecordRef typeRef) {

        RecordsQuery query = new RecordsQuery();
        query.setSourceId("emodel/type");
        query.setQuery(Predicates.eq("parentRef", typeRef.toString()));
        query.setLanguage(PredicateService.LANGUAGE_PREDICATE);

        return recordsService.queryRecords(query).getRecords();
    }

    private void forEachDesc(List<RecordRef> types, Function<TypeDto, Boolean> action) {

        for (RecordRef type : types) {

            if (RecordRef.isEmpty(type)) {
                continue;
            }

            TypeDto typeDto = typesManager.getType(type);
            if (typeDto != null && action.apply(typeDto)) {
                return;
            }

            forEachDesc(getChildren(type), action);
        }
    }

    public void forEachAsc(RecordRef typeRef, Function<TypeDto, Boolean> action) {

        if (RecordRef.isEmpty(typeRef) || typesManager == null) {
            return;
        }

        TypeDto typeDto = typesManager.getType(typeRef);

        if (typeDto == null) {
            return;
        }

        while (typeDto != null && !action.apply(typeDto)) {
            typeDto = typeDto.getParentRef() != null ? typesManager.getType(typeDto.getParentRef()) : null;
        }
    }

    private NodeRef getRootForTypeImpl(RecordRef typeRef, boolean createIfNotExists) {

        // todo: add tenant support
        String currentTenant = "";

        NodeRef rootRef = FTSQuery.create()
            .exact(EcosTypeModel.PROP_TENANT, currentTenant).and()
            .exact(EcosTypeModel.PROP_ROOT_FOR_TYPE, typeRef.getId())
            .transactional()
            .queryOne(searchService)
            .orElse(null);

        if (rootRef != null || !createIfNotExists) {
            return rootRef;
        }

        String tenantSiteName = "tenant_" + currentTenant;

        SiteInfo site = siteService.getSite(tenantSiteName);
        if (site == null) {
            String title = "Site for tenant '" + currentTenant + "'";
            site = siteService.createSite(
                "document-site-dashboard",
                tenantSiteName,
                title,
                title,
                SiteVisibility.PRIVATE
            );
        }

        NodeRef siteRoot = site.getNodeRef();
        nodeService.addAspect(siteRoot, EcosTypeModel.ASPECT_TENANT_SITE, new HashMap<>());

        NodeRef typesFolder = findOrCreateFolder(
            siteRoot,
            ECOS_TYPES_DOCS_ROOT_NAME,
            null,
            null,
            true
        );

        Map<QName, Serializable> props = new HashMap<>();
        props.put(EcosTypeModel.PROP_ROOT_FOR_TYPE, typeRef.getId());
        props.put(EcosTypeModel.PROP_TENANT, currentTenant);

        return findOrCreateFolder(typesFolder, typeRef.getId(), props, props, false);
    }

    private NodeRef findOrCreateFolder(NodeRef parent,
                                       String name,
                                       Map<QName, Serializable> props,
                                       Map<QName, Serializable> expectedProps,
                                       boolean isTypesRoot) {

        name = getValidName(name);

        if (StringUtils.isBlank(name)) {
            name = "dir";
        }

        NodeRef childByName = nodeService.getChildByName(parent, ContentModel.ASSOC_CONTAINS, name);

        if (childByName != null && expectedProps != null) {
            Map<QName, Serializable> childProps = nodeService.getProperties(childByName);
            int nameCounter = 1;
            while (childByName != null && !isAllMatch(childProps, expectedProps)) {
                name = name + "_" + nameCounter++;
                childByName = nodeService.getChildByName(parent, ContentModel.ASSOC_CONTAINS, name);
                childProps = childByName != null ? nodeService.getProperties(childByName) : null;
            }
        }
        if (childByName != null) {
            return childByName;
        }

        if (props == null) {
            props = new HashMap<>();
        } else {
            props = new HashMap<>(props);
        }

        QName assocName = QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, name);
        props.put(ContentModel.PROP_NAME, name);

        NodeRef result = nodeService.createNode(
            parent,
            ContentModel.ASSOC_CONTAINS,
            assocName,
            ContentModel.TYPE_FOLDER,
            props
        ).getChildRef();

        if (isTypesRoot) {
            permissionService.setInheritParentPermissions(result, false);
            permissionService.setPermission(
                result,
                "GROUP_EVERYONE",
                PermissionService.ADD_CHILDREN,
                true
            );
            permissionService.setPermission(
                result,
                "GROUP_EVERYONE",
                PermissionService.CREATE_CHILDREN,
                true
            );
        }

        return result;
    }

    private String getValidName(String name) {
        //todo: add transliteration
        return name.replaceAll("[^a-zA-Z-_0-9]", "_").trim();
    }

    private boolean isAllMatch(Map<QName, Serializable> baseProps, Map<QName, Serializable> expectedProps) {
        if (baseProps == null) {
            return false;
        }
        if (expectedProps == null || expectedProps.isEmpty()) {
            return true;
        }
        return expectedProps.entrySet()
            .stream()
            .allMatch(it -> Objects.equals(it.getValue(), baseProps.get(it.getKey())));
    }

    @Lazy
    @Autowired
    public void setModelServices(ModelServiceFactory modelServices) {
        this.modelServices = modelServices;
    }

    @Autowired(required = false)
    public void setTypesManager(TypesManager typesManager) {
        this.typesManager = typesManager;
    }

    @Data
    private static final class EcosTypeConfig {
        @MetaAtt("config")
        ObjectData data;
    }
}
