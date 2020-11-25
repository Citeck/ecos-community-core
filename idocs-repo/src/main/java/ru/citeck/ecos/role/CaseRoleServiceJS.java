package ru.citeck.ecos.role;

import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.jscript.ValueConverter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.namespace.QName;
import org.springframework.extensions.surf.util.ParameterCheck;
import ru.citeck.ecos.model.lib.role.dto.RoleDef;
import ru.citeck.ecos.role.script.ScriptCaseRole;
import ru.citeck.ecos.utils.AlfrescoScopableProcessorExtension;
import ru.citeck.ecos.utils.JavaScriptImplUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Maxim Strizhov
 * @author Pavel Simonov
 */
public class CaseRoleServiceJS extends AlfrescoScopableProcessorExtension {

    public static final QName QNAME = QName.createQName("", "caseRoleServiceJS");

    private final ValueConverter converter = new ValueConverter();

    private CaseRoleService caseRoleService;
    private AuthorityService authorityService;

    private ScriptCaseRole toScriptCaseRole(NodeRef roleRef) {
        return new ScriptCaseRole(roleRef, serviceRegistry, caseRoleService, getScope());
    };

    private ScriptCaseRole[] toScriptCaseRole(Collection<NodeRef> rolesRef) {
        return rolesRef.stream()
            .map(this::toScriptCaseRole)
            .toArray(ScriptCaseRole[]::new);
    }

    public String getRoleId(Object role) {
        NodeRef roleRef = JavaScriptImplUtils.getNodeRef(role);
        return caseRoleService.getRoleId(roleRef);
    }

    public ScriptCaseRole getRoleByRef(Object role) {
        return toScriptCaseRole(JavaScriptImplUtils.getNodeRef(role));
    }

    public ScriptCaseRole getRole(Object document, String name) {
        NodeRef docRef = JavaScriptImplUtils.getNodeRef(document);
        return toScriptCaseRole(caseRoleService.getRole(docRef, name));
    }

    public RoleDef getRoleDef(Object role) {
        NodeRef roleRef = JavaScriptImplUtils.getNodeRef(role);
        return caseRoleService.getRoleDef(roleRef);
    }

    public RoleDef[] getRolesDef(Object document) {
        NodeRef docRef = JavaScriptImplUtils.getNodeRef(document);
        return caseRoleService.getRolesDef(docRef).toArray(new RoleDef[0]);
    }

    public ScriptCaseRole[] getRoles(Object document) {
        NodeRef docRef = JavaScriptImplUtils.getNodeRef(document);
        List<NodeRef> roles = caseRoleService.getRoles(docRef);
        return toScriptCaseRole(roles);
    }

    public ScriptCaseRole[] getAlfRoles(Object document) {
        NodeRef docRef = JavaScriptImplUtils.getNodeRef(document);
        List<NodeRef> roles = caseRoleService.getRoles(docRef);
        roles = roles.stream().filter(caseRoleService::isAlfRole).collect(Collectors.toList());
        return toScriptCaseRole(roles);
    }

    public void setAssignees(Object document, Object role, Object assignees) {
        setAssignees(getRoleRef(document, role), assignees);
    }

    public void setAssignees(Object role, Object assignees) {
        ParameterCheck.mandatory("role", role);
        ParameterCheck.mandatory("assignees", assignees);

        NodeRef roleRef = JavaScriptImplUtils.getNodeRef(role);
        Set<NodeRef> assigneesSet = JavaScriptImplUtils.getAuthoritiesSet(assignees, authorityService);
        caseRoleService.setAssignees(roleRef, assigneesSet);
    }

    public void addAssignees(Object document, Object role, Object assignees) {
        addAssignees(getRoleRef(document, role), assignees);
    }

    public void addAssignees(Object role, Object assignees) {
        NodeRef roleRef = JavaScriptImplUtils.getNodeRef(role);
        Set<NodeRef> assigneesSet = JavaScriptImplUtils.getAuthoritiesSet(assignees, authorityService);
        caseRoleService.addAssignees(roleRef, assigneesSet);
    }

    public ScriptNode[] getAssignees(Object document, Object role) {
        NodeRef roleRef = getRoleRef(document, role);
        Set<NodeRef> assignees = caseRoleService.getAssignees(roleRef);
        return JavaScriptImplUtils.wrapNodes(assignees, this);
    }

    public ScriptNode[] getAssignees(Object role) {
        NodeRef roleRef = JavaScriptImplUtils.getNodeRef(role);
        Set<NodeRef> assignees = caseRoleService.getAssignees(roleRef);
        return JavaScriptImplUtils.wrapNodes(assignees, this);
    }

    public void updateRoles(Object document) {
        NodeRef docRef = JavaScriptImplUtils.getNodeRef(document);
        caseRoleService.updateRoles(docRef);
    }

    public void updateRole(Object role) {
        NodeRef roleRef = JavaScriptImplUtils.getNodeRef(role);
        caseRoleService.updateRole(roleRef);
    }

    public void updateRole(Object document, Object role) {
        NodeRef roleRef = getRoleRef(document, role);
        caseRoleService.updateRole(roleRef);
    }

    public void removeAssignees(Object document, Object role) {
        caseRoleService.removeAssignees(getRoleRef(document, role));
    }

    public void removeAssignees(Object role) {
        caseRoleService.removeAssignees(JavaScriptImplUtils.getNodeRef(role));
    }

    public boolean isRoleMember(Object role, Object authority) {
        NodeRef roleRef = JavaScriptImplUtils.getNodeRef(role);
        NodeRef authorityRef = JavaScriptImplUtils.getAuthorityRef(authority, authorityService);
        return caseRoleService.isRoleMember(roleRef, authorityRef);
    }

    public boolean isRoleMember(Object document, Object role, Object authority) {
        NodeRef roleRef = getRoleRef(document, role);
        NodeRef authorityRef = JavaScriptImplUtils.getAuthorityRef(authority, authorityService);
        return caseRoleService.isRoleMember(roleRef, authorityRef);
    }

    public boolean isRoleMember(Object role, Object authority, boolean immediate) {
        NodeRef roleRef = JavaScriptImplUtils.getNodeRef(role);
        NodeRef authorityRef = JavaScriptImplUtils.getAuthorityRef(authority, authorityService);
        return caseRoleService.isRoleMember(roleRef, authorityRef, immediate);
    }

    public boolean isRoleMember(Object document, Object role, Object authority, boolean immediate) {
        NodeRef roleRef = getRoleRef(document, role);
        NodeRef authorityRef = JavaScriptImplUtils.getAuthorityRef(authority, authorityService);
        return caseRoleService.isRoleMember(roleRef, authorityRef, immediate);
    }

    public void setDelegate(Object role, Object assignee, Object delegate) {
        NodeRef roleRef = JavaScriptImplUtils.getNodeRef(role);
        NodeRef assigneeRef = JavaScriptImplUtils.getAuthorityRef(assignee, authorityService);
        NodeRef delegateRef = JavaScriptImplUtils.getAuthorityRef(delegate, authorityService);
        caseRoleService.setDelegate(roleRef, assigneeRef, delegateRef);
    }

    public void setDelegates(Object role, Object delegatesJSObj) {
        NodeRef roleRef = JavaScriptImplUtils.getNodeRef(role);
        Object delegatesObj = converter.convertValueForJava(delegatesJSObj);
        if (delegatesObj instanceof Map) {
            Map<NodeRef, NodeRef> delegates = new HashMap<>();
            @SuppressWarnings("unchecked")
            Map<Object, Object> delegatesObjMap = (Map<Object, Object>) delegatesObj;
            for (Map.Entry entry : delegatesObjMap.entrySet()) {
                NodeRef assignee = JavaScriptImplUtils.getAuthorityRef(entry.getKey(), authorityService);
                NodeRef delegate = JavaScriptImplUtils.getAuthorityRef(entry.getValue(), authorityService);
                delegates.put(assignee, delegate);
            }
            caseRoleService.setDelegates(roleRef, delegates);
        } else {
            throw new IllegalArgumentException("Illegal argument 'delegates'. Expected Map but found: " + delegatesJSObj.getClass());
        }
    }

    public void removeDelegate(Object role, Object assignee) {
        NodeRef roleRef = JavaScriptImplUtils.getNodeRef(role);
        NodeRef assigneeRef = JavaScriptImplUtils.getAuthorityRef(assignee, authorityService);
        caseRoleService.removeDelegate(roleRef, assigneeRef);
    }

    public void removeDelegates(Object role) {
        NodeRef roleRef = JavaScriptImplUtils.getNodeRef(role);
        caseRoleService.removeDelegates(roleRef);
    }

    public ScriptNode getDelegate(Object role, Object assignee) {
        NodeRef roleRef = JavaScriptImplUtils.getNodeRef(role);
        NodeRef assigneeRef = JavaScriptImplUtils.getAuthorityRef(assignee, authorityService);
        Map<NodeRef, NodeRef> delegates = caseRoleService.getDelegates(roleRef);
        NodeRef delegateRef = delegates.get(assigneeRef);
        return delegateRef != null ? new ScriptNode(delegateRef, serviceRegistry, getScope()) : null;
    }

    private NodeRef getRoleRef(Object document, Object role) {
        if (role instanceof String) {
            String roleStr = (String) role;
            if (NodeRef.isNodeRef(roleStr)) {
                return new NodeRef(roleStr);
            }
            NodeRef docRef = JavaScriptImplUtils.getNodeRef(document);
            return caseRoleService.getRole(docRef, roleStr);
        }
        return JavaScriptImplUtils.getNodeRef(role);
    }

    public void setCaseRoleService(CaseRoleService caseRoleService) {
        this.caseRoleService = caseRoleService;
    }

    public void setAuthorityService(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }
}
