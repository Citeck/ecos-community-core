package ru.citeck.ecos.node;

import org.alfresco.repo.security.permissions.AccessDeniedException;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

public class AlfNodeInfoImpl implements AlfNodeInfo {

    private final NodeService nodeService;

    private final NodeRef nodeRef;

    public AlfNodeInfoImpl(NodeRef nodeRef, ServiceRegistry serviceRegistry) {
        this.nodeRef = nodeRef;
        this.nodeService = serviceRegistry.getNodeService();
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
        try {
            return nodeService.getProperties(nodeRef);
        } catch (AccessDeniedException e) {
            return Collections.emptyMap();
        }
    }
}
