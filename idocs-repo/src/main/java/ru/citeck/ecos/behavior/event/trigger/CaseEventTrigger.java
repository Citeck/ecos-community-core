package ru.citeck.ecos.behavior.event.trigger;

import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.node.NodeUtils;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.behavior.OrderedBehaviour;
import ru.citeck.ecos.icase.activity.dto.ActivityRef;
import ru.citeck.ecos.icase.activity.service.ActivityCommonService;
import ru.citeck.ecos.icase.activity.service.CaseActivityEventService;
import ru.citeck.ecos.model.ICaseEventModel;
import ru.citeck.ecos.model.ICaseModel;
import ru.citeck.ecos.service.CiteckServices;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.util.Map;

/**
 * @author Pavel Simonov
 */
@Component
@DependsOn("idocs.dictionaryBootstrap")
public class CaseEventTrigger implements NodeServicePolicies.OnUpdatePropertiesPolicy {

    private NodeService nodeService;
    private PolicyComponent policyComponent;
    private ActivityCommonService activityCommonService;
    private CaseActivityEventService caseActivityEventService;

    private int order = 200;

    @Autowired
    public CaseEventTrigger(ServiceRegistry serviceRegistry) {
        this.nodeService = serviceRegistry.getNodeService();
        this.policyComponent = serviceRegistry.getPolicyComponent();
        this.activityCommonService = (ActivityCommonService) serviceRegistry
                .getService(CiteckServices.ACTIVITY_COMMON_SERVICE);
        this.caseActivityEventService = (CaseActivityEventService) serviceRegistry
                .getService(CiteckServices.CASE_ACTIVITY_EVENT_SERVICE);
    }

    @PostConstruct
    public void init() {
        OrderedBehaviour behaviour;
        QName type = ICaseModel.ASPECT_CASE;
        NotificationFrequency frequency = Behaviour.NotificationFrequency.TRANSACTION_COMMIT;

        behaviour = new OrderedBehaviour(this, "onUpdateProperties", frequency, order);
        policyComponent.bindClassBehaviour(NodeServicePolicies.OnUpdatePropertiesPolicy.QNAME, type, behaviour);
    }

    @Override
    public void onUpdateProperties(NodeRef caseRef, Map<QName, Serializable> before, Map<QName, Serializable> after) {
        if (NodeUtils.exists(caseRef, nodeService)) {
            if (nodeService.hasAspect(caseRef, ICaseModel.ASPECT_LEGACY_EDITOR_TEMPLATE)) {
                return;
            }
            ActivityRef rootActivityRef = activityCommonService.composeRootActivityRef(caseRef);
            caseActivityEventService.fireEvent(rootActivityRef, ICaseEventModel.CONSTR_CASE_PROPERTIES_CHANGED);
        }
    }
}
