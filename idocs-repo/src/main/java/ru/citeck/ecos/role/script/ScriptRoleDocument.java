package ru.citeck.ecos.role.script;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.mozilla.javascript.Context;
import ru.citeck.ecos.model.ICaseRoleModel;
import ru.citeck.ecos.role.CaseRoleServiceJS;

import java.util.Arrays;
import java.util.Map;

@Slf4j
public class ScriptRoleDocument extends ScriptNode {

    private final CaseRoleServiceJS caseRoleServiceJS;

    public ScriptRoleDocument(NodeRef nodeRef, ServiceRegistry services) {
        super(nodeRef, services);
        this.caseRoleServiceJS = (CaseRoleServiceJS) services.getService(CaseRoleServiceJS.QNAME);
    }

    @Override
    public Map<String, Object> getChildAssocs() {

        Map<String, Object> childAssocs = super.getChildAssocs();

        if (childAssocs.get(ICaseRoleModel.ASSOC_ROLES.toString()) != null) {

            ScriptCaseRole[] roles = caseRoleServiceJS.getRoles(nodeRef);
            Object[] objs = Arrays.stream(roles).toArray();
            childAssocs.put(ICaseRoleModel.ASSOC_ROLES.toString(),
                Context.getCurrentContext().newArray(this.scope, objs));
        }

        return childAssocs;
    }
}

