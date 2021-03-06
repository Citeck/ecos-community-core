package ru.citeck.ecos.icase.activity.service.eproc.importer.parser;

public interface CmmnDefinitionConstants {

    String DOCUMENT_STATUS = "documentStatus";
    String CASE_STATUS = "caseStatus";

    String TASK_ROLE_VAR_NAMES_SET_KEY = "taskRoleVarNames";

    String WORKFLOW_DEFINITION_NAME = "workflowDefinitionName";
    String USE_ACTIVITY_TITLE = "useActivityTitle";
    String NAME = "name";
    String TITLE = "title";
    String EXPECTED_PERFORM_TIME = "expectedPerformTime";
    String PRIORITY = "priority";

    //action props
    String ACTION_TYPE = "actionType";
    String ACTION_SET_PROPERTY_PROP_NAME = "set-property-value.property";
    String ACTION_SET_PROPERTY_PROP_VALUE = "set-property-value.value";
    String ACTION_SET_PROCESS_VAR_NAME = "set-process-variable.variable";
    String ACTION_SET_PROCESS_VAR_VALUE = "set-process-variable.value";
    String ACTION_SET_STATUS_ACTION_STATUS_NAME = "actionCaseStatus";
    String ACTION_SEND_WORKFLOW_SIGNAL_NAME = "send-workflow-signal.signalName";
    String ACTION_SCRIPT = "execute-script.script";
    String ACTION_FAIL_MESSAGE = "fail.message";

    //completeness
    String COMPLETENESS_TYPE = "completenessType";
    String COMPLETENESS_LEVELS_SET = "completenessLevelsSet";

    //user-actions
    String AUTHORIZED_ROLE_VAR_NAMES_SET = "authorizedRoleVarNamesSet";
    String ADDITIONAL_DATA_TYPE = "additionalDataType";
    String CONFIRMATION_MESSAGE = "confirmationMessage";
    String SUCCESS_MESSAGE = "successMessage";
    String SUCCESS_MESSAGE_SPAN_CLASS = "successMessageSpanClass";

    //timers
    String TIMER_EXPRESSION = "timerExpression";
    String EXPRESSION_TYPE = "expressionType";
    String DATE_PRECISION = "datePrecision";

}
