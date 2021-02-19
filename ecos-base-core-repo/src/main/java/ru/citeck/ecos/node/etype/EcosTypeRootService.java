package ru.citeck.ecos.node.etype;

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
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.model.EcosTypeModel;
import ru.citeck.ecos.model.lib.type.service.TypeDefService;
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.utils.NodeUtils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class EcosTypeRootService {

    private static final String ECOS_TYPES_DOCS_ROOT_NAME = "documentLibrary";

    private final SearchService searchService;
    private final SiteService siteService;
    private final NodeService nodeService;
    private final PermissionService permissionService;
    private final TypeDefService typeDefService;
    private final NodeUtils nodeUtils;

    private final Map<RecordRef, String> rootPathByType = new ConcurrentHashMap<>();
    private final Map<String, NodeRef> rootByPath = new ConcurrentHashMap<>();

    @Autowired
    public EcosTypeRootService(
        NodeUtils nodeUtils,
        SearchService searchService,
        SiteService siteService,
        NodeService nodeService,
        PermissionService permissionService,
        TypeDefService typeDefService
    ) {
        this.nodeUtils = nodeUtils;
        this.typeDefService = typeDefService;
        this.searchService = searchService;
        this.siteService = siteService;
        this.nodeService = nodeService;
        this.permissionService = permissionService;
    }

    public NodeRef getRootForType(RecordRef typeRef, boolean createIfNotExists) {
        return AuthenticationUtil.runAsSystem(() -> getRootForTypeImpl(typeRef, createIfNotExists));
    }

    private NodeRef getRootForTypeImpl(RecordRef typeRef, boolean createIfNotExists) {

        // todo: add tenant support
        String currentTenant = "";

        String path = rootPathByType.get(typeRef);
        if (path == null) {
            AtomicReference<String> rootPath = new AtomicReference<>();
            typeDefService.forEachAsc(typeRef, typeDef -> {
                RecordRef parentRef = TypeUtils.getTypeRef(typeDef.getId());
                rootPath.set(rootPathByType.get(parentRef));
                return StringUtils.isNotBlank(rootPath.get());
            });
            path = rootPath.get();
            if (StringUtils.isNotBlank(path)) {
                rootPathByType.put(typeRef, path);
            }
        }

        if (path != null) {

            return rootByPath.computeIfAbsent(path, rootPath -> {

                NodeRef rootRef = nodeUtils.getNodeRef(rootPath);

                if (createIfNotExists && !nodeService.hasAspect(rootRef, EcosTypeModel.ASPECT_TYPE_ROOT)) {

                    setTypesRootPermissions(rootRef);
                    Map<QName, Serializable> props = new HashMap<>();
                    props.put(EcosTypeModel.PROP_TENANT, currentTenant);
                    props.put(EcosTypeModel.PROP_ROOT_FOR_TYPE, typeRef.getId());

                    nodeService.addAspect(rootRef, EcosTypeModel.ASPECT_TYPE_ROOT, props);
                }

                return rootRef;
            });
        }

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

    public void registerRoot(@NotNull RecordRef typeRef, @NotNull String path) {
        rootPathByType.put(typeRef, path);
    }
}
