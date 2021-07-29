package ru.citeck.ecos.icase.activity.service.eproc.listeners.activity;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.jscript.ValueConverter;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.repo.workflow.WorkflowQNameConverter;
import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.workflow.WorkflowDefinition;
import org.alfresco.service.cmr.workflow.WorkflowInstance;
import org.alfresco.service.cmr.workflow.WorkflowPath;
import org.alfresco.service.cmr.workflow.WorkflowService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.action.ActionConditionUtils;
import ru.citeck.ecos.behavior.activity.CaseTaskAttributesConverter;
import ru.citeck.ecos.config.EcosConfigService;
import ru.citeck.ecos.icase.activity.dto.ActivityDefinition;
import ru.citeck.ecos.icase.activity.dto.ActivityInstance;
import ru.citeck.ecos.icase.activity.dto.ActivityRef;
import ru.citeck.ecos.icase.activity.service.eproc.EProcActivityService;
import ru.citeck.ecos.icase.activity.service.eproc.EProcCaseActivityListenerManager;
import ru.citeck.ecos.icase.activity.service.eproc.EProcUtils;
import ru.citeck.ecos.icase.activity.service.eproc.importer.parser.CmmnDefinitionConstants;
import ru.citeck.ecos.icase.activity.service.eproc.importer.parser.CmmnInstanceConstants;
import ru.citeck.ecos.icase.activity.service.eproc.listeners.BeforeStartedActivityListener;
import ru.citeck.ecos.icase.activity.service.eproc.listeners.OnResetActivityListener;
import ru.citeck.ecos.model.CiteckWorkflowModel;
import ru.citeck.ecos.model.EcosProcessModel;
import ru.citeck.ecos.model.ICaseRoleModel;
import ru.citeck.ecos.records.RecordsUtils;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.role.CaseRoleService;
import ru.citeck.ecos.services.duedate.DueDateService;
import ru.citeck.ecos.workflow.variable.type.NodeRefsList;
import ru.citeck.ecos.workflow.variable.type.StringsList;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@Component
public class CaseTaskListener implements BeforeStartedActivityListener, OnResetActivityListener {

    private static final String DEFAULT_SLA_JOURNAL_ITEM_ID = "actual-default-sla-duration";
    private static final String DEFAULT_RAW_SLA = "0";

    private final ValueConverter valueConverter = new ValueConverter();

    private EProcCaseActivityListenerManager manager;
    private EProcActivityService eprocActivityService;
    private DictionaryService dictionaryService;
    private NamespaceService namespaceService;
    private WorkflowService workflowService;
    private CaseRoleService caseRoleService;
    private NodeService nodeService;
    private WorkflowQNameConverter qnameConverter;
    private EcosConfigService ecosConfigService;
    private DueDateService dueDateService;

    //inject as map beans
    private Map<String, Map<String, String>> attributesMappingByWorkflow;
    private Map<String, List<String>> workflowTransmittedVariables;
    private Map<String, CaseTaskAttributesConverter> attributesConverters = new HashMap<>();

    @Autowired
    public CaseTaskListener(EProcCaseActivityListenerManager manager,
                            EProcActivityService eprocActivityService,
                            @Qualifier("DictionaryService") DictionaryService dictionaryService,
                            NamespaceService namespaceService,
                            @Qualifier("WorkflowService") WorkflowService workflowService,
                            CaseRoleService caseRoleService,
                            NodeService nodeService,
                            @Qualifier("ecosConfigService") EcosConfigService ecosConfigService,
                            @Value("#{CaseTaskAttributesMappingByWorkflow}")
                                    Map<String, Map<String, String>> attributesMappingByWorkflow,
                            @Value("#{CaseTaskWorkflowTransmittedVariables}")
                                    Map<String, List<String>> workflowTransmittedVariables,
                            List<CaseTaskAttributesConverter> attributesConverters) {

        this.manager = manager;
        this.eprocActivityService = eprocActivityService;
        this.dictionaryService = dictionaryService;
        this.namespaceService = namespaceService;
        this.workflowService = workflowService;
        this.caseRoleService = caseRoleService;
        this.nodeService = nodeService;
        this.ecosConfigService = ecosConfigService;

        this.attributesMappingByWorkflow = attributesMappingByWorkflow;
        this.workflowTransmittedVariables = workflowTransmittedVariables;
        attributesConverters.forEach(conveter -> {
            for (String id : conveter.getWorkflowTypes()) {
                this.attributesConverters.put(id, conveter);
            }
        });

        this.qnameConverter = new WorkflowQNameConverter(namespaceService);
    }

    @PostConstruct
    public void init() {
        this.manager.subscribeBeforeStarted(this);
        this.manager.subscribeOnReset(this);
    }

    @Override
    public void beforeStartedActivity(ActivityRef activityRef) {
        ActivityDefinition definition = eprocActivityService.getActivityDefinition(activityRef);
        if (!EProcUtils.isUserTask(definition)) {
            return;
        }

        ActivityInstance instance = eprocActivityService.getStateInstance(activityRef);

        NodeRef caseRef = RecordsUtils.toNodeRef(activityRef.getProcessId());

        String workflowDefinitionName = EProcUtils.getAnyAttribute(instance,
                CmmnDefinitionConstants.WORKFLOW_DEFINITION_NAME);

        NodeRef wfPackage = workflowService.createPackage(null);
        nodeService.setProperty(wfPackage, EcosProcessModel.PROP_ACTIVITY_REF, activityRef.toString());

        Map<QName, Serializable> workflowProperties = getWorkflowProperties(caseRef, instance, workflowDefinitionName);
        workflowProperties.put(WorkflowModel.ASSOC_PACKAGE, wfPackage);

        String caseName = (String) this.nodeService.getProperty(caseRef, ContentModel.PROP_NAME);
        QName childQName = QName.createQName(
                NamespaceService.CONTENT_MODEL_1_0_URI,
                QName.createValidLocalName(caseName));
        this.nodeService.addChild(wfPackage, caseRef, WorkflowModel.ASSOC_PACKAGE_CONTAINS, childQName);

        WorkflowDefinition wfDefinition = workflowService.getDefinitionByName(workflowDefinitionName);
        WorkflowPath wfPath = workflowService.startWorkflow(wfDefinition.getId(), workflowProperties);

        EProcUtils.setAttribute(instance, CmmnInstanceConstants.BPM_PACKAGE_REF, wfPackage.toString());
        EProcUtils.setAttribute(instance, CmmnInstanceConstants.WORKFLOW_INSTANCE_ID, wfPath.getInstance().getId());
    }

    private Map<QName, Serializable> getWorkflowProperties(NodeRef caseRef, ActivityInstance instance, String workflowDefinitionName) {
        Map<QName, Serializable> workflowProperties = new HashMap<>();

        setWorkflowPropertiesFromITask(caseRef, workflowProperties, instance);

        Map<String, String> attributesMapping = attributesMappingByWorkflow.get(workflowDefinitionName);

        if (attributesMapping != null) {
            for (Map.Entry<String, String> entry : attributesMapping.entrySet()) {
                QName key = QName.createQName(entry.getKey(), namespaceService);
                QName value = QName.createQName(entry.getValue(), namespaceService);
                workflowProperties.put(value, getAttribute(caseRef, instance, key, value));
            }
        }

        workflowProperties.putAll(getTransmittedVariables(workflowDefinitionName));
        workflowProperties = convertCollections(workflowProperties);

        CaseTaskAttributesConverter converter = attributesConverters.get(workflowDefinitionName);
        if (converter != null) {
            workflowProperties = converter.convert(workflowProperties, caseRef, instance);
        }

        return workflowProperties;
    }

    private void setWorkflowPropertiesFromITask(NodeRef caseRef,
                                                Map<QName, Serializable> workflowProperties,
                                                ActivityInstance instance) {
        String taskTitle = EProcUtils.getAnyAttribute(instance, CmmnDefinitionConstants.TITLE);
        workflowProperties.put(WorkflowModel.PROP_WORKFLOW_DESCRIPTION, taskTitle);

        Boolean useActivityTitle = EProcUtils.getAnyAttribute(instance,
                CmmnDefinitionConstants.USE_ACTIVITY_TITLE, Boolean.class);
        if (!Boolean.FALSE.equals(useActivityTitle)) {
            workflowProperties.put(CiteckWorkflowModel.PROP_TASK_TITLE, taskTitle);
        }

        Date workflowDueDate = getWorkflowDueDate(caseRef, instance);
        if (workflowDueDate != null) {
            EProcUtils.setAttribute(instance, CmmnInstanceConstants.PLANNED_END_DATE, workflowDueDate);
        }
        workflowProperties.put(WorkflowModel.PROP_WORKFLOW_DUE_DATE, workflowDueDate);

        Integer workflowPriority = EProcUtils.getAnyAttribute(instance, CmmnDefinitionConstants.PRIORITY, Integer.class);
        workflowProperties.put(WorkflowModel.PROP_WORKFLOW_PRIORITY, workflowPriority);
    }

    private Date getWorkflowDueDate(NodeRef caseRef, ActivityInstance instance) {
        if (dueDateService == null) {
            return getDefaultWorkflowDueDate(instance);
        }

        Integer expectedPerformTime = EProcUtils.getAnyAttribute(instance,
            CmmnDefinitionConstants.EXPECTED_PERFORM_TIME, Integer.class);
        if (expectedPerformTime == null || expectedPerformTime < 0) {
            expectedPerformTime = 0;
        }

        int days = hoursToDays(expectedPerformTime);
        String dueDateStr = dueDateService.getDueDateForDocument(RecordRef.valueOf(caseRef.toString()), days);
        if (dueDateStr == null) {
            return getDefaultWorkflowDueDate(instance);
        }

        if (expectedPerformTime == 0) {
            return null;
        }

        return Date.from(ZonedDateTime.parse(dueDateStr).toInstant());
    }

    private Date getDefaultWorkflowDueDate(ActivityInstance instance) {
        Date workflowDueDate = null;

        Date startDate = instance.getActivated();
        if (startDate != null) {
            Integer expectedPerformTime = EProcUtils.getAnyAttribute(instance,
                    CmmnDefinitionConstants.EXPECTED_PERFORM_TIME, Integer.class);

            if (expectedPerformTime == null) {
                expectedPerformTime = getDefaultSLA();
            }

            if (expectedPerformTime > 0) {
                int days = hoursToDays(expectedPerformTime);
                workflowDueDate = addDays(startDate, days);
            }
        }

        return workflowDueDate;
    }

    private int getDefaultSLA() {
        String rawSla = (String) ecosConfigService.getParamValue(DEFAULT_SLA_JOURNAL_ITEM_ID);
        if (rawSla == null) {
            rawSla = DEFAULT_RAW_SLA;
        }
        try {
            return Integer.valueOf(rawSla);
        } catch (NumberFormatException exception) {
            throw new AlfrescoRuntimeException("Can't transform '" + rawSla + "' to the number", exception);
        }
    }

    private Date addDays(Date baseDate, int daysToAdd) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(baseDate);
        calendar.add(Calendar.DAY_OF_YEAR, daysToAdd);
        return calendar.getTime();
    }

    private int hoursToDays(int hoursToAdd) {
        return Math.round(hoursToAdd / 8f);
    }

    private Serializable getAttribute(NodeRef caseRef, ActivityInstance instance, QName source, QName target) {
        PropertyDefinition propertyDef = dictionaryService.getProperty(source);
        if (propertyDef != null) {
            Class<?> clazz = getClassByName(propertyDef.getDataType().getJavaClassName());
            return (Serializable) EProcUtils.getAnyAttribute(instance, source.getLocalName(), clazz);
        }

        AssociationDefinition associationDef = dictionaryService.getAssociation(source);
        if (associationDef != null) {
            AssociationDefinition targetAssoc = dictionaryService.getAssociation(target);
            if (targetAssoc == null) {
                throw new AlfrescoRuntimeException("Error occurred during workflow attribute getting. " +
                        "Make sure that QName \"" + target + "\" exists.");
            }

            QName associationTargetQName = associationDef.getTargetClass().getName();
            if (dictionaryService.isSubClass(associationTargetQName, ICaseRoleModel.TYPE_ROLE)) {
                ArrayList<NodeRef> assocs = getRoleAssociations(caseRef, instance, source);
                return targetAssoc.isTargetMany() ? assocs : (!assocs.isEmpty() ? assocs.get(0) : null);
            } else {
                throw new AlfrescoRuntimeException(source + " is not a role association (role assocs only allowed)");
            }
        }
        throw new AlfrescoRuntimeException(source + " is not a property or association (child associations is not allowed)");
    }

    private Class<?> getClassByName(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Can not find class by name='" + className + "'");
        }
    }

    private ArrayList<NodeRef> getRoleAssociations(NodeRef caseRef, ActivityInstance instance, QName source) {
        String key = source.getLocalName();
        String[] roleVarNames = EProcUtils.getAnyAttribute(instance, key, String[].class);
        if (ArrayUtils.isEmpty(roleVarNames)) {
            roleVarNames = EProcUtils.getAnyAttribute(instance,
                    CmmnDefinitionConstants.TASK_ROLE_VAR_NAMES_SET_KEY, String[].class);
        }
        if (ArrayUtils.isEmpty(roleVarNames)) {
            throw new AlfrescoRuntimeException(source + " for assoc not found any roles in definition");
        }

        ArrayList<NodeRef> roles = new ArrayList<>(roleVarNames.length);
        for (String roleVarName : roleVarNames) {
            NodeRef roleRef = caseRoleService.getRole(caseRef, roleVarName);
            caseRoleService.updateRole(roleRef);
            roles.addAll(caseRoleService.getAssignees(roleRef));
        }
        return roles;
    }

    private Map<QName, Serializable> getTransmittedVariables(String workflowDefinitionName) {

        Map<QName, Serializable> result = new HashMap<>();

        List<String> transmittedParameters = workflowTransmittedVariables.get(workflowDefinitionName);

        if (transmittedParameters != null && !transmittedParameters.isEmpty()) {

            Map<String, Object> processVariables = ActionConditionUtils.getProcessVariables();

            for (String parameter : transmittedParameters) {

                QName parameterQName;
                if (parameter.indexOf(':') > 0 || parameter.startsWith("{")) {
                    parameterQName = QName.resolveToQName(namespaceService, parameter);
                } else {
                    parameterQName = QName.createQName(parameter);
                }

                String procParamName = qnameConverter.mapQNameToName(parameterQName);
                Serializable value = convertForRepo(processVariables.get(procParamName));

                if (value != null) {
                    result.put(parameterQName, value);
                }
            }
        }

        return result;
    }

    private Map<QName, Serializable> convertCollections(Map<QName, Serializable> attributes) {
        Map<QName, Serializable> result = new HashMap<>();

        attributes.forEach((k, v) -> {
            if (v instanceof List && !((List) v).isEmpty()) {
                Object value = ((List) v).get(0);
                if (value instanceof NodeRef) {
                    result.put(k, new NodeRefsList((List) v));
                } else if (value instanceof String) {
                    result.put(k, new StringsList((List) v));
                } else {
                    result.put(k, v);
                }
            } else {
                result.put(k, v);
            }
        });
        return result;
    }

    private Serializable convertForRepo(Object value) {
        return value instanceof Serializable
                ? valueConverter.convertValueForRepo((Serializable) value)
                : null;
    }

    @Override
    public void onResetActivity(ActivityRef activityRef) {
        ActivityDefinition definition = eprocActivityService.getActivityDefinition(activityRef);
        if (!EProcUtils.isUserTask(definition)) {
            return;
        }

        ActivityInstance instance = eprocActivityService.getStateInstance(activityRef);

        String workflowInstanceId = EProcUtils.getAnyAttribute(instance, CmmnInstanceConstants.WORKFLOW_INSTANCE_ID);
        if (isWorkflowActive(workflowInstanceId)) {
            workflowService.cancelWorkflow(workflowInstanceId);
        }
        EProcUtils.setAttribute(instance, CmmnInstanceConstants.WORKFLOW_INSTANCE_ID, null);

        String rawBpmPackageRef = EProcUtils.getAnyAttribute(instance, CmmnInstanceConstants.BPM_PACKAGE_REF);
        if (StringUtils.isNotBlank(rawBpmPackageRef) && NodeRef.isNodeRef(rawBpmPackageRef)) {
            NodeRef bpmPackageRef = new NodeRef(rawBpmPackageRef);
            if (nodeService.exists(bpmPackageRef)) {
                nodeService.removeProperty(bpmPackageRef, EcosProcessModel.PROP_ACTIVITY_REF);
            }
        }
        EProcUtils.setAttribute(instance, CmmnInstanceConstants.BPM_PACKAGE_REF, null);
    }

    private boolean isWorkflowActive(String id) {
        if (StringUtils.isBlank(id)) {
            return false;
        }
        WorkflowInstance wf = workflowService.getWorkflowById(id);
        if (wf == null || !wf.isActive()) {
            return false;
        }
        NodeRef bpmPackage = wf.getWorkflowPackage();
        if (bpmPackage == null || !nodeService.exists(bpmPackage)) {
            return true;
        }
        Boolean isActive = (Boolean) nodeService.getProperty(bpmPackage, CiteckWorkflowModel.PROP_IS_WORKFLOW_ACTIVE);
        if (isActive == null) {
            isActive = true;
        }
        return isActive;
    }

    @Autowired(required = false)
    public void setDueDateService(DueDateService dueDateService) {
        this.dueDateService = dueDateService;
    }

    public void registerAttributesMapping(Map<String, Map<String, String>> attributesMappingByWorkflow) {
        this.attributesMappingByWorkflow.putAll(attributesMappingByWorkflow);
    }

    public void registerWorkflowTransmittedVariables(Map<String, List<String>> workflowTransmittedVariables) {
        this.workflowTransmittedVariables.putAll(workflowTransmittedVariables);
    }
}
