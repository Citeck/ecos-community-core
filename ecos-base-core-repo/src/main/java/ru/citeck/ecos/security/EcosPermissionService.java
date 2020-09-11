package ru.citeck.ecos.security;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class EcosPermissionService {

    public static final QName QNAME = QName.createQName("", "ecosPermissionService");

    private static final String EDIT_MODE = "edit";

    private AttributesPermissionServiceResolver attsPermServiceResolver;
    private NamespaceService namespaceService;

    private Set<QName> protectedAttributes = new HashSet<>();

    public boolean isAttributeProtected(NodeRef nodeRef, String attributeName) {

        if (nodeRef == null || StringUtils.isBlank(attributeName)) {
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
        if (attsPermServiceResolver == null) {
            return false;
        }
        AttributesPermissionService attrsPermissionService = attsPermServiceResolver.resolve(nodeRef);
        return attrsPermissionService == null ? false : !attrsPermissionService.isFieldEditable(attQName, nodeRef, EDIT_MODE);
    }

    @Autowired(required = false)
    public void setAttsPermServiceResolver(AttributesPermissionServiceResolver attsPermServiceResolver) {
        this.attsPermServiceResolver = attsPermServiceResolver;
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
}
