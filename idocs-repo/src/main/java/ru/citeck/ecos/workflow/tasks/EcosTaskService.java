package ru.citeck.ecos.workflow.tasks;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.ParameterCheck;
import org.apache.commons.lang.StringUtils;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.NativeJavaObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.comment.CommentTag;
import ru.citeck.ecos.comment.EcosCommentTagService;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.locks.LockUtils;
import ru.citeck.ecos.model.CiteckWorkflowModel;
import ru.citeck.ecos.props.EcosPropertiesService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.workflow.owner.OwnerAction;
import ru.citeck.ecos.workflow.owner.OwnerService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class EcosTaskService {

    private static final String ECOS_TASK_STR_VARS_LIMIT_KEY = "ecos.task.variable.type.str.limit";

    private static final String TASKS_PREFIX = "task-%s";

    public static final String FIELD_COMMENT = "comment";

    private static final String ASSIGNEE_NOT_MATCH_ERR_MSG_KEY = "ecos.task.complete.assignee.validation.error";

    private Map<String, EngineTaskService> taskServices = new ConcurrentHashMap<>();

    private LockUtils lockUtils;

    @Autowired
    private OwnerService ownerService;

    @Autowired
    private EcosPropertiesService ecosProperties;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private EcosCommentTagService commentTagService;

    @Autowired
    private TaskErrorEventsEmitter errorEventsEmitter;

    @Autowired
    private TransactionService transactionService;

    public void endTask(String taskId, Map<String, Object> variables) {
        endTask(taskId, null, variables, null);
    }

    public void endTask(String taskId, String transition) {
        endTask(taskId, transition, null, null);
    }

    public void endTask(String taskId, String transition, Map<String, Object> variables) {
        endTask(taskId, transition, variables, null);
    }

    public void endTask(String taskId,
                        String transition,
                        Map<String, Object> variables,
                        Map<String, Object> transientVariables) {

        ParameterCheck.mandatoryString("taskId", taskId);

        validateStrFields(variables);
        validateStrFields(transientVariables);

        if (variables == null) {
            variables = Collections.emptyMap();
        }
        if (transientVariables == null) {
            transientVariables = Collections.emptyMap();
        }

        TaskId task = new TaskId(taskId);
        EngineTaskService taskService = needTaskService(task.getEngine());

        TaskInfo taskInfo = taskService.getTaskInfo(task.getLocalId());
        String assignee = taskInfo.getAssignee();

        String user = AuthenticationUtil.getFullyAuthenticatedUser();
        if (assignee != null && !AuthenticationUtil.isRunAsUserTheSystemUser()) {
            if (!assignee.equals(user)) {
                throw new IllegalStateException(I18NUtil.getMessage(ASSIGNEE_NOT_MATCH_ERR_MSG_KEY));
            }
        }

        boolean isAssigneeEmpty = StringUtils.isBlank(assignee);
        if (isAssigneeEmpty) {
            ownerService.changeOwner(taskId, OwnerAction.CLAIM, user);
        }

        Map<String, Object> finalVariables = new HashMap<>(variables);
        Map<String, Object> finalTransientVariables = new HashMap<>(transientVariables);

        try {
            RecordRef taskDocument = taskInfo.getDocument();
            org.alfresco.service.cmr.repository.MLText taskMlTitle = taskInfo.getMlTitle();

            lockUtils.doWithLock(String.format(TASKS_PREFIX, taskId),
                () -> {
                    addCommentToDocument(taskDocument, taskMlTitle, (String) finalVariables.get(FIELD_COMMENT));
                    taskService.endTask(task.getLocalId(), transition, finalVariables, finalTransientVariables);
                }
            );

            AuthenticationUtil.runAsSystem(() -> {
                addLastCompletedTaskDate(taskInfo.getDocument());
                return null;
            });
        } catch (RuntimeException exception) {
            if (isAssigneeEmpty) {
                try {
                    ownerService.changeOwner(taskId, OwnerAction.RELEASE, user);
                } catch (Exception changeOwnerException) {
                    log.error("Cannot release task with id: " + taskId, changeOwnerException);
                }
            }
            try {
                RetryingTransactionHelper helper = transactionService.getRetryingTransactionHelper();
                helper.doInTransaction(() -> {
                    TaskInfo errorTaskInfo = taskService.getTaskInfo(task.getLocalId());
                    errorEventsEmitter.emitEndTaskErrorEvent(errorTaskInfo, exception);
                    return null;
                }, true, true);
            } catch (Throwable e) {
                log.error("Failed to emit error event: {} - {} \non cause error {} - {}",
                    e.getMessage(), e, exception.getMessage(), exception);
            }

            unwrapJsExceptionAndThrow(exception);
        }
    }

    private void addCommentToDocument(RecordRef taskDocument, org.alfresco.service.cmr.repository.MLText taskMlTitle,
                                      String comment) {
        if (StringUtils.isBlank(comment) || RecordRef.isEmpty(taskDocument)) {
            return;
        }

        commentTagService.addCommentWithTag(taskDocument, comment, CommentTag.TASK,
            new MLText(taskMlTitle));
    }


    private void addLastCompletedTaskDate(RecordRef documentRef) {
        if (RecordRef.isEmpty(documentRef)) {
            return;
        }
        String nodeRefStr = documentRef.getId();
        if (NodeRef.isNodeRef(nodeRefStr)) {
            NodeRef nodeRef = new NodeRef(nodeRefStr);
            nodeService.setProperty(nodeRef, CiteckWorkflowModel.PROP_LAST_COMPLETED_TASK_DATE, new Date());
        }
    }

    private void unwrapJsExceptionAndThrow(RuntimeException exception) {

        Throwable ex = exception;

        while (ex.getCause() != null) {
            ex = ex.getCause();
        }

        if (ex instanceof JavaScriptException) {
            Object value = ((JavaScriptException) ex).getValue();
            if (value instanceof NativeJavaObject) {
                value = ((NativeJavaObject) value).unwrap();
            }
            StackTraceElement[] stackTrace = exception.getStackTrace();
            exception = new RuntimeException(String.valueOf(value));
            exception.setStackTrace(stackTrace);
        } else if (ex instanceof AlfrescoRuntimeException) {
            String msg = ((AlfrescoRuntimeException) ex).getMsgId();
            exception = new RuntimeException(msg);
            exception.addSuppressed(exception);
        }
        throw exception;
    }

    private void validateStrFields(Map<String, Object> variables) {

        if (variables == null) {
            return;
        }

        int limit = ecosProperties.getInt(ECOS_TASK_STR_VARS_LIMIT_KEY, 5000);
        if (limit <= 0) {
            return;
        }

        variables.forEach((k, v) -> {

            if (v instanceof String && ((String) v).length() > limit) {

                String msg = "Variable length can't exceed limit " + limit;
                log.error(msg + ". You can setup this limit by config key " + ECOS_TASK_STR_VARS_LIMIT_KEY +
                    " Value key: " + k + " Value: " + v);

                throw new IllegalArgumentException(msg);
            }
        });
    }

    public Optional<TaskInfo> getTaskInfo(String taskId) {
        TaskId task = new TaskId(taskId);
        EngineTaskService taskService = needTaskService(task.getEngine());
        TaskInfo taskInfo = taskService.getTaskInfo(task.getLocalId());
        if (taskInfo != null) {
            taskInfo = new EngineTaskInfo(task.getEngine(), taskInfo);
        }
        return Optional.ofNullable(taskInfo);
    }

    private EngineTaskService needTaskService(String engineId) {
        EngineTaskService taskService = taskServices.get(engineId);
        if (taskService == null) {
            throw new IllegalArgumentException("Task service for engine '" + engineId + "' is not registered");
        }
        return taskService;
    }

    private static class TaskId {

        @Getter
        private final String engine;
        @Getter
        private final String localId;

        TaskId(String taskId) {
            int delimIdx = taskId.indexOf('$');
            if (delimIdx == -1) {
                throw new IllegalArgumentException("Task id should has engine prefix. Task: '" + taskId + "'");
            }
            this.engine = taskId.substring(0, delimIdx);
            this.localId = taskId.substring(delimIdx + 1);
        }
    }

    public void register(String engine, EngineTaskService taskService) {
        taskServices.put(engine, taskService);
    }

    @Autowired
    public void setLockUtils(LockUtils lockUtils) {
        this.lockUtils = lockUtils;
    }
}
