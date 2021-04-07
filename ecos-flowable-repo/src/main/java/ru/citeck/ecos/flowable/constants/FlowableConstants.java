package ru.citeck.ecos.flowable.constants;

/**
 * Flowable constants
 */
public class FlowableConstants {

    public static final String ENGINE_ID = "flowable";
    public static final String ENGINE_PREFIX = ENGINE_ID + "$";

    public static final String SERVICE_REGISTRY_BEAN_KEY = "serviceRegistry";
    public static final String NOTIFICATION_SERVICE_BEAN_KEY = "ecosNotificationService";
    public static final String GLOBAL_PROPERTIES_BEAN_KEY = "global-properties";
    public static final String FLOWABLE_EMAIL_SENDER_BEAN_KEY = "ecosFlowableEmailSender";

    public static final String COMPLETENESS_SERVICE_JS_KEY = "completeness";
    public static final String CASE_STATUS_SERVICE_JS_KEY = "caseStatusService";

    public static final String DELETE_REASON_DELETED = "deleted";
    public static final String DELETE_REASON_CANCELLED = "cancelled";

    public static final String PROP_START_TASK_END_DATE = "_startTaskCompleted";
    public static final String START_TASK_PREFIX = "start";

    public static final String PROP_POOLED_ACTORS_HISTORY = "pooledActorsHistory";
    public static final String PROP_TASK_FORM_KEY = "taskFormKey";
}
