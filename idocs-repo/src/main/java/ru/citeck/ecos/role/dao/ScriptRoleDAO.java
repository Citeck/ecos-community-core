package ru.citeck.ecos.role.dao;

import lombok.Getter;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.ScriptService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.ICaseRoleModel;
import ru.citeck.ecos.role.CaseRoleService;
import ru.citeck.ecos.role.script.ScriptCaseRole;
import ru.citeck.ecos.role.script.ScriptRoleDocument;
import ru.citeck.ecos.utils.JavaScriptImplUtils;

import java.util.*;

/**
 * @author Pavel Simonov
 */
@Component
public class ScriptRoleDAO implements RoleDAO {

    private static final Log logger = LogFactory.getLog(ScriptRoleDAO.class);

    private ScriptService scriptService;
    private AuthorityService authorityService;
    private NodeService nodeService;
    private ServiceRegistry serviceRegistry;

    @Getter(lazy = true)
    private final CaseRoleService caseRoleService = evalCaseRoleService();

    private CaseRoleService evalCaseRoleService() {
        return (CaseRoleService) Objects.requireNonNull(serviceRegistry).getService(CaseRoleService.QNAME);
    }

    @Override
    public QName getRoleType() {
        return ICaseRoleModel.TYPE_SCRIPT_ROLE;
    }

    @Override
    public Set<NodeRef> getAssignees(NodeRef caseRef, NodeRef roleRef) {

        Map<String, Object> model = new HashMap<>();
        model.put("document", new ScriptRoleDocument(caseRef, serviceRegistry));
        model.put("role", new ScriptCaseRole(roleRef, serviceRegistry, getCaseRoleService()));

        String script = (String) nodeService.getProperty(roleRef, ICaseRoleModel.PROP_SCRIPT);

        if (script == null || StringUtils.isBlank(script)) {
            return Collections.emptySet();
        }

        try {
            Object result = scriptService.executeScriptString(script, model);
            return JavaScriptImplUtils.getAuthoritiesSet(result, authorityService);
        } catch (Exception e) {
            logger.warn("Script role evaluation failed", e);
        }

        return null;
    }

    @Autowired
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
        this.authorityService = serviceRegistry.getAuthorityService();
        this.nodeService = serviceRegistry.getNodeService();
    }

    @Autowired
    public void setScriptService(ScriptService scriptService) {
        this.scriptService = scriptService;
    }
}
