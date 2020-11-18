package ru.citeck.ecos.role.script;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.jscript.Association;
import org.alfresco.repo.jscript.ContentAwareScriptableQNameMap;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.jscript.ScriptableQNameMap;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.springframework.extensions.surf.util.I18NUtil;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.model.ICaseRoleModel;
import ru.citeck.ecos.model.lib.role.dto.RoleDef;
import ru.citeck.ecos.role.CaseRoleService;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ScriptCaseRole extends ScriptNode {

    private final CaseRoleService caseRoleService;

    private final NodeRef roleRef;

    private Map<String, Object> properties = null;
    private Map<String, Object> assocs = null;

    public ScriptCaseRole(NodeRef roleRef,
                          ServiceRegistry services,
                          CaseRoleService caseRoleService) {

        super(roleRef, services);
        this.roleRef = roleRef;
        this.caseRoleService = caseRoleService;
    }

    public ScriptCaseRole(NodeRef roleRef,
                          ServiceRegistry services,
                          CaseRoleService caseRoleService,
                          Scriptable scope) {

        super(roleRef, services, scope);
        this.roleRef = roleRef;
        this.caseRoleService = caseRoleService;
    }

    @Override
    public Association createAssociation(ScriptNode target, String assocType) {
        if (caseRoleService.isAlfRole(roleRef)) {
            return super.createAssociation(target, assocType);
        }
        log.error("ScriptCaseRole '" + roleRef + "' doesn't support 'createAssociation'. " +
            "Target: " + target.getNodeRef() + " assocType: " + assocType);
        return null;
    }

    @Override
    public void removeAssociation(ScriptNode target, String assocType) {
        if (caseRoleService.isAlfRole(roleRef)) {
            super.removeAssociation(target, assocType);
        }
        log.error("ScriptCaseRole '" + roleRef + "' doesn't support 'removeAssociation'. " +
            "Target: " + target.getNodeRef() + " assocType: " + assocType);
    }

    @Override
    public Map<String, Object> getProperties() {

        if (caseRoleService.isAlfRole(roleRef)) {
            return super.getProperties();
        }

        if (this.properties == null)
        {
            // this Map implements the Scriptable interface for native JS syntax property access
            // this impl of the QNameMap is capable of creating ScriptContentData on demand for 'cm:content'
            // properties that have not been initialised - see AR-1673.
            @SuppressWarnings("unchecked")
            Map<String, Object> props = new ContentAwareScriptableQNameMap<String, Object>(this, this.services);
            this.properties = props;

            RoleDef roleDef = caseRoleService.getRoleDef(roleRef);
            props.put(ICaseRoleModel.PROP_VARNAME.toString(), roleDef.getId());
            props.put(ICaseRoleModel.PROP_IS_REFERENCE_ROLE.toString(), true);
            props.put(ContentModel.PROP_NAME.toString(),
                MLText.getClosestValue(roleDef.getName(), I18NUtil.getLocale()));
            props.put(ContentModel.PROP_TITLE.toString(),
                MLText.getClosestValue(roleDef.getName(), I18NUtil.getLocale()));
        }

        return this.properties;
    }

    @Override
    public Map<String, Object> getAssocs() {

        if (caseRoleService.isAlfRole(roleRef)) {
            return super.getAssocs();
        }

        if (this.assocs == null) {

            @SuppressWarnings("unchecked")
            Map<String, Object> assocsMap = new ScriptableQNameMap<String, Object>(this);
            this.assocs = assocsMap;

            Set<NodeRef> assigness = caseRoleService.getAssignees(roleRef);
            assocs.put(ICaseRoleModel.ASSOC_ASSIGNEES.toString(), assigness.stream()
                .map(a -> newInstance(a, services, scope)).collect(Collectors.toList()));
            assocs.put(ICaseRoleModel.ASSOC_REFERENCE_ROLE.toString(), Collections.singletonList(this));

            // convert each Node list into a JavaScript array object
            for (String qname : this.assocs.keySet()) {
                @SuppressWarnings("unchecked")
                List<ScriptNode> nodes = (List<ScriptNode>) this.assocs.get(qname);
                Object[] objs = nodes.toArray(new Object[0]);
                this.assocs.put(qname, Context.getCurrentContext().newArray(this.scope, objs));
            }
        }

        return this.assocs;
    }

    @Override
    public Map<String, Object> getChildAssocs() {
        if (caseRoleService.isAlfRole(roleRef)) {
            return super.getChildAssocs();
        }
        return getAssocs();
    }

    @Override
    public QName getQNameType() {
        if (caseRoleService.isAlfRole(roleRef)) {
            return super.getQNameType();
        }
        return ICaseRoleModel.TYPE_ROLE;
    }
}
