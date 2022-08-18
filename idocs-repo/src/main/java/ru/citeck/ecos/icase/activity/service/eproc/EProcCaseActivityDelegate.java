package ru.citeck.ecos.icase.activity.service.eproc;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.icase.activity.dto.*;
import ru.citeck.ecos.icase.activity.service.CaseActivityDelegate;
import ru.citeck.ecos.icase.activity.service.eproc.importer.parser.CmmnDefinitionConstants;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.evaluator.RecordEvaluatorDto;
import ru.citeck.ecos.records2.evaluator.RecordEvaluatorService;
import ru.citeck.ecos.records2.evaluator.details.EvalDetails;
import ru.citeck.ecos.records2.evaluator.details.EvalResultCause;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EProcCaseActivityDelegate implements CaseActivityDelegate {

    private static final String CHECK_LIST_EXCEPTION_HEADER_MESSAGE =
            "eproc.transition-exception.check-lists-not-completed-header.message";
    private static final String CHECK_LIST_EXCEPTION_DEFAULT_MESSAGE =
            "eproc.transition-exception.default-transition-denied.message";

    private EProcActivityService eprocActivityService;
    private EProcCaseActivityListenerManager listenerManager;
    private EProcCaseEvaluatorConverter eprocCaseEvaluatorConverter;
    private RecordEvaluatorService recordEvaluatorService;

    @Autowired
    public EProcCaseActivityDelegate(EProcActivityService eprocActivityService,
                                     EProcCaseActivityListenerManager listenerManager,
                                     EProcCaseEvaluatorConverter eprocCaseEvaluatorConverter,
                                     RecordEvaluatorService recordEvaluatorService) {
        this.eprocActivityService = eprocActivityService;
        this.listenerManager = listenerManager;
        this.eprocCaseEvaluatorConverter = eprocCaseEvaluatorConverter;
        this.recordEvaluatorService = recordEvaluatorService;
    }

    public void doTransition(ActivityRef activityRef, ActivityTransitionDefinition transitionDefinition) {
        ActivityInstance instance = eprocActivityService.getStateInstance(activityRef);
        RecordRef caseRef = activityRef.getProcessId();
        switch (transitionDefinition.getToState()) {
            case STARTED:
                if (transitionDefinition.isRestartRequired()) {
                    resetRecursive(caseRef, instance);
                }
                startActivityImpl(instance, caseRef, transitionDefinition);
                break;
            case COMPLETED:
                stopActivityImpl(instance, caseRef, transitionDefinition);
                break;
        }
    }

    @Override
    public void startActivity(ActivityRef activityRef) {
        ActivityInstance instance = eprocActivityService.getStateInstance(activityRef);
        ActivityTransitionDefinition transitionDefinition = getTransitionDefinition(
            instance.getDefinition().getTransitions(),
            ActivityState.NOT_STARTED,
            ActivityState.STARTED
        );

        if (transitionDefinition == null) {
            return;
        }

        startActivityImpl(instance, activityRef.getProcessId(), transitionDefinition);
    }

    private void startActivityImpl(ActivityInstance instance, RecordRef caseRef,
                                   ActivityTransitionDefinition transitionDefinition) {

        boolean isResetPerformed = false;
        if (needResetBeforeStart(instance)) {
            resetRecursive(caseRef, instance);
            isResetPerformed = true;
        }

        if (!isResetPerformed && transitionIsNotAllowed(instance, transitionDefinition)) {
            return;
        }

        checkTransitionCondition(caseRef, transitionDefinition);

        instance.setState(ActivityState.STARTED);
        instance.setActivated(new Date());

        ActivityRef activityRef = ActivityRef.of(CaseServiceType.EPROC, caseRef, instance.getId());
        listenerManager.beforeStartedActivity(activityRef);
        listenerManager.onStartedActivity(activityRef);

        eprocActivityService.saveState(caseRef);
    }

    private boolean needResetBeforeStart(ActivityInstance instance) {
        ActivityState fromState = instance.getState();
        if (fromState != ActivityState.NOT_STARTED) {
            return instance.getDefinition().isRepeatable();
        }
        return false;
    }

    @Override
    public void stopActivity(ActivityRef activityRef) {
        ActivityInstance instance = eprocActivityService.getStateInstance(activityRef);
        ActivityTransitionDefinition transitionDefinition = getTransitionDefinition(instance, ActivityState.COMPLETED);
        if (transitionDefinition == null) {
            return;
        }

        stopActivityImpl(instance, activityRef.getProcessId(), transitionDefinition);
    }

    private void stopActivityImpl(ActivityInstance instance, RecordRef caseRef,
                                  ActivityTransitionDefinition transitionDefinition) {

        if (transitionIsNotAllowed(instance, transitionDefinition)) {
            return;
        }

        checkTransitionCondition(caseRef, transitionDefinition);

        instance.setState(ActivityState.COMPLETED);
        instance.setTerminated(new Date());

        ActivityRef activityRef = ActivityRef.of(CaseServiceType.EPROC, caseRef, instance.getId());
        listenerManager.beforeStoppedActivity(activityRef);
        listenerManager.onStoppedActivity(activityRef);

        eprocActivityService.saveState(caseRef);
    }

    private boolean transitionIsNotAllowed(ActivityInstance instance, ActivityTransitionDefinition transitionDefinition) {
        return transitionDefinition.getFromState() != instance.getState();
    }

    private ActivityTransitionDefinition getTransitionDefinition(ActivityInstance instance, ActivityState toState) {
        List<ActivityTransitionDefinition> transitions = instance.getDefinition().getTransitions();
        ActivityState fromState = instance.getState();
        return getTransitionDefinition(transitions, fromState, toState);
    }

    private ActivityTransitionDefinition getTransitionDefinition(List<ActivityTransitionDefinition> transitions,
                                                                 ActivityState fromState, ActivityState toState) {
        if (CollectionUtils.isEmpty(transitions)) {
            return null;
        }

        return transitions.stream()
                .filter(transition -> transition.getFromState() == fromState)
                .filter(transition -> transition.getToState() == toState)
                .findFirst().orElse(null);
    }

    private void checkTransitionCondition(RecordRef caseRef, ActivityTransitionDefinition transitionDefinition) {
        RecordEvaluatorDto evaluatorDto = eprocCaseEvaluatorConverter
                .convertEvaluatorDefinition(transitionDefinition.getEvaluator());
        if (evaluatorDto == null) {
            return;
        }

        EvalDetails evalDetails = recordEvaluatorService.evalWithDetails(caseRef, evaluatorDto);
        boolean success = evalDetails.getResult();
        if (success) {
            return;
        }

        List<EvalResultCause> causes = evalDetails.getCauses();
        throw new RuntimeException(getLocalizedExceptionMessage(causes));
    }

    private String getLocalizedExceptionMessage(List<EvalResultCause> causes) {
        if (CollectionUtils.isEmpty(causes)) {
            return getLocalized(CHECK_LIST_EXCEPTION_DEFAULT_MESSAGE);
        }

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(getLocalized(CHECK_LIST_EXCEPTION_HEADER_MESSAGE))
                .append("\n");
        for (EvalResultCause cause : causes) {
            messageBuilder.append(cause.getLocalizedMessage())
                    .append("\n");
        }
        return messageBuilder.toString();
    }

    private String getLocalized(String key) {
        return I18NUtil.getMessage(key);
    }

    @Override
    public void reset(ActivityRef activityRef) {
        RecordRef caseRef = activityRef.getProcessId();

        ActivityInstance instance = eprocActivityService.getStateInstance(activityRef);
        resetRecursive(caseRef, instance);

        eprocActivityService.saveState(caseRef);
    }

    private void resetRecursive(RecordRef processId, ActivityInstance instance) {
        instance.setState(ActivityState.NOT_STARTED);
        instance.setActivated(null);
        instance.setTerminated(null);

        ActivityRef activityRef = ActivityRef.of(CaseServiceType.EPROC, processId, instance.getId());
        listenerManager.onResetActivity(activityRef);

        if (CollectionUtils.isNotEmpty(instance.getActivities())) {
            for (ActivityInstance childInstance : instance.getActivities()) {
                resetRecursive(processId, childInstance);
            }
        }
    }

    @Override
    public CaseActivity getActivity(ActivityRef activityRef) {
        ActivityInstance instance = eprocActivityService.getStateInstance(activityRef);
        return toCaseActivity(activityRef, instance);
    }

    @Override
    public CaseActivity getParentActivity(ActivityRef childActivityRef) {
        ActivityInstance childInstance = eprocActivityService.getStateInstance(childActivityRef);
        ActivityInstance parentInstance = childInstance.getParentInstance();
        if (parentInstance == null) {
            return null;
        }
        return toCaseActivity(childActivityRef.getProcessId(), parentInstance);
    }

    @Override
    public List<CaseActivity> getActivities(ActivityRef activityRef) {
        return getActivities(activityRef, false);
    }

    @Override
    public List<CaseActivity> getActivities(ActivityRef activityRef, boolean recurse) {
        ActivityInstance instance = eprocActivityService.getStateInstance(activityRef);
        List<ActivityInstance> activities = getActivitiesImpl(instance, recurse);
        return activities.stream()
                .map(item -> toCaseActivity(activityRef.getProcessId(), item))
                .sorted(Comparator.comparingInt(CaseActivity::getIndex))
                .collect(Collectors.toList());
    }

    private CaseActivity toCaseActivity(RecordRef caseRef, ActivityInstance instance) {
        ActivityRef activityRef = ActivityRef.of(CaseServiceType.EPROC, caseRef, instance.getId());
        return toCaseActivity(activityRef, instance);
    }

    private CaseActivity toCaseActivity(ActivityRef activityRef, ActivityInstance instance) {
        CaseActivity result = new CaseActivity();
        result.setActivityRef(activityRef);
        result.setActivityType(instance.getDefinition().getType());
        result.setTitle(EProcUtils.getAnyAttribute(instance, CmmnDefinitionConstants.TITLE));
        result.setState(instance.getState());
        result.setActive(instance.getState() == ActivityState.STARTED);
        result.setRepeatable(instance.getDefinition().isRepeatable());
        result.setIndex(instance.getDefinition().getIndex());
        result.setStartDate(instance.getActivated());
        result.setCompleteDate(instance.getTerminated());
        return result;
    }

    private List<ActivityInstance> getActivitiesImpl(ActivityInstance instance, boolean recurse) {
        List<ActivityInstance> instances = instance.getActivities();
        if (instances == null) {
            instances = new ArrayList<>();
        }

        List<ActivityInstance> foundInstances = new ArrayList<>(instances);
        if (recurse) {
            for (ActivityInstance childInstance : instances) {
                foundInstances.addAll(getActivitiesImpl(childInstance, true));
            }
        }

        return foundInstances;
    }

    @Override
    public List<CaseActivity> getStartedActivities(ActivityRef activityRef) {
        return getActivities(activityRef).stream()
                .filter(CaseActivity::isActive)
                .collect(Collectors.toList());
    }

    @Override
    public CaseActivity getActivityByName(ActivityRef activityRef, String name, boolean recurse) {
        ActivityInstance instance = eprocActivityService.getStateInstance(activityRef);
        return getActivitiesImpl(instance, recurse).stream()
                .filter(childInstance -> {
                    String instanceName = EProcUtils.getAnyAttribute(childInstance, CmmnDefinitionConstants.NAME);
                    return StringUtils.equals(name, instanceName);
                })
                .map(childInstance -> toCaseActivity(activityRef.getProcessId(), childInstance))
                .findFirst().orElse(null);
    }

    @Override
    public CaseActivity getActivityByTitle(ActivityRef activityRef, String title, boolean recurse) {
        return getActivities(activityRef, recurse).stream()
                .filter(caseActivity -> StringUtils.equals(caseActivity.getTitle(), title))
                .findFirst().orElse(null);
    }

    @Override
    public void setParent(ActivityRef activityRef, ActivityRef parentRef) {
        throw new UnsupportedOperationException("Method not implemented");
    }

    @Override
    public void setParentInIndex(ActivityRef activityRef, int newIndex) {
        throw new UnsupportedOperationException("Method not implemented");
    }

    @Override
    public boolean hasActiveChildren(ActivityRef activityRef) {
        return getActivities(activityRef).stream()
                .anyMatch(CaseActivity::isActive);
    }
}
