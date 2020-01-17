/*
 * Copyright (C) 2008-2019 Citeck LLC.
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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.deputy.DeputyService;
import ru.citeck.ecos.history.HistoryEventType;
import ru.citeck.ecos.history.HistoryService;
import ru.citeck.ecos.history.TaskHistoryUtils;
import ru.citeck.ecos.model.CiteckWorkflowModel;
import ru.citeck.ecos.model.HistoryModel;
import ru.citeck.ecos.model.ICaseTaskModel;
import ru.citeck.ecos.service.CiteckServices;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskHistoryListener extends AbstractTaskListener {

    public static final String VAR_ADDITIONAL_EVENT_PROPERTIES = "event_additionalProperties";

    private static final Log logger = LogFactory.getLog(TaskHistoryListener.class);

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
    private List<String> panelOfAuthorized; //группа уполномоченных

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
            logger.warn("Unsupported activiti task event: " + task.getEventName());
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

        String originalOwner = (String) task.getVariableLocal("taskOriginalOwner");

        if (assignee != null && !assignee.equals(originalOwner)) {
            if (originalOwner != null && deputyService.isAssistantUserByUser(originalOwner, assignee)) {
                eventProperties.put(QName.createQName("", "taskOriginalOwner"),
                        authorityService.getAuthorityNodeRef(originalOwner));
            }
        }

        // pooled actors
        ArrayList<NodeRef> pooledActors = ListenerUtils.getPooledActors(task, authorityService);

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
            List<NodeRef> listRoles = taskHistoryUtils.getListRoles(document);
            String authorizedName = taskHistoryUtils.getAuthorizedName(panelOfAuthorized, listRoles, assignee);
            roleName = StringUtils.isNoneBlank(authorizedName) ? authorizedName : taskHistoryUtils.getRoleName(
                    packageAssocs, assignee, task.getId(), ActivitiConstants.ENGINE_ID);
        } else {
            roleName = taskHistoryUtils.getRoleName(packageAssocs, assignee, task.getId(), ActivitiConstants.ENGINE_ID);
            if (!packageAssocs.isEmpty()) {
                eventProperties.put(HistoryModel.PROP_CASE_TASK, packageAssocs.get(0).getSourceRef());
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
        eventProperties.put(HistoryModel.PROP_TASK_ROLE, roleName);
        eventProperties.put(HistoryModel.PROP_TASK_DUE_DATE, task.getDueDate());

        String taskTitleProp = qNameConverter.mapQNameToName(CiteckWorkflowModel.PROP_TASK_TITLE);
        eventProperties.put(HistoryModel.PROP_TASK_TITLE, (String) task.getVariable(taskTitleProp));

        String taskFormKey = ListenerUtils.getTaskFormKey(task);
        if (StringUtils.isNotBlank(taskFormKey) && !StringUtils.startsWith(taskFormKey, ALF_PREFIX)) {
            taskFormKey = ALF_PREFIX + taskFormKey;
        }

        eventProperties.put(HistoryModel.PROP_TASK_FORM_KEY, taskFormKey);
        eventProperties.put(HistoryModel.PROP_WORKFLOW_INSTANCE_ID, ACTIVITI_PREFIX + task.getProcessInstanceId());
        eventProperties.put(HistoryModel.PROP_WORKFLOW_DESCRIPTION, (Serializable) task.getExecution().getVariable(
                VAR_DESCRIPTION));
        eventProperties.put(HistoryModel.ASSOC_INITIATOR, assignee != null ? assignee : HistoryService.SYSTEM_USER);
        eventProperties.put(HistoryModel.ASSOC_DOCUMENT, document);
        historyService.persistEvent(HistoryModel.TYPE_BASIC_EVENT, eventProperties);
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
        logger.warn("Unknown type of additional event properties: " + additionalPropertiesObj.getClass());
        return null;
    }

    @SuppressWarnings("rawtypes")
    private Map<QName, Serializable> convertProperties(Map additionalProperties) {
        Map<QName, Serializable> result = new HashMap<>(additionalProperties.size());
        for (Map.Entry<?,?> entry : ((Map<?, ?>) additionalProperties).entrySet()) {
            Object key = entry.getKey();
            QName name = null;
            if (key instanceof String) {
                name = qNameConverter.mapNameToQName((String) key);
            } else if (key instanceof QName) {
                name = (QName) key;
            } else {
                logger.warn("Unknown type of key: " + key.getClass());
                continue;
            }
            result.put(name, (Serializable) entry.getValue());
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
    public void setTaskHistoryUtils(TaskHistoryUtils taskHistoryUtils) {
        this.taskHistoryUtils = taskHistoryUtils;
    }
}
