package ru.citeck.ecos.model;

import org.alfresco.service.namespace.QName;

/**
 * @author Maxim Strizhov
 */
public interface StagesModel {
    // model
    String PREFIX = "stages";

    // namespace
    String NAMESPACE = "http://www.citeck.ru/model/stages/1.0";

    // types
    QName TYPE_STAGE = QName.createQName(NAMESPACE, "stage");

    // aspects
    QName ASPECT_HAS_START_COMPLETENESS_LEVELS_RESTRICTION = QName.createQName(NAMESPACE, "hasStartCompletenessLevelsRestriction");
    QName ASPECT_HAS_END_COMPLETENESS_LEVELS_RESTRICTION = QName.createQName(NAMESPACE, "hasStopCompletenessLevelsRestriction");

    // properties
    QName PROP_STATE = QName.createQName(NAMESPACE, "state");
    QName PROP_PLANNED_START_DATE = ActivityModel.PROP_PLANNED_START_DATE;
    QName PROP_PLANNED_END_DATE = ActivityModel.PROP_PLANNED_END_DATE;
    QName PROP_ACTUAL_START_DATE = ActivityModel.PROP_ACTUAL_START_DATE;
    QName PROP_ACTUAL_END_DATE = ActivityModel.PROP_ACTUAL_END_DATE;
    QName PROP_START_EVENT = QName.createQName(NAMESPACE, "startEvent");
    QName PROP_STOP_EVENT = QName.createQName(NAMESPACE, "stopEvent");
    QName PROP_START_EVENT_TIMER = QName.createQName(NAMESPACE, "startEventTimer");
    QName PROP_STOP_EVENT_TIMER = QName.createQName(NAMESPACE, "stopEventTimer");
    QName PROP_START_EVENT_LIFE_CYCLE_STAGE = QName.createQName(NAMESPACE, "startEventLifeCycleStage");
    QName PROP_STOP_EVENT_LIFE_CYCLE_STAGE = QName.createQName(NAMESPACE, "stopEventLifeCycleStage");
    QName PROP_DOCUMENT_STATUS = QName.createQName(NAMESPACE, "documentStatus");

    // association
    QName ASSOC_CHILD_STAGES = QName.createQName(NAMESPACE, "childStages");
    QName ASSOC_START_COMPLETENESS_LEVELS_RESTRICTION = QName.createQName(NAMESPACE, "startCompletenessLevelsRestriction");
    QName ASSOC_STOP_COMPLETENESS_LEVELS_RESTRICTION = QName.createQName(NAMESPACE, "stopCompletenessLevelsRestriction");
    QName ASSOC_START_EVENT_STAGE = QName.createQName(NAMESPACE, "startEventStage");
    QName ASSOC_STOP_EVENT_STAGE = QName.createQName(NAMESPACE, "stopEventStage");
    QName ASSOC_CASE_STATUS = QName.createQName(NAMESPACE, "caseStatusAssoc");
    QName ASSOC_CASE_STATUS_PROP = QName.createQName(NAMESPACE, "caseStatusAssoc-prop");

    // constraint
    String CONSTR_USER_ACTION = "userAction";
    String CONSTR_TIMER = "timer";
    String CONSTR_LIFE_CYCLE_ENTER = "lifeCycleEnter";
    String CONSTR_LIFE_CYCLE_EXIT = "lifeCycleExit";
    String CONSTR_STAGE_START = "stageStart";
    String CONSTR_STAGE_END = "stageEnd";

    String CONSTR_STAGE_STATE_NOT_STARTED = "notStarted";
    String CONSTR_STAGE_STATE_STARTED = "started";
    String CONSTR_STAGE_STATE_STOPPED = "stopped";
}
