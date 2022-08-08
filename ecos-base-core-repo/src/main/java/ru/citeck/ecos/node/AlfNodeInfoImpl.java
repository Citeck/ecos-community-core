package ru.citeck.ecos.node;

import org.alfresco.repo.security.permissions.AccessDeniedException;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

public class AlfNodeInfoImpl implements AlfNodeInfo {

    private final NodeService nodeService;
    private final PermissionService permissionService;

    private final NodeRef nodeRef;

    public AlfNodeInfoImpl(NodeRef nodeRef, ServiceRegistry serviceRegistry) {
        this.nodeRef = nodeRef;
        this.nodeService = serviceRegistry.getNodeService();
        this.permissionService = serviceRegistry.getPermissionService();
    }

    @Override
    public NodeRef getNodeRef() {
        return nodeRef;
    }

    @Override
    public QName getType() {
        return nodeService.getType(nodeRef);
    }

    @Override
    public Map<QName, Serializable> getProperties() {
        if (AccessStatus.ALLOWED.equals(permissionService.hasPermission(nodeRef, PermissionService.READ))) {
            return nodeService.getProperties(nodeRef);
        }
        return Collections.emptyMap();
    }
}
