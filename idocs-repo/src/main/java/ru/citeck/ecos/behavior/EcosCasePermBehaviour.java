package ru.citeck.ecos.behavior;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import ru.citeck.ecos.behavior.base.AbstractBehaviour;
import ru.citeck.ecos.behavior.base.PolicyMethod;
import ru.citeck.ecos.config.EcosConfigService;
import ru.citeck.ecos.model.EcosModel;
import ru.citeck.ecos.model.IdocsModel;
import ru.citeck.ecos.commons.utils.StringUtils;
import ru.citeck.ecos.utils.NodeUtils;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class EcosCasePermBehaviour extends AbstractBehaviour implements NodeServicePolicies.OnCreateNodePolicy {

    private static final String ENABLED_CONFIG_KEY = "ecosCaseFolderPermissionConfig";
    private static final String NODE_MANAGER_PERM = "NodeManager";

    private EcosConfigService ecosConfigService;
    private PermissionService permissionService;
    private NodeUtils nodeUtils;

    @Override
    protected void beforeInit() {
        setClassName(EcosModel.TYPE_CASE);
        permissionService = serviceRegistry.getPermissionService();
    }

    @PolicyMethod(policy = NodeServicePolicies.OnCreateNodePolicy.class,
                  frequency = Behaviour.NotificationFrequency.TRANSACTION_COMMIT)
    public void onCreateNode(ChildAssociationRef childAssociationRef) {
        AuthenticationUtil.runAsSystem(() -> {
            onCreateNodeAsAdmin(childAssociationRef);
            return null;
        });
    }

    private void onCreateNodeAsAdmin(ChildAssociationRef childAssociationRef) {

        Object paramValue = ecosConfigService.getParamValue(ENABLED_CONFIG_KEY);
        if (!"true".equals(paramValue)) {
            return;
        }

        NodeRef caseRef = childAssociationRef.getChildRef();
        Map<QName, Serializable> caseProps = nodeService.getProperties(caseRef);
        String creator = (String) caseProps.get(ContentModel.PROP_CREATOR);

        if (StringUtils.isBlank(creator)) {
            return;
        }

        NodeRef parentRef = nodeService.getPrimaryParent(caseRef).getParentRef();
        Map<QName, Serializable> parentProps = nodeService.getProperties(parentRef);

        String parentName = (String) parentProps.get(ContentModel.PROP_NAME);

        if (creator.equals(parentName)) {
            return;
        }

        NodeRef creatorFolder = nodeService.getChildByName(parentRef, ContentModel.ASSOC_CONTAINS, creator);

        if (creatorFolder == null) {

            Map<QName, Serializable> properties = new HashMap<>();
            properties.put(ContentModel.PROP_NAME, creator);

            creatorFolder = nodeUtils.createNode(parentRef,
                                                 ContentModel.TYPE_FOLDER,
                                                 ContentModel.ASSOC_CONTAINS,
                                                 properties);

            permissionService.setInheritParentPermissions(creatorFolder, false);
            permissionService.setPermission(creatorFolder, creator, NODE_MANAGER_PERM, true);

            String managerGroup = (String) parentProps.get(IdocsModel.PROP_MANAGER_GROUP);
            String managerPermission = (String) parentProps.get(IdocsModel.PROP_MANAGER_PERMISSION);

            if (StringUtils.isNotBlank(managerGroup) && StringUtils.isNotBlank(managerPermission)) {
                permissionService.setPermission(creatorFolder, managerGroup, managerPermission, true);
            }
        }

        String caseName = (String) caseProps.get(ContentModel.PROP_NAME);
        String newCaseName;
        if (StringUtils.isNotBlank(caseName)) {
            newCaseName = nodeUtils.getValidChildName(creatorFolder, caseName);
        } else {
            newCaseName = UUID.randomUUID().toString();
        }
        if (!newCaseName.equals(caseName)) {
            nodeService.setProperty(caseRef, ContentModel.PROP_NAME, newCaseName);
        }
        QName assocQName = QName.createQName(EcosModel.ECOS_NAMESPACE, newCaseName);
        nodeService.moveNode(caseRef, creatorFolder, ContentModel.ASSOC_CONTAINS, assocQName);
    }

    @Autowired
    public void setNodeUtils(NodeUtils nodeUtils) {
        this.nodeUtils = nodeUtils;
    }

    @Autowired
    @Qualifier("ecosConfigService")
    public void setEcosConfigService(EcosConfigService ecosConfigService) {
        this.ecosConfigService = ecosConfigService;
    }
}
