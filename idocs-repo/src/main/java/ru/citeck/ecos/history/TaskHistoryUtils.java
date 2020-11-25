package ru.citeck.ecos.history;

import lombok.extern.log4j.Log4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.workflow.WorkflowService;
import org.alfresco.service.cmr.workflow.WorkflowTask;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.config.EcosConfigService;
import ru.citeck.ecos.model.CasePerformModel;
import ru.citeck.ecos.role.CaseRoleAssocsDao;
import ru.citeck.ecos.role.CaseRoleService;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Log4j
@Component
public class TaskHistoryUtils {

    private static final String DEFAULT_SLA_JOURNAL_ITEM_ID = "actual-default-sla-duration";

    private final NodeService nodeService;
    private final CaseRoleService caseRoleService;
    private final AuthorityService authorityService;
    private final WorkflowService workflowService;
    private final EcosConfigService ecosConfigService;
    private final CaseRoleAssocsDao caseRoleAssocsDao;

    @Autowired
    public TaskHistoryUtils(@Qualifier("NodeService") NodeService nodeService, CaseRoleService caseRoleService,
                            @Qualifier("AuthorityService") AuthorityService authorityService,
                            @Qualifier("WorkflowService") WorkflowService workflowService,
                            @Qualifier("ecosConfigService") EcosConfigService ecosConfigService,
                            CaseRoleAssocsDao caseRoleAssocsDao) {
        this.nodeService = nodeService;
        this.caseRoleService = caseRoleService;
        this.authorityService = authorityService;
        this.workflowService = workflowService;
        this.ecosConfigService = ecosConfigService;
        this.caseRoleAssocsDao = caseRoleAssocsDao;
    }

    public String getAuthorizedName(List<String> varNameRoles, List<NodeRef> listRoles, String assignee) {
        for (NodeRef role : listRoles) {
            String varName = caseRoleService.getRoleId(role);
            if (varNameRoles.contains(varName)) {
                for (String varNameRole : varNameRoles) {
                    if (varNameRole.equals(varName)) {
                        Map<NodeRef, NodeRef> delegates = caseRoleService.getDelegates(role);
                        for (Map.Entry<NodeRef, NodeRef> entry : delegates.entrySet()) {
                            NodeRef assigneeNodeRef = authorityService.getAuthorityNodeRef(assignee);
                            if (Objects.equals(assigneeNodeRef, entry.getValue())) {
                                return (String) nodeService.getProperty(entry.getKey(),
                                        ContentModel.PROP_AUTHORITY_DISPLAY_NAME);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public String getRoleName(List<AssociationRef> packageAssocs, String assignee, String taskId, String engineId) {

        String roleName = "";

        if (StringUtils.isNotBlank(taskId)) {
            WorkflowTask task = workflowService.getTaskById(engineId + "$" + taskId);
            if (task != null) {
                Map<QName, Serializable> properties = task.getProperties();
                List<NodeRef> roleRefs = caseRoleAssocsDao.getRolesByAssoc(properties, CasePerformModel.ASSOC_CASE_ROLE);
                if (!roleRefs.isEmpty()) {
                    roleName = caseRoleService.getRoleDispName(roleRefs.get(0));
                }
            }
        }

        if (StringUtils.isBlank(roleName)) {
            if (CollectionUtils.isNotEmpty(packageAssocs)) {

                NodeRef currentTask = packageAssocs.get(0).getSourceRef();
                List<NodeRef> roleRefs = caseRoleAssocsDao.getRolesByAssoc(currentTask,
                    CasePerformModel.ASSOC_PERFORMERS_ROLES);

                if (!roleRefs.isEmpty()) {
                    roleName = caseRoleService.getRoleDispName(roleRefs.get(0));
                }
            }
            if (roleName.isEmpty()) {
                roleName = assignee;
            }
        }

        return roleName;
    }

    public Integer getDefaultSLA() {
        String rawSla = (String) ecosConfigService.getParamValue(DEFAULT_SLA_JOURNAL_ITEM_ID);
        try {
            return Integer.valueOf(rawSla);
        } catch (NumberFormatException exception) {
            log.error("Can't transform '" + rawSla + "' to the number", exception);
            return null;
        }
    }
}
