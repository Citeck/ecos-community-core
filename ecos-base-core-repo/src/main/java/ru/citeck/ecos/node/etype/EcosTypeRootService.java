package ru.citeck.ecos.node.etype;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.EcosTypeModel;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.utils.NodeUtils;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class EcosTypeRootService {

    private static final String ECOS_TYPES_DOCS_ROOT_NAME = "documentLibrary";

    private final SearchService searchService;
    private final SiteService siteService;
    private final NodeService nodeService;
    private final PermissionService permissionService;
    private final EcosTypeService ecosTypeService;
    private final NodeUtils nodeUtils;

    private final Map<String, NodeRef> nodeByPath = new ConcurrentHashMap<>();

    @Autowired
    public EcosTypeRootService(
        NodeUtils nodeUtils,
        SearchService searchService,
        SiteService siteService,
        NodeService nodeService,
        PermissionService permissionService,
        EcosTypeService ecosTypeService
    ) {
        this.nodeUtils = nodeUtils;
        this.ecosTypeService = ecosTypeService;
        this.searchService = searchService;
        this.siteService = siteService;
        this.nodeService = nodeService;
        this.permissionService = permissionService;
    }

    public NodeRef getRootForType(EntityRef typeRef, boolean createIfNotExists) {
        return AuthenticationUtil.runAsSystem(() -> getRootForTypeImpl(typeRef, createIfNotExists));
    }

    private NodeRef getRootForTypeImpl(EntityRef typeRef, boolean createIfNotExists) {

        // todo: add tenant support
        String currentTenant = "";

        ObjectData typeProps = ecosTypeService.getResolvedProperties(typeRef);
        String alfRootPath = typeProps.get("alfRoot").asText();

        if (StringUtils.isNotBlank(alfRootPath)) {

            return nodeByPath.computeIfAbsent(alfRootPath, rootPath -> {

                NodeRef rootRef = nodeUtils.getNodeRef(rootPath);

                if (createIfNotExists && !nodeService.hasAspect(rootRef, EcosTypeModel.ASPECT_TYPE_ROOT)) {

                    setTypesRootPermissions(rootRef);
                    Map<QName, Serializable> props = new HashMap<>();
                    props.put(EcosTypeModel.PROP_TENANT, "");
                    props.put(EcosTypeModel.PROP_ROOT_FOR_TYPE, typeRef.getLocalId());

                    nodeService.addAspect(rootRef, EcosTypeModel.ASPECT_TYPE_ROOT, props);
                }

                return rootRef;
            });
        }

        NodeRef rootRef = FTSQuery.create()
            .exact(EcosTypeModel.PROP_TENANT, currentTenant).and()
            .exact(EcosTypeModel.PROP_ROOT_FOR_TYPE, typeRef.getLocalId())
            .transactional()
            .queryOne(searchService)
            .orElse(null);

        if (rootRef != null || !createIfNotExists) {
            return rootRef;
        }

        String tenantSiteName = "tenant_" + currentTenant;

        SiteInfo site = siteService.getSite(tenantSiteName);
        if (site == null) {
            log.info("Create new site for tenant: '" + currentTenant + "'");
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
        if (!nodeService.hasAspect(siteRoot, EcosTypeModel.ASPECT_TENANT_SITE)) {
            nodeService.addAspect(siteRoot, EcosTypeModel.ASPECT_TENANT_SITE, new HashMap<>());
        }

        NodeRef typesFolder = findOrCreateFolder(
            siteRoot,
            ECOS_TYPES_DOCS_ROOT_NAME,
            null,
            null,
            true
        );

        Map<QName, Serializable> props = new HashMap<>();
        props.put(EcosTypeModel.PROP_ROOT_FOR_TYPE, typeRef.getLocalId());
        props.put(EcosTypeModel.PROP_TENANT, currentTenant);

        return findOrCreateFolder(typesFolder, typeRef.getLocalId(), props, props, false);
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
            setTypesRootPermissions(result);
        }

        return result;
    }

    private void setTypesRootPermissions(NodeRef rootRef) {

        permissionService.setInheritParentPermissions(rootRef, false);

        Arrays.asList(
            PermissionService.ADD_CHILDREN,
            PermissionService.CREATE_CHILDREN,
            PermissionService.READ
        ).forEach(permission ->
            permissionService.setPermission(rootRef, "GROUP_EVERYONE", permission, true)
        );
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
}
