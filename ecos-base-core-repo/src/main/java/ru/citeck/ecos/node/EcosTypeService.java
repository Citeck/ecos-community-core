package ru.citeck.ecos.node;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.ServiceRegistry;
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
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.EcosTypeModel;
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

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@Service("ecosTypeService")
@Slf4j
public class EcosTypeService {

    public static final QName QNAME = QName.createQName("", "ecosTypeService");
    private static final RecordRef DEFAULT_TYPE = RecordRef.create("emodel", "type", "base");

    private static final String ECOS_TYPES_DOCS_ROOT_NAME = "documentLibrary";

    private EvaluatorsByAlfNode<RecordRef> evaluators;
    private PermissionService permissionService;
    private RecordsService recordsService;
    private SearchService searchService;
    private SiteService siteService;
    private NodeService nodeService;

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

    public RecordRef getEcosType(NodeRef nodeRef) {
        RecordRef result = evaluators.eval(nodeRef);
        return result != null ? result : RecordRef.EMPTY;
    }

    public RecordRef getEcosType(AlfNodeInfo nodeInfo) {
        RecordRef result = evaluators.eval(nodeInfo);
        return result != null ? result : RecordRef.EMPTY;
    }

    public List<RecordRef> getDescendantTypes(RecordRef typeRef) {
        List<RecordRef> result = new ArrayList<>();
        forEachDesc(typeRef, type -> {
            result.add(RecordRef.create("emodel", "type", type.getId()));
            return false;
        });
        return result;
    }

    public NodeRef getRootForType(RecordRef typeRef) {
        return AuthenticationUtil.runAsSystem(() -> getRootForTypeImpl(typeRef));
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
    public Long getNumberForDocument(RecordRef docRef) {

        RecordRef typeRef = recordsService.getAttribute(docRef, "_etype?id").getAs(RecordRef.class);

        if (typesManager == null || RecordRef.isEmpty(typeRef) || RecordRef.isEmpty(docRef)) {
            return null;
        }

        AtomicReference<Long> result = new AtomicReference<>();

        forEachAsc(typeRef, typeDto -> {

            RecordRef numTemplateRef = typeDto.getNumTemplateRef();

            if (RecordRef.isNotEmpty(numTemplateRef)) {

                NumTemplateDto numTemplate = typesManager.getNumTemplate(numTemplateRef);

                if (numTemplate != null) {

                    ObjectData model;
                    if (numTemplate.getModelAttributes() != null) {
                        model = recordsService.getAttributes(docRef, numTemplate.getModelAttributes()).getAttributes();
                    } else {
                        model = ObjectData.create();
                    }

                    result.set(typesManager.getNextNumber(numTemplateRef, model));
                    return true;
                }
            }

            return !typeDto.isInheritNumTemplate();
        });

        return result.get();
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

    private NodeRef getRootForTypeImpl(RecordRef typeRef) {

        // todo: add tenant support
        String currentTenant = "";

        NodeRef rootRef = FTSQuery.create()
            .exact(EcosTypeModel.PROP_TENANT, currentTenant).and()
            .exact(EcosTypeModel.PROP_ROOT_FOR_TYPE, typeRef.getId())
            .transactional()
            .queryOne(searchService)
            .orElse(null);

        if (rootRef != null) {
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
