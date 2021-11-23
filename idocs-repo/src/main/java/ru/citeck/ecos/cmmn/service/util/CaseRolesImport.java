package ru.citeck.ecos.cmmn.service.util;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.namespace.InvalidQNameException;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import ru.citeck.ecos.cmmn.CMMNUtils;
import ru.citeck.ecos.cmmn.CmmnExportImportException;
import ru.citeck.ecos.cmmn.model.CaseRoles;
import ru.citeck.ecos.cmmn.model.Role;
import ru.citeck.ecos.model.ICaseRoleModel;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Maxim Strizhov (maxim.strizhov@citeck.ru)
 */
public class CaseRolesImport {
    private static final Logger logger = Logger.getLogger(CaseRolesImport.class);
    private NodeService nodeService;
    private AuthorityService authorityService;

    public static final QName ASSOC_ROLES = QName.createQName("http://www.citeck.ru/model/icaseRole/1.0", "roles");

    private CMMNUtils utils;

    public CaseRolesImport(NodeService nodeService, AuthorityService authorityService, CMMNUtils utils) {
        this.nodeService = nodeService;
        this.authorityService = authorityService;
        this.utils = utils;
    }

    public Map<String, NodeRef> importRoles(NodeRef templateRef, CaseRoles caseRoles) throws CmmnExportImportException {
        if (logger.isDebugEnabled()) {
            logger.debug("Importing roles for case. Found " + caseRoles.getRole().size() + " roles.");
        }
        if (nodeService.exists(templateRef) && !caseRoles.getRole().isEmpty()) {
            return addCaseRoles(templateRef, caseRoles);
        } else {
            return Collections.emptyMap();
        }
    }

    private Map<String, NodeRef> addCaseRoles(NodeRef caseRolesConfigRef, CaseRoles caseRoles) throws CmmnExportImportException {
        if (caseRolesConfigRef != null) {
            Map<String, NodeRef> roles = new HashMap<>();
            for (Role role : caseRoles.getRole()) {
                Map<QName, Serializable> nodeProps = new HashMap<>(2);
                nodeProps.put(ContentModel.PROP_NAME, role.getName());
                nodeProps.put(ContentModel.PROP_TITLE, role.getName());

                String roleTypeStr = role.getOtherAttributes().get(CMMNUtils.QNAME_NODE_TYPE);
                QName roleType;
                try {
                    roleType = roleTypeStr != null ? QName.createQName(roleTypeStr) : ICaseRoleModel.TYPE_ROLE;
                } catch (InvalidQNameException e) {
                    logger.warn("Role type QName " + roleTypeStr + " is invalid. message = " + e.getMessage());
                    roleType = ICaseRoleModel.TYPE_ROLE;
                }

                for (Map.Entry<javax.xml.namespace.QName, QName> entry : CMMNUtils.ROLES_ATTRIBUTES_MAPPING.entrySet()) {
                    String value = role.getOtherAttributes().get(entry.getKey());
                    if (value != null) {
                        nodeProps.put(entry.getValue(), utils.convertValueForRepo(entry.getValue(), value));
                    }
                }

                NodeRef roleRef = nodeService.createNode(caseRolesConfigRef,
                        ASSOC_ROLES,
                        QName.createQName(ICaseRoleModel.NAMESPACE, role.getName()),
                        roleType, nodeProps).getChildRef();
                roles.put(role.getId(), roleRef);

                String assigneesStr = role.getOtherAttributes().get(CMMNUtils.QNAME_ROLE_ASSIGNEES);
                if (StringUtils.isNotBlank(assigneesStr) && !roleType.equals(ICaseRoleModel.TYPE_SCRIPT_ROLE)) {
                    String[] assignees = assigneesStr.split(",");
                    for (String assignee : assignees) {
                        NodeRef authorityRef = authorityService.getAuthorityNodeRef(assignee);
                        if (authorityRef != null) {
                            nodeService.createAssociation(roleRef, authorityRef, ICaseRoleModel.ASSOC_ASSIGNEES);
                        }
                    }
                }

                String roleAssocString = role.getOtherAttributes().get(CMMNUtils.QNAME_REFERENSE_ROLE);
                if (roleAssocString != null && !roleAssocString.trim().isEmpty()) {
                    NodeRef originalRoleRef = utils.getCaseRoleById(roleAssocString);
                    if (originalRoleRef != null) {
                        nodeService.createAssociation(roleRef, originalRoleRef, ICaseRoleModel.ASSOC_REFERENCE_ROLE);
                    } else {
                        logger.warn("Can't find role with varName = '" + roleAssocString + "'. Please, create this role and restart template import.");
                    }
                }

                if (logger.isDebugEnabled()) {
                    logger.debug("Created role with name '" + role.getName() + "'");
                }
            }
            return roles;
        } else {
            return Collections.emptyMap();
        }
    }
}
