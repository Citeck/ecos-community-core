package ru.citeck.ecos.icase.activity.service.alfresco;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.policy.ClassPolicyDelegate;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.action.ActionConditionUtils;
import ru.citeck.ecos.icase.activity.dto.ActivityRef;
import ru.citeck.ecos.icase.activity.dto.EventRef;
import ru.citeck.ecos.icase.activity.service.CaseActivityEventDelegate;
import ru.citeck.ecos.model.EventModel;
import ru.citeck.ecos.records.RecordsUtils;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.evaluator.RecordEvaluatorDto;
import ru.citeck.ecos.records2.evaluator.RecordEvaluatorService;
import ru.citeck.ecos.records2.evaluator.evaluators.GroupEvaluator;
import ru.citeck.ecos.service.CiteckServices;
import ru.citeck.ecos.utils.AlfActivityUtils;
import ru.citeck.ecos.utils.DictionaryUtils;
import ru.citeck.ecos.utils.EvaluatorUtils;
import ru.citeck.ecos.utils.RepoUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@DependsOn("idocs.dictionaryBootstrap")
public class AlfrescoCaseActivityEventDelegate implements CaseActivityEventDelegate {

    public static final String TRANSACTION_EVENT_VARIABLE = "event";

    private ClassPolicyDelegate<EventPolicies.OnEventPolicy> onEventDelegate;
    private ClassPolicyDelegate<EventPolicies.BeforeEventPolicy> beforeEventDelegate;

    private RecordEvaluatorService recordEvaluatorService;
    private CaseEvaluatorConverter caseEvaluatorConverter;
    private AlfActivityUtils alfActivityUtils;

    private PolicyComponent policyComponent;
    private NodeService nodeService;

    @Autowired
    public AlfrescoCaseActivityEventDelegate(ServiceRegistry serviceRegistry,
                                             CaseEvaluatorConverter caseEvaluatorConverter,
                                             RecordEvaluatorService recordEvaluatorService) {
        this.alfActivityUtils = (AlfActivityUtils) serviceRegistry.getService(CiteckServices.ALF_ACTIVITY_UTILS);
        this.policyComponent = serviceRegistry.getPolicyComponent();
        this.nodeService = serviceRegistry.getNodeService();
        this.caseEvaluatorConverter = caseEvaluatorConverter;
        this.recordEvaluatorService = recordEvaluatorService;
    }

    @PostConstruct
    public void init() {
        onEventDelegate = policyComponent.registerClassPolicy(EventPolicies.OnEventPolicy.class);
        beforeEventDelegate = policyComponent.registerClassPolicy(EventPolicies.BeforeEventPolicy.class);
    }

    @Override
    public void fireEvent(ActivityRef activityRef, String eventType, boolean procDefRequired) {
        NodeRef activityNodeRef = alfActivityUtils.getActivityNodeRef(activityRef);
        fireEventImpl(activityNodeRef, activityRef.getProcessId(), eventType);
    }

    private void fireEventImpl(NodeRef eventSourceRef, RecordRef caseRef, String eventType) {
        if (!nodeService.exists(eventSourceRef)) {
            return;
        }

        if (log.isDebugEnabled()) {
            String sourceTitle = (String) nodeService.getProperty(eventSourceRef, ContentModel.PROP_TITLE);
            if (sourceTitle == null) {
                sourceTitle = (String) nodeService.getProperty(eventSourceRef, ContentModel.PROP_NAME);
            }
            log.debug(String.format("Try to fire event. Event type: '%s', Event source: '%s'", eventType, sourceTitle));
        }

        EventPolicies.BeforeEventPolicy beforePolicy = getBeforePolicy(eventSourceRef);
        EventPolicies.OnEventPolicy onPolicy = getOnPolicy(eventSourceRef);
        List<NodeRef> events = getEvents(eventSourceRef, eventType);

        for (NodeRef event : events) {
            fireConcreteEventImpl(event, caseRef, beforePolicy, onPolicy);
        }
    }

    private List<NodeRef> getEvents(NodeRef nodeRef, String eventType) {
        List<NodeRef> events = new ArrayList<>();
        List<AssociationRef> eventsAssocs = nodeService.getSourceAssocs(nodeRef, EventModel.ASSOC_EVENT_SOURCE);
        for (AssociationRef assoc : eventsAssocs) {
            NodeRef eventRef = assoc.getSourceRef();
            if (eventType.equals(nodeService.getProperty(eventRef, EventModel.PROP_TYPE))) {
                events.add(eventRef);
            }
        }
        return events;
    }

    @Override
    public void fireConcreteEvent(EventRef eventRef) {
        NodeRef eventNodeRef = alfActivityUtils.getEventNodeRef(eventRef);
        NodeRef caseRef = RecordsUtils.toNodeRef(eventRef.getProcessId());
        EventPolicies.BeforeEventPolicy beforePolicy = getBeforePolicy(caseRef);
        EventPolicies.OnEventPolicy onPolicy = getOnPolicy(caseRef);
        fireConcreteEventImpl(eventNodeRef, eventRef.getProcessId(), beforePolicy, onPolicy);
    }

    private void fireConcreteEventImpl(NodeRef eventRef, RecordRef conditionContextRef,
                                       EventPolicies.BeforeEventPolicy beforePolicy,
                                       EventPolicies.OnEventPolicy onPolicy) {

        ActionConditionUtils.getTransactionVariables().put(TRANSACTION_EVENT_VARIABLE, eventRef);
        if (checkConditionsImpl(eventRef, conditionContextRef)) {
            if (log.isDebugEnabled()) {
                log.debug(String.format("Event '%s' was fired.", eventRef));
            }
            beforePolicy.beforeEvent(eventRef);
            onPolicy.onEvent(eventRef);
        }
    }

    @Override
    public boolean checkConditions(EventRef eventRef) {
        NodeRef eventNodeRef = alfActivityUtils.getEventNodeRef(eventRef);
        return checkConditionsImpl(eventNodeRef, eventRef.getProcessId());
    }

    private boolean checkConditionsImpl(NodeRef eventRef, RecordRef conditionContextRef) {
        ActionConditionUtils.getTransactionVariables().put(TRANSACTION_EVENT_VARIABLE, eventRef);
        RecordEvaluatorDto evaluatorDefinition = getEvaluatorDefinition(eventRef);
        if (evaluatorDefinition != null) {
            return recordEvaluatorService.evaluate(conditionContextRef, evaluatorDefinition);
        }
        return true;
    }

    private RecordEvaluatorDto getEvaluatorDefinition(NodeRef eventRef) {
        List<NodeRef> conditionRefs = RepoUtils.getChildrenByAssoc(eventRef, EventModel.ASSOC_CONDITIONS, nodeService);
        switch (conditionRefs.size()) {
            case 0:
                return null;
            case 1:
                return caseEvaluatorConverter.convertCondition(conditionRefs.get(0));
            default:
                GroupEvaluator.Config config = new GroupEvaluator.Config();
                config.setJoinBy(GroupEvaluator.JoinType.AND);
                List<RecordEvaluatorDto> groupedEvaluators = conditionRefs.stream()
                        .map(caseEvaluatorConverter::convertCondition)
                        .collect(Collectors.toList());
                config.setEvaluators(groupedEvaluators);
                return EvaluatorUtils.createEvaluatorDto("group", config, false);
        }
    }

    private EventPolicies.OnEventPolicy getOnPolicy(NodeRef nodeRef) {
        List<QName> classes = DictionaryUtils.getNodeClassNames(nodeRef, nodeService);
        return onEventDelegate.get(new HashSet<>(classes));
    }

    private EventPolicies.BeforeEventPolicy getBeforePolicy(NodeRef nodeRef) {
        List<QName> classes = DictionaryUtils.getNodeClassNames(nodeRef, nodeService);
        return beforeEventDelegate.get(new HashSet<>(classes));
    }
}
