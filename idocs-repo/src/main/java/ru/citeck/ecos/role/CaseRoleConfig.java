package ru.citeck.ecos.role;

import org.alfresco.model.ContentModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.DefaultImageResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.extensions.surf.util.AbstractLifecycleBean;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.attr.prov.ChildAssocAttributes;
import ru.citeck.ecos.attr.prov.PropertyAttributes;
import ru.citeck.ecos.attr.prov.TargetAssocAttributes;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.model.ICaseRoleModel;
import ru.citeck.ecos.model.lib.role.dto.RoleDef;
import ru.citeck.ecos.role.script.TemplateCaseRole;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CaseRoleConfig {

    private final ServiceRegistry serviceRegistry;
    private final PropertyAttributes propsAtts;
    private final ChildAssocAttributes childAssocAttributes;

    private final CaseRoleService caseRoleService;

    @Autowired
    public CaseRoleConfig(ServiceRegistry serviceRegistry,
                          PropertyAttributes propsAtts,
                          CaseRoleService caseRoleService,
                          ChildAssocAttributes childAssocAttributes) {
        this.propsAtts = propsAtts;
        this.serviceRegistry = serviceRegistry;
        this.caseRoleService = caseRoleService;
        this.childAssocAttributes = childAssocAttributes;
    }

    @PostConstruct
    public void init() {
        propsAtts.registerAttsResolver(CaseRoleService.ROLE_REF_PROTOCOL, this::getProperty);
        childAssocAttributes.registerAssocResolver(ICaseRoleModel.ASSOC_ROLES, this::getRoles);
    }

    private List<Object> getRoles(NodeRef nodeRef) {

        List<NodeRef> roles = caseRoleService.getRoles(nodeRef);

        return roles.stream()
            .map(roleRef -> new TemplateCaseRole(roleRef, serviceRegistry, new DefaultImageResolver()))
            .collect(Collectors.toList());
    }

    private Serializable getProperty(NodeRef nodeRef, QName name) {
        RoleDef roleDef = caseRoleService.getRoleDef(nodeRef);
        if (ContentModel.PROP_NAME.equals(name)
                || ContentModel.PROP_TITLE.equals(name)) {
            return MLText.getClosestValue(roleDef.getName(), I18NUtil.getLocale());
        }
        if (ICaseRoleModel.PROP_VARNAME.equals(name)) {
            return roleDef.getId();
        }
        return null;
    }

    @Component
    public static class RoleAssocRegistrar extends AbstractLifecycleBean {

        @Autowired
        private TargetAssocAttributes attributes;
        @Autowired
        private DictionaryService dictionaryService;
        @Autowired
        private CaseRoleAssocsDao caseRoleAssocsDao;
        @Autowired
        private ServiceRegistry serviceRegistry;

        @Override
        protected void onBootstrap(ApplicationEvent event) {
            dictionaryService.getAllAssociations()
                .stream()
                .map(assoc -> dictionaryService.getAssociation(assoc))
                .filter(assoc -> assoc.getTargetClass().getName().equals(ICaseRoleModel.TYPE_ROLE))
                .filter(assoc -> !assoc.isChild())
                .forEach(this::registerAssoc);
        }

        private void registerAssoc(AssociationDefinition assocDef) {
            attributes.addAssocsReader(assocDef.getName(), nodeRef ->
                caseRoleAssocsDao.getRolesByAssoc(nodeRef, assocDef.getName()).stream()
                    .map(ref -> new TemplateCaseRole(ref, serviceRegistry, new DefaultImageResolver()))
                    .collect(Collectors.toList())
            );
            attributes.addAssocsWriter(assocDef.getName(), (nodeInfo, value) ->
                caseRoleAssocsDao.createRoleAssocs(nodeInfo, assocDef.getName(), value)
            );
        }

        @Override
        protected void onShutdown(ApplicationEvent applicationEvent) {
        }
    }
}
