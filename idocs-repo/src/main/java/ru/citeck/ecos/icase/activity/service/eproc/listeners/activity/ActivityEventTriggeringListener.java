package ru.citeck.ecos.icase.activity.service.eproc.listeners.activity;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.util.transaction.TransactionSupportUtil;
import org.apache.commons.lang.mutable.MutableInt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.icase.activity.dto.ActivityDefinition;
import ru.citeck.ecos.icase.activity.dto.ActivityInstance;
import ru.citeck.ecos.icase.activity.dto.ActivityRef;
import ru.citeck.ecos.icase.activity.service.CaseActivityEventService;
import ru.citeck.ecos.icase.activity.service.CaseActivityService;
import ru.citeck.ecos.icase.activity.service.eproc.EProcActivityService;
import ru.citeck.ecos.icase.activity.service.eproc.EProcCaseActivityListenerManager;
import ru.citeck.ecos.icase.activity.service.eproc.EProcUtils;
import ru.citeck.ecos.icase.activity.service.eproc.listeners.OnStartedActivityListener;
import ru.citeck.ecos.icase.activity.service.eproc.listeners.OnStoppedActivityListener;
import ru.citeck.ecos.model.ICaseEventModel;
import ru.citeck.ecos.records2.RecordRef;

import javax.annotation.PostConstruct;
import java.util.*;

@Component
@Slf4j
public class ActivityEventTriggeringListener implements
        OnStartedActivityListener,
        OnStoppedActivityListener {

    private static final String ACTIVITY_EVENT_TRIGGER_DATA_KEY = "eproc-case-activity-event-trigger-data";
    private static final String STAGE_CHILDREN_COMPLETED_TXN_KEY = "eproc-case-activity-stage-children-completed";
    private static final int STAGE_COMPLETE_LIMIT = 40;

    private EProcCaseActivityListenerManager manager;
    private EProcActivityService eprocActivityService;
    private CaseActivityService caseActivityService;
    private CaseActivityEventService caseActivityEventService;

    @Autowired
    public ActivityEventTriggeringListener(EProcCaseActivityListenerManager manager,
                                           EProcActivityService eprocActivityService,
                                           CaseActivityService caseActivityService,
                                           CaseActivityEventService caseActivityEventService) {

        this.manager = manager;
        this.eprocActivityService = eprocActivityService;
        this.caseActivityService = caseActivityService;
        this.caseActivityEventService = caseActivityEventService;
    }

    @PostConstruct
    public void init() {
        manager.subscribeOnStarted(this);
        manager.subscribeOnStopped(this);
    }

    @Override
    public void onStartedActivity(ActivityRef activityRef) {
        TransactionData data = getTransactionData();
        boolean isDataOwner = false;
        if (log.isDebugEnabled()) {
            log.debug("onStartedActivity.activityRef: " + activityRef + ", hasOwner: " + data.hasOwner);
        }
        if (!data.hasOwner) {
            data.hasOwner = isDataOwner = true;
        }

        caseActivityEventService.fireEvent(activityRef, ICaseEventModel.CONSTR_ACTIVITY_STARTED);

        ActivityDefinition activityDefinition = eprocActivityService.getActivityDefinition(activityRef);
        if (EProcUtils.isStage(activityDefinition)) {
            if (log.isDebugEnabled()) {
                log.debug("onStartedActivity.stagesToTryComplete.add: " + activityRef +
                    ", size: " + data.stagesToTryComplete.size() +
                    ", thread: " + Thread.currentThread().getId() +
                    ", trx: " + TransactionSupportUtil.getTransactionId());
            }
            ActivityInstance activityInstance = eprocActivityService.getStateInstance(activityRef);
            data.stagesToTryComplete.add(activityInstance);
        }

        if (isDataOwner) {
            RecordRef caseRef = activityRef.getProcessId();
            tryToFireStageChildrenStoppedEvents(data, caseRef);
            data.hasOwner = false;
        }
    }

    @Override
    public void onStoppedActivity(ActivityRef activityRef) {
        TransactionData data = getTransactionData();
        boolean isDataOwner = false;
        if (log.isDebugEnabled()) {
            log.debug("onStoppedActivity.activityRef: " + activityRef + ", hasOwner: " + data.hasOwner);
        }
        if (!data.hasOwner) {
            data.hasOwner = isDataOwner = true;
        }

        //TODO: micro optimization: check, that parentActivity is stage and only after this request activity instance from service
        //This will optimize execution because of getStateInstance works recursively, getDefinition works from cache.
        ActivityInstance activityInstance = eprocActivityService.getStateInstance(activityRef);
        ActivityInstance parentInstance = activityInstance.getParentInstance();
        if (parentInstance != null && EProcUtils.isStage(parentInstance.getDefinition())) {
            if (log.isDebugEnabled()) {
                log.debug("onStoppedActivity.stagesToTryComplete.add: " + parentInstance +
                    ", activityRef: " + activityRef +
                    ", size: " + data.stagesToTryComplete.size() +
                    ", thread: " + Thread.currentThread().getId() +
                    ", trx: " + TransactionSupportUtil.getTransactionId());
            }
            data.stagesToTryComplete.add(parentInstance);
        }

        caseActivityEventService.fireEvent(activityRef, ICaseEventModel.CONSTR_ACTIVITY_STOPPED);

        if (isDataOwner) {
            RecordRef caseRef = activityRef.getProcessId();
            tryToFireStageChildrenStoppedEvents(data, caseRef);
            data.hasOwner = false;
        }
    }

    private void tryToFireStageChildrenStoppedEvents(TransactionData data, RecordRef caseRef) {

        Map<ActivityInstance, MutableInt> completedStages =
                TransactionalResourceHelper.getMap(STAGE_CHILDREN_COMPLETED_TXN_KEY);

        Queue<ActivityInstance> stages = new ArrayDeque<>(data.stagesToTryComplete);
        data.stagesToTryComplete.clear();

        while (!stages.isEmpty()) {

            ActivityInstance stage = stages.poll();

            ActivityRef stageRef = EProcUtils.composeActivityRef(stage, caseRef);
            if (!caseActivityService.hasActiveChildren(stageRef)) {

                if (log.isDebugEnabled()) {
                    log.debug("tryToFireStageChildrenStoppedEvents.stage: " + stage);
                }
                MutableInt completedCounter = completedStages.computeIfAbsent(stage, s -> new MutableInt(0));
                completedCounter.increment();

                if (completedCounter.intValue() > STAGE_COMPLETE_LIMIT) {
                    throw new IllegalStateException("Stage " + stage + " completed more than " + STAGE_COMPLETE_LIMIT +
                            " times. Seems it is a infinite loop. Document: " + caseRef);
                }
                caseActivityEventService.fireEvent(stageRef, ICaseEventModel.CONSTR_STAGE_CHILDREN_STOPPED);
            }
            for (ActivityInstance itrStage : data.stagesToTryComplete) {
                if (!stages.contains(itrStage)) {
                    stages.add(itrStage);
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
        Set<ActivityInstance> stagesToTryComplete = new LinkedHashSet<>();
    }
}
