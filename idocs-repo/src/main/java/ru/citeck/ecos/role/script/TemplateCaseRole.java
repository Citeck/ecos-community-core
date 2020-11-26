package ru.citeck.ecos.role.script;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.template.TemplateNode;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.TemplateImageResolver;
import org.alfresco.service.namespace.QName;
import org.springframework.extensions.surf.util.I18NUtil;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.model.ICaseRoleModel;
import ru.citeck.ecos.model.lib.role.dto.RoleDef;
import ru.citeck.ecos.role.CaseRoleService;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TemplateCaseRole extends TemplateNode {

    private final RoleDef roleDef;

    public TemplateCaseRole(NodeRef nodeRef, ServiceRegistry services, TemplateImageResolver resolver) {
        super(nodeRef, services, resolver);
        CaseRoleService caseRoleService = (CaseRoleService) services.getService(CaseRoleService.QNAME);
        this.roleDef = caseRoleService.getRoleDef(nodeRef);
    }

    @Override
    public boolean getExists() {
        return true;
    }

    @Override
    public QName getType() {
        return ICaseRoleModel.TYPE_ROLE;
    }

    @Override
    public Map<String, Serializable> getProperties() {

        Map<String, Serializable> props = new HashMap<>();
        String name = MLText.getClosestValue(roleDef.getName(), I18NUtil.getLocale());

        props.put(ICaseRoleModel.PROP_VARNAME.toPrefixString(services.getNamespaceService()), roleDef.getId());
        props.put(ContentModel.PROP_NAME.toPrefixString(services.getNamespaceService()), name);
        props.put(ContentModel.PROP_TITLE.toPrefixString(services.getNamespaceService()), name);

        return props;
    }

    @Override
    public Map<String, List<TemplateNode>> getAssocs() {
        return new HashMap<>();
    }
}
