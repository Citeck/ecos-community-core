package ru.citeck.ecos.security;

import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.node.AlfNodeInfo;
import ru.citeck.ecos.node.AlfNodeInfoImpl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class EcosPermissionService {

    public static final QName QNAME = QName.createQName("", "ecosPermissionService");

    private static final String EDIT_MODE = "edit";

    /**
     * @deprecated use EcosPermissionComponent instead
     */
    @Deprecated
    private AttributesPermissionServiceResolver attsPermServiceResolver;

    private EcosPermissionComponent ecosPermissionComponent;

    private NamespaceService namespaceService;
    private ServiceRegistry serviceRegistry;

    private Set<QName> protectedAttributes = new HashSet<>();

    public boolean isAttributeProtected(NodeRef nodeRef, String attributeName) {

        if (nodeRef == null || StringUtils.isBlank(attributeName)) {
            return false;
        }

        return isAttProtected(new AlfNodeInfoImpl(nodeRef, serviceRegistry), attributeName);
    }

    public boolean isAttProtected(AlfNodeInfo node, String attributeName) {

        if (node == null || StringUtils.isBlank(attributeName)) {
            return false;
        }

        QName attQName;
        if (attributeName.equals("_type") || attributeName.equals("_etype")) {
            attQName = QName.createQName("", attributeName);
        } else {
            attQName = QName.resolveToQName(namespaceService, attributeName);
        }
        if (attQName == null) {
            return false;
        }
        if (protectedAttributes.contains(attQName)) {
            return true;
        }

        if (ecosPermissionComponent != null) {
            return ecosPermissionComponent.isAttProtected(node, attributeName);
        }

        if (attsPermServiceResolver == null) {
            return false;
        }

        AttributesPermissionService attrsPermissionService = attsPermServiceResolver.resolve(node.getNodeRef());

        if (attrsPermissionService == null) {
            return false;
        }

        return !attrsPermissionService.isFieldEditable(attQName, node.getNodeRef(), EDIT_MODE);
    }

    public boolean isAttVisible(AlfNodeInfo info, String attributeName) {

        if (info == null || StringUtils.isBlank(attributeName) || ecosPermissionComponent == null) {
            return true;
        }

        return ecosPermissionComponent.isAttVisible(info, attributeName);
    }

    @Autowired(required = false)
    public void setAttsPermServiceResolver(AttributesPermissionServiceResolver attsPermServiceResolver) {
        this.attsPermServiceResolver = attsPermServiceResolver;
    }

    @Autowired(required = false)
    public void setEcosPermissionComponent(EcosPermissionComponent ecosPermissionComponent) {
        this.ecosPermissionComponent = ecosPermissionComponent;
    }

    @Autowired
    public void setNamespaceService(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    public void setProtectedAttributes(List<QName> protectedAttributes) {
        this.protectedAttributes = new HashSet<>(protectedAttributes);
    }

    public void addProtectedAttributes(List<QName> protectedAttributes) {
        Set<QName> newAtts = new HashSet<>(this.protectedAttributes);
        newAtts.addAll(protectedAttributes);
        this.protectedAttributes = newAtts;
    }

    @Autowired
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }
}
