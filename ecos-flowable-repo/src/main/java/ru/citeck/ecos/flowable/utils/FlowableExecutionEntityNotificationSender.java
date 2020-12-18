package ru.citeck.ecos.flowable.utils;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.notification.EMailNotificationProvider;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.repo.workflow.WorkflowQNameConverter;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.notification.NotificationContext;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.TemplateService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import ru.citeck.ecos.flowable.constants.FlowableConstants;
import ru.citeck.ecos.model.DmsModel;
import ru.citeck.ecos.notification.AbstractNotificationSender;
import ru.citeck.ecos.notification.task.record.TaskExecutionRecord;
import ru.citeck.ecos.notifications.lib.Notification;
import ru.citeck.ecos.notifications.lib.NotificationType;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.security.NodeOwnerDAO;

import java.io.Serializable;
import java.util.*;

/**
 * Flowable execution notification sender
 */
public class FlowableExecutionEntityNotificationSender extends AbstractNotificationSender<ExecutionEntity> {

    private static final String DOCS_INFO_KEY = FlowableExecutionEntityNotificationSender.class.getName() + ".docsInfo";
    public static final String ARG_TASK = "task";
    public static final String ARG_TASK_ID = "id";
    public static final String ARG_TASK_NAME = "name";
    public static final String ARG_TASK_DESCRIPTION = "description";
    public static final String ARG_TASK_EDITOR = "editor";
    public static final String ARG_TASK_PROPERTIES = "properties";
    public static final String ARG_TASK_PROPERTIES_PRIORITY = "bpm_priority";
    public static final String ARG_TASK_PROPERTIES_DESCRIPTION = "bpm_description";
    public static final String ARG_TASK_PROPERTIES_DUEDATE = "bpm_dueDate";
    public static final String ARG_WORKFLOW = "workflow";
    public static final String ARG_WORKFLOW_ID = "id";
    public static final String ARG_WORKFLOW_PROPERTIES = "properties";
    public static final String ARG_WORKFLOW_DOCUMENTS = "documents";

    protected WorkflowQNameConverter qNameConverter;
    protected PersonService personService;
    protected AuthenticationService authenticationService;
    protected boolean sendToOwner;
    private NodeOwnerDAO nodeOwnerDAO;
    private static final Log logger = LogFactory.getLog(FlowableExecutionEntityNotificationSender.class);
    public static final String ARG_MODIFIER = "modifier";
    List<String> allowDocList;
    Map<String, Map<String, String>> subjectTemplates;
    private TemplateService templateService;
    private String nodeVariable;
    private String templateEngine;

    @Override
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        super.setServiceRegistry(serviceRegistry);
        this.qNameConverter = new WorkflowQNameConverter(namespaceService);
        this.authenticationService = serviceRegistry.getAuthenticationService();
        this.personService = serviceRegistry.getPersonService();
    }

    /**
     * Recipients provided as parameter taskSubscribers: "task name"-{"doc type1"-"recepient field1", ...}
     */
    @Deprecated
    public void setTaskSubscribers(Map<String, Map<String, List<String>>> taskSubscribers) {
        // not used
    }

    // get notification template arguments for the task
    protected Map<String, Serializable> getNotificationArgs(ExecutionEntity task) {
        Map<String, Serializable> args = new HashMap<>();
        args.put(ARG_WORKFLOW, getWorkflowInfo(task));
        String userName = authenticationService.getCurrentUserName();
        NodeRef person = personService.getPerson(userName);
        String lastName = (String) nodeService.getProperty(person, ContentModel.PROP_FIRSTNAME);
        String firstName = (String) nodeService.getProperty(person, ContentModel.PROP_LASTNAME);
        args.put(ARG_MODIFIER, lastName + " " + firstName);
        return args;
    }

    @Override
    protected Map<String, Object> getEcosNotificationArgs(ExecutionEntity task) {
        Map<String, Object> args = super.getEcosNotificationArgs(task);

        TaskExecutionRecord taskExecutionRecord = executionsTaskService
            .getExecutionRecord(ExecutionEntity.class, task)
            .orElse(null);
        args.put("_record", taskExecutionRecord);

        return args;
    }

    private Serializable getWorkflowInfo(ExecutionEntity task) {
        HashMap<String, Object> workflowInfo = new HashMap<>();
        workflowInfo.put(ARG_WORKFLOW_ID, task.getId());
        HashMap<String, Serializable> properties = new HashMap<>();
        workflowInfo.put(ARG_WORKFLOW_PROPERTIES, properties);
        for (Map.Entry<String, Object> entry : task.getVariables().entrySet()) {
            if (entry.getValue() != null) {
                properties.put(entry.getKey(), entry.getValue().toString());
            } else {
                properties.put(entry.getKey(), null);
            }
        }
        workflowInfo.put(ARG_WORKFLOW_DOCUMENTS, getDocsInfo());
        return workflowInfo;
    }

    /**
     * Method send notificatiion about start task to notification recipients.
     * Mail sends to each document to subscriber because task can containls a lot of different documents
     * and these documents can contains different subscriber.
     */
    @Override
    public void sendNotification(ExecutionEntity task) {

        NodeRef workflowPackage = (NodeRef) task.getVariable("bpm_package");
        if (workflowPackage == null || !nodeService.exists(workflowPackage)) {
            return;
        }

        calcDocsInfo(workflowPackage);
        NodeRef docsInfo = getDocsInfo();
        if (docsInfo == null || !nodeService.exists(docsInfo)) {
            return;
        }

        NodeRef template = getNotificationTemplate(task);
        if (template == null || !nodeService.exists(template)) {
            return;
        }
        String notifyTemplate = (String) nodeService.getProperty(template, DmsModel.PROP_ECOS_NOTIFICATION_TEMPLATE);
        if (StringUtils.isNotBlank(notifyTemplate)) {
            String subject = getSubject(task, docsInfo, template);
            ArrayList<String> recipient = new ArrayList<>(getRecipients(task, template, docsInfo));
            send(task, subject, notifyTemplate, recipient);
        } else {
            sendDeprecated(task, docsInfo, template);
        }

    }

    private void send(ExecutionEntity task, String subject, String template, ArrayList<String> recipients) {
        Map<String, Object> additionalMeta = getEcosNotificationArgs(task);

        Object record = additionalMeta.get("_record");
        additionalMeta.remove("_record");
        additionalMeta.put("subject", subject);

        Notification notification = new Notification.Builder()
            .record(record)
            .templateRef(RecordRef.valueOf(template))
            .notificationType(NotificationType.EMAIL_NOTIFICATION)
            .recipients(getEmailFromAuthorityNames(recipients))
            .additionalMeta(additionalMeta)
            .build();

        notificationService.send(notification);
    }

    private void sendDeprecated(ExecutionEntity task, NodeRef docsInfo, NodeRef template) {
        NotificationContext notificationContext = new NotificationContext();
        String notificationProviderName = EMailNotificationProvider.NAME;
        String subject = getSubject(task, docsInfo, template);
        ArrayList<String> recipient = new ArrayList<>(getRecipients(task, template, docsInfo));
        for (String to : recipient) {
            notificationContext.addTo(to);
        }
        notificationContext.setSubject(subject);
        setBodyTemplate(notificationContext, template);
        notificationContext.setTemplateArgs(getNotificationArgs(task));
        notificationContext.setAsyncNotification(getAsyncNotification());

        services.getNotificationService().sendNotification(notificationProviderName, notificationContext);
    }

    private String getSubject(ExecutionEntity task, NodeRef docsInfo, NodeRef template) {
        if (subjectTemplates != null) {
            String processDef = task.getProcessDefinitionId();
            String wfkey = FlowableConstants.ENGINE_PREFIX + processDef.substring(0, processDef.indexOf(':'));
            if (subjectTemplates.containsKey(wfkey)) {
                Map<String, String> taskSubjectTemplate = subjectTemplates.get(wfkey);
                if (taskSubjectTemplate.containsKey(qNameConverter.mapQNameToName(nodeService.getType(docsInfo)))) {
                    HashMap<String, Object> model = new HashMap<>(1);
                    model.put(nodeVariable, docsInfo);
                    return templateService.processTemplateString(templateEngine, taskSubjectTemplate.get(qNameConverter.mapQNameToName(nodeService.getType(docsInfo))), model);
                }
            } else {
                return (String) nodeService.getProperty(template, ContentModel.PROP_TITLE);
            }
        } else {
            return (String) nodeService.getProperty(template, ContentModel.PROP_TITLE);
        }

        return null;
    }

    private void calcDocsInfo(NodeRef workflowPackage) {
        List<ChildAssociationRef> children = services.getNodeService().getChildAssocs(workflowPackage);
        for (ChildAssociationRef child : children) {
            NodeRef node = child.getChildRef();
            if (node != null && nodeService.exists(node)) {
                if (allowDocList == null) {
                    setDocsInfo(node);
                    break;
                } else {
                    if (allowDocList.contains(qNameConverter.mapQNameToName(nodeService.getType(node)))) {
                        setDocsInfo(node);
                        break;
                    }
                }
            }
        }
    }

    public NodeRef getNotificationTemplate(ExecutionEntity task) {
        String processDef = task.getProcessDefinitionId();
        String wfkey = FlowableConstants.ENGINE_PREFIX + processDef.substring(0, processDef.indexOf(':'));
        String tkey = (String) task.getVariableLocal("taskFormKey");
        String etype = getEtype();

        return getNotificationTemplate(wfkey, tkey, nodeService.getType(getDocsInfo()), etype);
    }

    private String getEtype() {
        NodeRef docsInfo = getDocsInfo();

        return getFirstEtypeFromNodeRefs(Collections.singletonList(docsInfo));
    }

    /**
     * Include initiator of process to recipients
     */
    public void setSendToOwner(Boolean sendToOwner) {
        this.sendToOwner = sendToOwner;
    }

    public void setNodeOwnerDAO(NodeOwnerDAO nodeOwnerDAO) {
        this.nodeOwnerDAO = nodeOwnerDAO;
    }

    protected void sendToAssignee(ExecutionEntity task, Set<String> authorities) {
        // empty
    }

    protected void sendToInitiator(ExecutionEntity task, Set<String> authorities) {
        NodeRef initiator = (NodeRef) task.getVariable("initiator");
        String initiatorName = (String) nodeService.getProperty(initiator, ContentModel.PROP_USERNAME);
        authorities.add(initiatorName);
    }

    protected void sendToOwner(Set<String> authorities, NodeRef node) {
        String owner = nodeOwnerDAO.getOwner(node);
        authorities.add(owner);
    }

    public void setAllowDocList(List<String> allowDocList) {
        this.allowDocList = allowDocList;
    }


    protected void sendToSubscribers(ExecutionEntity task, Set<String> authorities, List<String> taskSubscribers) {
        for (String subscriber : taskSubscribers) {
            if (StringUtils.isBlank(subscriber)) {
                continue;
            }
            QName sub = qNameConverter.mapNameToQName(subscriber);
            NodeRef workflowPackage;
            workflowPackage = (NodeRef) task.getVariable("bpm_package");
            if (workflowPackage != null) {
                List<ChildAssociationRef> children = nodeService.getChildAssocs(workflowPackage);
                for (ChildAssociationRef child : children) {
                    NodeRef node = child.getChildRef();
                    Collection<AssociationRef> assocs = nodeService.getTargetAssocs(node, sub);
                    for (AssociationRef assoc : assocs) {
                        NodeRef ref = assoc.getTargetRef();
                        if (nodeService.exists(ref)) {
                            String subName = (String) nodeService.getProperty(ref, ContentModel.PROP_USERNAME);
                            authorities.add(subName);
                        }
                    }
                }
            }
        }
    }

    public void setTemplateService(TemplateService templateService) {
        this.templateService = templateService;
    }

    public void setTemplateEngine(String templateEngine) {
        this.templateEngine = templateEngine;
    }

    public void setSubjectTemplates(Map<String, Map<String, String>> subjectTemplates) {
        this.subjectTemplates = subjectTemplates;
    }

    public void setNodeVariable(String nodeVariable) {
        this.nodeVariable = nodeVariable;
    }

    private void setDocsInfo(NodeRef docsInfo) {
        AlfrescoTransactionSupport.bindResource(DOCS_INFO_KEY, docsInfo);
    }

    private NodeRef getDocsInfo() {
        return AlfrescoTransactionSupport.getResource(DOCS_INFO_KEY);
    }
}
