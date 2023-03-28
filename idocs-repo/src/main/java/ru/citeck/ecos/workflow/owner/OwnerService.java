package ru.citeck.ecos.workflow.owner;

import org.alfresco.service.cmr.workflow.WorkflowTask;

public interface OwnerService {
    WorkflowTask changeOwner(String taskId, OwnerAction action, String owner);
}
