package ru.citeck.ecos.security;

import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

public class ProtectedAttributesRegistrar {

    @Autowired
    private EcosPermissionService ecosPermissionService;
    private List<QName> protectedAttributes;

    @PostConstruct
    public void register() {
        if (protectedAttributes != null && protectedAttributes.size() > 0) {
            this.ecosPermissionService.addProtectedAttributes(protectedAttributes);
        }
    }

    public void setProtectedAttributes(List<QName> protectedAttributes) {
        this.protectedAttributes = new ArrayList<>(protectedAttributes);
    }
}
