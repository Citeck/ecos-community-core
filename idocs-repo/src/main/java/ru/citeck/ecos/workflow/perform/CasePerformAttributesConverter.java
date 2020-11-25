package ru.citeck.ecos.workflow.perform;

import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.behavior.activity.CaseTaskAttributesConverter;
import ru.citeck.ecos.icase.activity.dto.ActivityInstance;
import ru.citeck.ecos.icase.activity.service.eproc.EProcUtils;
import ru.citeck.ecos.icase.activity.service.eproc.importer.parser.CmmnDefinitionConstants;
import ru.citeck.ecos.model.CasePerformModel;
import ru.citeck.ecos.model.RouteModel;
import ru.citeck.ecos.role.CaseRoleAssocsDao;
import ru.citeck.ecos.role.CaseRoleService;
import ru.citeck.ecos.utils.RepoUtils;
import ru.citeck.ecos.workflow.confirm.PrecedenceToJsonListener;
import ru.citeck.ecos.workflow.variable.type.TaskConfig;
import ru.citeck.ecos.workflow.variable.type.TaskConfigs;
import ru.citeck.ecos.workflow.variable.type.TaskStages;

import java.io.Serializable;
import java.util.*;

@Component
public class CasePerformAttributesConverter implements CaseTaskAttributesConverter {

    private DictionaryService dictionaryService;
    private CaseRoleService caseRoleService;
    private CaseRoleAssocsDao caseRoleAssocsDao;
    private NodeService nodeService;

    private CasePerformUtils utils;

    @Override
    public Map<QName, Serializable> convert(Map<QName, Serializable> properties, NodeRef taskRef) {
        List<NodeRef> performersRoles =
            caseRoleAssocsDao.getRolesByAssoc(taskRef, CasePerformModel.ASSOC_PERFORMERS_ROLES);
        return convertImpl(properties, performersRoles);
    }

    @Override
    public Map<QName, Serializable> convert(Map<QName, Serializable> properties,
                                            NodeRef caseRef, ActivityInstance instance) {

        String[] taskRoleVarNames = EProcUtils.getAnyAttribute(instance,
                CmmnDefinitionConstants.TASK_ROLE_VAR_NAMES_SET_KEY, String[].class);
        List<NodeRef> performersRoles = new ArrayList<>(taskRoleVarNames.length);
        for (String taskRoleVarName : taskRoleVarNames) {
            performersRoles.add(caseRoleService.getRole(caseRef, taskRoleVarName));
        }
        return convertImpl(properties, performersRoles);
    }

    private Map<QName, Serializable> convertImpl(Map<QName, Serializable> properties, List<NodeRef> performersRoles) {

        Map<QName, Serializable> result = new HashMap<>(properties);

        TaskStages stages = new TaskStages();
        TaskConfigs defaultConfigs = new TaskConfigs();

        Date defaultDueDate = (Date) properties.get(WorkflowModel.PROP_WORKFLOW_DUE_DATE);
        String formKey = (String) properties.get(CasePerformModel.PROP_FORM_KEY);

        for (NodeRef roleRef : performersRoles) {

            caseRoleService.updateRole(roleRef);

            if (caseRoleService.isAlfRole(roleRef)
                    && nodeService.hasAspect(roleRef, RouteModel.ASPECT_HAS_PRECEDENCE)) {

                String precedence = (String) nodeService.getProperty(roleRef, RouteModel.PROP_PRECEDENCE);

                if (StringUtils.isEmpty(precedence)) {
                    continue;
                }

                List<PrecedenceToJsonListener.Stage> precStages = PrecedenceToJsonListener.getStages(precedence);

                for (PrecedenceToJsonListener.Stage stage : precStages) {

                    TaskConfigs configs = new TaskConfigs();

                    Date stageDueDate;
                    if (stage.hours != null) {
                        Calendar calendar = Calendar.getInstance();
                        calendar.setTime(new Date());
                        calendar.add(Calendar.MINUTE, (int) (stage.hours * 60));
                        stageDueDate = calendar.getTime();
                    } else {
                        stageDueDate = defaultDueDate;
                    }

                    for (NodeRef participant : stage.participants) {
                        configs.add(createConfig(participant, stageDueDate, formKey));
                    }

                    stages.add(configs);
                }
            } else {
                Set<NodeRef> assignees = caseRoleService.getAssignees(roleRef);
                for (NodeRef assignee : assignees) {
                    defaultConfigs.add(createConfig(assignee, defaultDueDate, formKey));
                }
            }
        }

        if (!defaultConfigs.isEmpty()) {
            stages.add(defaultConfigs);
        }

        String stagesStr;
        try {
            stagesStr = utils.getObjectMapper().writeValueAsString(stages);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        result.put(QName.createQName(CasePerformUtils.PERFORM_STAGES), stagesStr);
        if (stages.size() > 1) {
            //not supported yet
            result.put(CasePerformModel.PROP_SYNC_ROLES_TO_WORKFLOW, false);
        }

        return result;
    }

    private TaskConfig createConfig(NodeRef assignee, Date dueDate, String formKey) {

        TaskConfig taskConfig = new TaskConfig();

        String authName = RepoUtils.getAuthorityName(assignee, nodeService, dictionaryService);
        taskConfig.setPerformer(authName);
        taskConfig.setDueDate(dueDate);
        taskConfig.setFormKey(formKey);

        return taskConfig;
    }

    @Override
    public Set<String> getWorkflowTypes() {
        return new HashSet<>(Arrays.asList("activiti$case-perform",
                "flowable$case-perform"));
    }

    @Autowired
    public void setCasePerformUtils(CasePerformUtils utils) {
        this.utils = utils;
    }

    @Autowired
    public void setDictionaryService(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    @Autowired
    public void setCaseRoleService(CaseRoleService caseRoleService) {
        this.caseRoleService = caseRoleService;
    }

    @Autowired
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @Autowired
    public void setCaseRoleAssocsDao(CaseRoleAssocsDao caseRoleAssocsDao) {
        this.caseRoleAssocsDao = caseRoleAssocsDao;
    }
}
