package ru.citeck.ecos.security;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;

/**
 * @since 05.09.2020
 */
public interface AttributesPermissionServiceResolver {

    AttributesPermissionService resolve(NodeRef caseRef);
}
