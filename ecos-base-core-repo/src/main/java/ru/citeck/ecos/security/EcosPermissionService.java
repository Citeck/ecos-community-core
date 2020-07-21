package ru.citeck.ecos.security;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EcosPermissionService {

    public static final QName QNAME = QName.createQName("", "ecosPermissionService");

    private static final String EDIT_MODE = "edit";

    private AttributesPermissionService attsPermService;
    private NamespaceService namespaceService;

    public boolean isAttributeProtected(NodeRef nodeRef, String attributeName) {

        if (attsPermService == null || nodeRef == null || StringUtils.isBlank(attributeName)) {
            return false;
        }

        QName attQName = QName.resolveToQName(namespaceService, attributeName);
        if (attQName == null) {
            return false;
        }
        return !attsPermService.isFieldEditable(attQName, nodeRef, EDIT_MODE);
    }

    @Autowired(required = false)
    public void setAttsPermService(AttributesPermissionService attsPermService) {
        this.attsPermService = attsPermService;
    }

    @Autowired
    public void setNamespaceService(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }
}
