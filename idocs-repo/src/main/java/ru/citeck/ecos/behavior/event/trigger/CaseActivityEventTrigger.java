package ru.citeck.ecos.behavior.event.trigger;

import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.lang.mutable.MutableInt;
import ru.citeck.ecos.behavior.ChainingJavaBehaviour;
import ru.citeck.ecos.event.EventService;
import ru.citeck.ecos.icase.activity.dto.CaseActivity;
import ru.citeck.ecos.icase.activity.CaseActivityPolicies;
import ru.citeck.ecos.icase.activity.service.CaseActivityService;
import ru.citeck.ecos.model.ActivityModel;
import ru.citeck.ecos.model.ICaseEventModel;
import ru.citeck.ecos.model.StagesModel;

import java.util.*;

public class CaseActivityEventTrigger implements CaseActivityPolicies.OnCaseActivityStartedPolicy,
                                                 CaseActivityPolicies.OnCaseActivityStoppedPolicy {

    private static final String ACTIVITY_EVENT_TRIGGER_DATA_KEY = "case-activity-event-trigger-data";
    private static final String STAGE_CHILDREN_COMPLETED_TXN_KEY = "case-activity-stage-children-completed";

    private static final int STAGE_COMPLETE_LIMIT = 40;

    private CaseActivityService caseActivityService;
    private DictionaryService dictionaryService;
    private PolicyComponent policyComponent;
    private EventService eventService;
    private NodeService nodeService;

    public void init() {
        policyComponent.bindClassBehaviour(CaseActivityPolicies.OnCaseActivityStartedPolicy.QNAME,
                ActivityModel.TYPE_ACTIVITY,
                new ChainingJavaBehaviour(this, "onCaseActivityStarted", Behaviour.NotificationFrequency.EVERY_EVENT));
        policyComponent.bindClassBehaviour(CaseActivityPolicies.OnCaseActivityStoppedPolicy.QNAME,
                ActivityModel.TYPE_ACTIVITY,
                new ChainingJavaBehaviour(this, "onCaseActivityStopped", Behaviour.NotificationFrequency.EVERY_EVENT));
    }

    @Override
    public void onCaseActivityStarted(NodeRef activityRef) {
        String documentId = caseActivityService.getDocumentId(activityRef.toString());
        NodeRef documentNodeRef = new NodeRef(documentId);

        TransactionData data = getTransactionData();
        boolean isDataOwner = false;
        if (!data.hasOwner) {
            data.hasOwner = isDataOwner = true;
        }

        eventService.fireEvent(activityRef, documentNodeRef, ICaseEventModel.CONSTR_ACTIVITY_STARTED);

        if (dictionaryService.isSubClass(nodeService.getType(activityRef), StagesModel.TYPE_STAGE)) {
            Integer version = (Integer) nodeService.getProperty(activityRef, ActivityModel.PROP_TYPE_VERSION);
            if (version != null && version >= 1) {
                data.stagesToTryComplete.add(activityRef);
            }
        }

        if (isDataOwner) {
            tryToFireStageChildrenStoppedEvents(data, documentNodeRef);
            data.hasOwner = false;
        }
    }

    @Override
    public void onCaseActivityStopped(NodeRef activityRef) {
        String documentId = caseActivityService.getDocumentId(activityRef.toString());
        NodeRef documentNodeRef = new NodeRef(documentId);

        TransactionData data = getTransactionData();
        boolean isDataOwner = false;
        if (!data.hasOwner) {
            data.hasOwner = isDataOwner = true;
        }

        eventService.fireEvent(activityRef, documentNodeRef, ICaseEventModel.CONSTR_ACTIVITY_STOPPED);

        NodeRef parent = nodeService.getPrimaryParent(activityRef).getParentRef();
        if (parent != null && dictionaryService.isSubClass(nodeService.getType(parent), StagesModel.TYPE_STAGE)) {
            data.stagesToTryComplete.add(parent);
        }

        if (isDataOwner) {
            tryToFireStageChildrenStoppedEvents(data, documentNodeRef);
            data.hasOwner = false;
        }
    }

    private void tryToFireStageChildrenStoppedEvents(TransactionData data, NodeRef document) {

        Map<NodeRef, MutableInt> completedStages =
                TransactionalResourceHelper.getMap(STAGE_CHILDREN_COMPLETED_TXN_KEY);

        Queue<NodeRef> stages = new ArrayDeque<>(data.stagesToTryComplete);
        data.stagesToTryComplete.clear();

        while (!stages.isEmpty()) {

            NodeRef stage = stages.poll();

            CaseActivity activity = caseActivityService.getActivity(stage.toString());
            if (!caseActivityService.hasActiveChildren(activity)) {

                MutableInt completedCounter = completedStages.computeIfAbsent(stage, s -> new MutableInt(0));
                completedCounter.increment();

                if (completedCounter.intValue() > STAGE_COMPLETE_LIMIT) {
                    throw new IllegalStateException("Stage " + stage + " completed more than " + STAGE_COMPLETE_LIMIT +
                                                    " times. Seems it is a infinite loop. Document: " + document);
                }
                eventService.fireEvent(stage, document, ICaseEventModel.CONSTR_STAGE_CHILDREN_STOPPED);
            }
            for (NodeRef st : data.stagesToTryComplete) {
                if (!stages.contains(st)) {
                    stages.add(st);
                }
            }
            data.stagesToTryComplete.clear();
        }
    }

    private TransactionData getTransactionData() {
        TransactionData data = AlfrescoTransactionSupport.getResource(ACTIVITY_EVENT_TRIGGER_DATA_KEY);
        if (data == null) {
            data = new TransactionData();
            AlfrescoTransactionSupport.bindResource(ACTIVITY_EVENT_TRIGGER_DATA_KEY, data);
        }
        return data;
    }

    private static class TransactionData {
        boolean hasOwner = false;
        Set<NodeRef> stagesToTryComplete = new HashSet<>();
    }

    public void setCaseActivityService(CaseActivityService caseActivityService) {
        this.caseActivityService = caseActivityService;
    }

    public void setDictionaryService(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public void setEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }
}
