package ru.citeck.ecos.security;

import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.node.AlfNodeInfo;
import ru.citeck.ecos.node.AlfNodeInfoImpl;
import ru.citeck.ecos.records3.record.request.context.SystemContextUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class EcosPermissionService {

    public static final QName QNAME = QName.createQName("", "ecosPermissionService");

    private static final String IS_SYSTEM_CONTEXT_VAR = "ecos-permissions-is-system-context";

    private EcosPermissionComponent ecosPermissionComponent;

    private NamespaceService namespaceService;
    private ServiceRegistry serviceRegistry;

    private Set<QName> protectedAttributes = new HashSet<>();

    public void updateNodePermissions(NodeRef nodeRef) {
        if (nodeRef != null && ecosPermissionComponent != null) {
            AuthenticationUtil.runAsSystem(() -> {
                ecosPermissionComponent.updateNodePermissions(new AlfNodeInfoImpl(nodeRef, serviceRegistry));
                return null;
            });
        }
    }

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
            if (SystemContextUtil.isSystemContext()) {
                return false;
            }
            if (AuthenticationUtil.isRunAsUserTheSystemUser()) {
                return false;
            }

            return ecosPermissionComponent.isAttProtected(node, attributeName);
        }

        return false;
    }

    public boolean isAttributeVisible(NodeRef nodeRef, String attributeName) {

        if (nodeRef == null || StringUtils.isBlank(attributeName)) {
            return false;
        }

        return isAttVisible(new AlfNodeInfoImpl(nodeRef, serviceRegistry), attributeName);
    }

    public boolean isAttVisible(AlfNodeInfo info, String attributeName) {

        if (info == null || StringUtils.isBlank(attributeName) || ecosPermissionComponent == null) {
            return true;
        }

        if (AuthenticationUtil.isRunAsUserTheSystemUser()) {
            return true;
        }

        return SystemContextUtil.doAsSystemIfNotSystemContextJ(
            () -> ecosPermissionComponent.isAttVisible(info, attributeName),
            true
        );
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
