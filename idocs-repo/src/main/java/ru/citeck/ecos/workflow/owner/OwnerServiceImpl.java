package ru.citeck.ecos.workflow.owner;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.workflow.WorkflowService;
import org.alfresco.service.cmr.workflow.WorkflowTask;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.deputy.DeputyService;
import ru.citeck.ecos.deputy.TaskDeputyListener;
import ru.citeck.ecos.workflow.listeners.GrantWorkflowTaskPermissionExecutor;
import ru.citeck.ecos.workflow.mirror.WorkflowMirrorService;

import java.io.Serializable;
import java.util.*;

@Service("ownerService")
public class OwnerServiceImpl implements OwnerService {

    private static final QName CLAIM_OWNER = QName.createQName(null, "claimOwner");

    private final WorkflowService workflowService;
    private final WorkflowMirrorService workflowMirrorService;
    private final DeputyService deputyService;
    private final GrantWorkflowTaskPermissionExecutor grantWorkflowTaskPermissionExecutor;
    private final TaskDeputyListener taskDeputyListener;

    @Autowired
    public OwnerServiceImpl(@Qualifier("WorkflowService") WorkflowService workflowService,
                            WorkflowMirrorService workflowMirrorService,
                            @Qualifier("DeputyService") DeputyService deputyService,
                            GrantWorkflowTaskPermissionExecutor grantWorkflowTaskPermissionExecutor,
                            @Qualifier("deputyListener.taskManagement") TaskDeputyListener taskDeputyListener) {
        this.workflowService = workflowService;
        this.workflowMirrorService = workflowMirrorService;
        this.deputyService = deputyService;
        this.grantWorkflowTaskPermissionExecutor = grantWorkflowTaskPermissionExecutor;
        this.taskDeputyListener = taskDeputyListener;
    }

    public WorkflowTask changeOwner(String taskId, OwnerAction action, String owner) {
        WorkflowTask workflowTask = workflowService.getTaskById(taskId);
        String claimOwner = (String) workflowTask.getProperties().get(CLAIM_OWNER);
        if ("null".equals(claimOwner) || StringUtils.isBlank(claimOwner)) {
            claimOwner = null;
        }


        List<String> assistants = new ArrayList<>();
        if (!(action == OwnerAction.RELEASE && claimOwner == null)) {
            assistants.addAll(deputyService.getUserAssistants(action == OwnerAction.CLAIM ? owner : claimOwner));
        }

        boolean hasAssistants = !assistants.isEmpty();
        if (hasAssistants) {
            assistants.add(action == OwnerAction.CLAIM ? owner : claimOwner);
            taskDeputyListener.updatePooledActors(Collections.singletonList(workflowTask),
                    assistants,
                    action == OwnerAction.CLAIM || action == OwnerAction.RELEASE);
        }

        Map<QName, Serializable> props = setOwners(action, owner, hasAssistants);
        workflowTask = AuthenticationUtil.runAsSystem(() -> workflowService.updateTask(taskId, props, null, null));

        if (hasAssistants) {
            workflowMirrorService.mirrorTask(workflowTask);
            grantWorkflowTaskPermissionExecutor.grantPermissions(workflowTask);
        }

        return workflowTask;
    }


    private Map<QName, Serializable> setOwners(OwnerAction action, String owner, boolean hasAssistants) {
        Serializable setOwner = (action == OwnerAction.CLAIM && !hasAssistants) ? owner : null;
        Serializable setClaimOwner = (action == OwnerAction.CLAIM) ? owner : null;

        Map<QName, Serializable> props = new HashMap<>();
        props.put(ContentModel.PROP_OWNER, setOwner);
        props.put(CLAIM_OWNER, setClaimOwner);

        return props;
    }


}
