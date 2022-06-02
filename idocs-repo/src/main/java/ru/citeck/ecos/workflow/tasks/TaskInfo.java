package ru.citeck.ecos.workflow.tasks;

import org.alfresco.service.cmr.repository.MLText;
import org.alfresco.service.cmr.workflow.WorkflowInstance;
import org.jetbrains.annotations.NotNull;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TaskInfo {

    String getId();

    String getTitle();

    MLText getMlTitle();

    String getDescription();

    String getAssignee();

    String getCandidate();

    List<String> getActors();

    String getFormKey();

    Map<String, Object> getAttributes();

    Map<String, Object> getLocalAttributes();

    @NotNull
    RecordRef getDocument();

    Object getAttribute(String name);

    WorkflowInstance getWorkflow();

    default Set<String> getCandidateRoles() {
        return Collections.emptySet();
    }
}
