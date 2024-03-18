package ru.citeck.ecos.icase.activity.service.eproc;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.action.ActionConditionUtils;
import ru.citeck.ecos.icase.activity.dto.*;
import ru.citeck.ecos.icase.activity.service.CaseActivityEventDelegate;
import ru.citeck.ecos.model.ICaseEventModel;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.evaluator.RecordEvaluatorDto;
import ru.citeck.ecos.records2.evaluator.RecordEvaluatorService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class EProcCaseActivityEventDelegate implements CaseActivityEventDelegate {

    public static final String TRANSACTION_EVENT_VARIABLE = "event";

    private EProcActivityService eprocActivityService;
    private EProcCaseActivityListenerManager listenerManager;
    private EProcCaseEvaluatorConverter eprocCaseEvaluatorConverter;
    private RecordEvaluatorService recordEvaluatorService;

    @Autowired
    public EProcCaseActivityEventDelegate(EProcActivityService eprocActivityService,
                                          EProcCaseActivityListenerManager listenerManager,
                                          EProcCaseEvaluatorConverter eprocCaseEvaluatorConverter,
                                          RecordEvaluatorService recordEvaluatorService) {
        this.eprocActivityService = eprocActivityService;
        this.listenerManager = listenerManager;
        this.eprocCaseEvaluatorConverter = eprocCaseEvaluatorConverter;
        this.recordEvaluatorService = recordEvaluatorService;
    }

    @Override
    public void fireConcreteEvent(EventRef eventRef) {
        SentryDefinition sentryDefinition = eprocActivityService.getSentryDefinition(eventRef);
        fireConcreteEventImpl(eventRef.getProcessId(), sentryDefinition);
    }

    @Override
    public void fireEvent(ActivityRef activityRef, String eventType, boolean procDefRequired) {
        List<SentryDefinition> sentryDefs = eprocActivityService.findSentriesBySourceRefAndEventType(
            activityRef.getProcessId(), activityRef.getId(), eventType, procDefRequired);
        if (CollectionUtils.isEmpty(sentryDefs)) {
            return;
        }

        for (SentryDefinition sentryDef : sentryDefs) {
            fireConcreteEventImpl(activityRef.getProcessId(), sentryDef);
        }
    }

    private void fireConcreteEventImpl(RecordRef caseRef, SentryDefinition sentryDefinition) {
        if (checkConditionsImpl(caseRef, sentryDefinition)) {
            addScriptSentryToJSContext(caseRef, sentryDefinition);

            EventRef eventRef = EventRef.of(CaseServiceType.EPROC, caseRef, sentryDefinition.getId());
            listenerManager.beforeEventFired(eventRef);
            listenerManager.onEventFired(eventRef);
        }
    }

    @Override
    public boolean checkConditions(EventRef eventRef) {
        SentryDefinition sentryDefinition = eprocActivityService.getSentryDefinition(eventRef);
        return checkConditionsImpl(eventRef.getProcessId(), sentryDefinition);
    }

    private boolean checkConditionsImpl(RecordRef caseRef, SentryDefinition sentryDefinition) {
        addScriptSentryToJSContext(caseRef, sentryDefinition);

        RecordEvaluatorDto evaluatorDefinition = eprocCaseEvaluatorConverter
                .convertEvaluatorDefinition(sentryDefinition.getEvaluator());
        if (evaluatorDefinition != null) {
            return recordEvaluatorService.evaluate(caseRef, evaluatorDefinition);
        }
        return true;
    }

    //TODO: Script sentry is bad. Hardcode. Uses now for support of old functionality in existing processes
    private void addScriptSentryToJSContext(RecordRef caseRef, SentryDefinition sentryDefinition) {
        ActivityDefinition userActionEventDefinition = sentryDefinition
                .getParentTriggerDefinition()
                .getParentActivityTransitionDefinition()
                .getParentActivityDefinition();

        SentryDefinition actualSentry = sentryDefinition;

        List<SentryDefinition> sentries = eprocActivityService.findSentriesBySourceRefAndEventType(caseRef,
                userActionEventDefinition.getId(), ICaseEventModel.CONSTR_ACTIVITY_STARTED);
        if (CollectionUtils.isEmpty(sentries)) {
            sentries = eprocActivityService.findSentriesBySourceRefAndEventType(caseRef,
                    userActionEventDefinition.getId(), ICaseEventModel.CONSTR_ACTIVITY_STOPPED);
        }

        if (CollectionUtils.isNotEmpty(sentries)) {
            actualSentry = sentries.get(0);
        }

        ActivityDefinition actualActivityDefinition = actualSentry
                .getParentTriggerDefinition()
                .getParentActivityTransitionDefinition()
                .getParentActivityDefinition();

        ActivityRef activityRef = ActivityRef.of(CaseServiceType.EPROC, caseRef, actualActivityDefinition.getId());
        ActivityInstance activityInstance = eprocActivityService.getStateInstance(activityRef);

        ScriptSentry scriptSentry = new ScriptSentry(activityInstance);
        ActionConditionUtils.getTransactionVariables().put(TRANSACTION_EVENT_VARIABLE, scriptSentry);
    }

    @Data
    @EqualsAndHashCode(doNotUseGetters = true)
    @ToString(doNotUseGetters = true)
    public static class ScriptSentry {
        private final ActivityInstance activityInstance;

        private ScriptActivityInstance parent;

        public ScriptActivityInstance getParent() {
            if (parent == null) {
                parent = new ScriptActivityInstance(activityInstance);
            }
            return parent;
        }
    }

    @Data
    @EqualsAndHashCode(doNotUseGetters = true)
    @ToString(doNotUseGetters = true)
    public static class ScriptActivityInstance {
        private final ActivityInstance activityInstance;

        private ScriptActivityInstance parent;
        private Map<String, Object> properties;

        public ScriptActivityInstance getParent() {
            if (parent == null) {
                parent = new ScriptActivityInstance(activityInstance.getParentInstance());
            }
            return parent;
        }

        public Map<String, Object> getProperties() {
            if (properties == null) {
                Map<String, Object> map = new HashMap<>();
                map.put("lc:state", activityInstance.getState().getValue());
                properties = map;
            }
            return properties;
        }
    }

}
