package ru.citeck.ecos.behavior;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.model.EcosModel;
import ru.citeck.ecos.utils.NewUIUtils;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

public class InvalidateCacheBehaviour implements NodeServicePolicies.OnUpdatePropertiesPolicy {
    @Autowired
    private PolicyComponent policyComponent;

    @Autowired
    private NewUIUtils newUIUtils;

    @PostConstruct
    public void init() {
        policyComponent.bindClassBehaviour(
            NodeServicePolicies.OnUpdatePropertiesPolicy.QNAME,
            ContentModel.TYPE_PERSON,
            new JavaBehaviour(this,
                "onUpdateProperties",
                Behaviour.NotificationFrequency.TRANSACTION_COMMIT)
        );
    }

    @Override
    public void onUpdateProperties(NodeRef nodeRef, Map<QName, Serializable> before, Map<QName, Serializable> after) {
        Object propBefore = before.get(EcosModel.PROP_NEW_JOURNALS_ENABLED);
        Object propAfter = after.get(EcosModel.PROP_NEW_JOURNALS_ENABLED);
        if (!Objects.equals(propBefore, propAfter)) {
            newUIUtils.invalidateCacheForUser(AuthenticationUtil.getRunAsUser());
        }
    }
}
