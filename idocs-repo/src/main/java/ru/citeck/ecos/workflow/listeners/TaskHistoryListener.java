/*
 * Copyright (C) 2008-2020 Citeck LLC.
 *
 * This file is part of Citeck EcoS
 *
 * Citeck EcoS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Citeck EcoS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Citeck EcoS. If not, see <http://www.gnu.org/licenses/>.
 */
package ru.citeck.ecos.workflow.listeners;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.activiti.engine.TaskService;
import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.delegate.TaskListener;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.repo.workflow.WorkflowQNameConverter;
import org.alfresco.repo.workflow.activiti.ActivitiConstants;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.deputy.DeputyService;
import ru.citeck.ecos.history.HistoryEventType;
import ru.citeck.ecos.history.HistoryService;
import ru.citeck.ecos.history.TaskHistoryUtils;
import ru.citeck.ecos.model.CiteckWorkflowModel;
import ru.citeck.ecos.model.HistoryModel;
import ru.citeck.ecos.model.ICaseTaskModel;
import ru.citeck.ecos.role.CaseRoleService;
import ru.citeck.ecos.service.CiteckServices;

import java.io.Serializable;
import java.util.*;

import static ru.citeck.ecos.utils.WorkflowConstants.VAR_TASK_ORIGINAL_OWNER;

@Slf4j
public class TaskHistoryListener extends AbstractTaskListener {

    public static final String VAR_ADDITIONAL_EVENT_PROPERTIES = "event_additionalProperties";

    private static final String ALF_PREFIX = "alf_";
    private static final String ACTIVITI_PREFIX = ActivitiConstants.ENGINE_ID + "$";

    private static final Map<String, String> eventNames;

    static {
        eventNames = new HashMap<>(3);
        eventNames.put(TaskListener.EVENTNAME_CREATE, HistoryEventType.TASK_CREATE);
        eventNames.put(TaskListener.EVENTNAME_ASSIGNMENT, HistoryEventType.TASK_ASSIGN);
        eventNames.put(TaskListener.EVENTNAME_COMPLETE, HistoryEventType.TASK_COMPLETE);
    }

    private NodeService nodeService;
    private HistoryService historyService;
    private TaskHistoryUtils taskHistoryUtils;
    private NamespaceService namespaceService;
    private AuthorityService authorityService;
    private DeputyService deputyService;
    private TaskDataListenerUtils taskDataListenerUtils;
    private List<String> panelOfAuthorized; //группа уполномоченных
    private CaseRoleService caseRoleService;
    private TaskService taskService;

    private WorkflowQNameConverter qNameConverter;
    private String VAR_OUTCOME_PROPERTY_NAME;
    private String VAR_COMMENT;
    private String VAR_LAST_COMMENT;
    private String VAR_DESCRIPTION;
    private WorkflowDocumentResolverRegistry documentResolverRegistry;

    /* (non-Javadoc)
     * @see ru.citeck.ecos.workflow.listeners.AbstractTaskListener#notifyImpl(org.activiti.engine.delegate.DelegateTask)
     */
    @Override
    protected void notifyImpl(DelegateTask task) {

        String eventName = eventNames.get(task.getEventName());
        if (eventName == null) {
            log.warn("Unsupported activiti task event: " + task.getEventName());
            return;
        }
        NodeRef document = documentResolverRegistry.getResolver(task.getExecution()).getDocument(task.getExecution());

        Map<QName, Serializable> eventProperties = new HashMap<>();
        // task type
        QName taskType = QName.createQName((String) task.getVariable(ActivitiConstants.PROP_TASK_FORM_KEY),
            namespaceService);

        // task outcome
        QName outcomeProperty = (QName) task.getVariable(VAR_OUTCOME_PROPERTY_NAME);
        if (outcomeProperty == null) {
            outcomeProperty = WorkflowModel.PROP_OUTCOME;
        }
        String taskOutcome = (String) task.getVariable(qNameConverter.mapQNameToName(outcomeProperty));

        // comments
        String taskComment = (String) task.getVariable(VAR_COMMENT);
        String lastTaskComment = (String) task.getVariable(VAR_LAST_COMMENT);

        // task attachments
        ArrayList<NodeRef> taskAttachments = ListenerUtils.getTaskAttachments(task);

        // task assignee
        String assignee = task.getAssignee();

        NodeRef originalOwner = processOriginalOwner(task);
        if (originalOwner != null) {
            eventProperties.put(QName.createQName("", VAR_TASK_ORIGINAL_OWNER), originalOwner);
        }

        ArrayList<NodeRef> pooledActors = ListenerUtils.getPooledActors(task, authorityService);
        List<NodeRef> actors = ListenerUtils.getActors(task, authorityService);

        // additional properties if any
        Map<QName, Serializable> additionalProperties = getAdditionalProperties(task.getExecution());

        // persist it
        if (additionalProperties != null) {
            eventProperties.putAll(additionalProperties);
        }
        NodeRef bpmPackage = ListenerUtils.getWorkflowPackage(task);
        List<AssociationRef> packageAssocs = nodeService.getSourceAssocs(bpmPackage,
            ICaseTaskModel.ASSOC_WORKFLOW_PACKAGE);

        String roleName;
        if (assignee != null && CollectionUtils.isNotEmpty(panelOfAuthorized)) {
            List<NodeRef> listRoles = caseRoleService.getRoles(document);
            String authorizedName = taskHistoryUtils.getAuthorizedName(panelOfAuthorized, listRoles, assignee);
            roleName = StringUtils.isNoneBlank(authorizedName) ? authorizedName : taskHistoryUtils.getRoleName(
                packageAssocs, assignee, task.getId(), ActivitiConstants.ENGINE_ID);
        } else {
            roleName = taskHistoryUtils.getRoleName(packageAssocs, assignee, task.getId(), ActivitiConstants.ENGINE_ID);
            if (!packageAssocs.isEmpty()) {
                eventProperties.put(HistoryModel.PROP_CASE_TASK, packageAssocs.get(0).getSourceRef());
            }
        }

        Object formInfo = taskService.getVariable(task.getId(), "_formInfo");
        if (formInfo instanceof ObjectNode) {
            Object outcomeName = ((ObjectNode) formInfo).get("submitName");
            if (outcomeName instanceof ObjectNode) {
                Map<Locale, String> name = DataValue.create(outcomeName).asMap(Locale.class, String.class);
                eventProperties.put(HistoryModel.PROP_TASK_OUTCOME_NAME, new HashMap<>(name));
            }
        }

        eventProperties.put(HistoryModel.PROP_NAME, eventName);
        eventProperties.put(HistoryModel.PROP_TASK_INSTANCE_ID, ACTIVITI_PREFIX + task.getId());
        eventProperties.put(HistoryModel.PROP_TASK_TYPE, taskType);
        eventProperties.put(HistoryModel.PROP_TASK_OUTCOME, taskOutcome);
        eventProperties.put(HistoryModel.PROP_TASK_COMMENT, taskComment);
        eventProperties.put(HistoryModel.PROP_LAST_TASK_COMMENT, lastTaskComment);
        eventProperties.put(HistoryModel.PROP_TASK_ATTACHMENTS, taskAttachments);
        eventProperties.put(HistoryModel.PROP_TASK_POOLED_ACTORS, pooledActors);
        eventProperties.put(HistoryModel.PROP_TASK_ACTORS, new ArrayList<>(actors));
        eventProperties.put(HistoryModel.PROP_TASK_ROLE, roleName);
        eventProperties.put(HistoryModel.PROP_TASK_DUE_DATE, task.getDueDate());

        String taskTitleProp = qNameConverter.mapQNameToName(CiteckWorkflowModel.PROP_TASK_TITLE);
        eventProperties.put(HistoryModel.PROP_TASK_TITLE, (String) task.getVariable(taskTitleProp));

        String taskFormKey = ListenerUtils.getTaskFormKey(task);
        if (StringUtils.isNotBlank(taskFormKey) && !StringUtils.startsWith(taskFormKey, ALF_PREFIX)) {
            taskFormKey = ALF_PREFIX + taskFormKey;
        }

        eventProperties.put(HistoryModel.PROP_TASK_FORM_KEY, taskFormKey);
        eventProperties.put(HistoryModel.PROP_TASK_DEFINITION_KEY, null);
        eventProperties.put(HistoryModel.PROP_WORKFLOW_INSTANCE_ID, ACTIVITI_PREFIX + task.getProcessInstanceId());
        eventProperties.put(HistoryModel.PROP_WORKFLOW_DESCRIPTION, (Serializable) task.getExecution().getVariable(
            VAR_DESCRIPTION));
        eventProperties.put(HistoryModel.ASSOC_INITIATOR, assignee != null ? assignee : HistoryService.SYSTEM_USER);
        eventProperties.put(HistoryModel.ASSOC_DOCUMENT, document);

        taskDataListenerUtils.fillDocumentData(document, eventProperties);

        historyService.persistEvent(HistoryModel.TYPE_BASIC_EVENT, eventProperties);
    }

    private NodeRef processOriginalOwner(DelegateTask delegateTask) {
        String assignee = delegateTask.getAssignee();
        String originalOwner = (String) delegateTask.getVariableLocal(VAR_TASK_ORIGINAL_OWNER);
        if (assignee == null || originalOwner == null) {
            return null;
        }

        if (StringUtils.equals(assignee, originalOwner)) {
            return null;
        }

        if (deputyService.isAssistantUserByUser(originalOwner, assignee)) {
            return authorityService.getAuthorityNodeRef(originalOwner);
        }

        return null;
    }

    @SuppressWarnings("rawtypes")
    private Map<QName, Serializable> getAdditionalProperties(DelegateExecution execution) {
        Object additionalPropertiesObj = execution.getVariable(VAR_ADDITIONAL_EVENT_PROPERTIES);
        if (additionalPropertiesObj == null) {
            return null;
        }
        if (additionalPropertiesObj instanceof Map) {
            return convertProperties((Map) additionalPropertiesObj);
        }
        log.warn("Unknown type of additional event properties: " + additionalPropertiesObj.getClass());
        return null;
    }

    @SuppressWarnings("rawtypes")
    private Map<QName, Serializable> convertProperties(Map additionalProperties) {
        Map<QName, Serializable> result = new HashMap<>(additionalProperties.size());
        for (Object key : additionalProperties.keySet()) {
            QName name;
            if (key instanceof String) {
                name = qNameConverter.mapNameToQName((String) key);
            } else if (key instanceof QName) {
                name = (QName) key;
            } else {
                log.warn("Unknown type of key: " + key.getClass());
                continue;
            }
            result.put(name, (Serializable) additionalProperties.get(key));
        }
        return result;
    }

    @Override
    protected void initImpl() {
        historyService = (HistoryService) serviceRegistry.getService(CiteckServices.HISTORY_SERVICE);
        nodeService = serviceRegistry.getNodeService();
        namespaceService = serviceRegistry.getNamespaceService();
        authorityService = serviceRegistry.getAuthorityService();
        deputyService = (DeputyService) serviceRegistry.getService(CiteckServices.DEPUTY_SERVICE);
        documentResolverRegistry = getBean(WorkflowDocumentResolverRegistry.BEAN_NAME,
            WorkflowDocumentResolverRegistry.class);

        qNameConverter = new WorkflowQNameConverter(namespaceService);
        VAR_OUTCOME_PROPERTY_NAME = qNameConverter.mapQNameToName(WorkflowModel.PROP_OUTCOME_PROPERTY_NAME);
        VAR_COMMENT = qNameConverter.mapQNameToName(WorkflowModel.PROP_COMMENT);
        VAR_LAST_COMMENT = qNameConverter.mapQNameToName(CiteckWorkflowModel.PROP_LASTCOMMENT);
        VAR_DESCRIPTION = qNameConverter.mapQNameToName(WorkflowModel.PROP_WORKFLOW_DESCRIPTION);
    }

    public void setPanelOfAuthorized(List<String> panelOfAuthorized) {
        this.panelOfAuthorized = panelOfAuthorized;
    }

    @Autowired
    public void setCaseRoleService(CaseRoleService caseRoleService) {
        this.caseRoleService = caseRoleService;
    }

    @Autowired
    public void setTaskHistoryUtils(TaskHistoryUtils taskHistoryUtils) {
        this.taskHistoryUtils = taskHistoryUtils;
    }

    @Autowired
    public void setTaskDataListenerUtils(TaskDataListenerUtils taskDataListenerUtils) {
        this.taskDataListenerUtils = taskDataListenerUtils;
    }

    @Autowired
    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }
}
