package ru.citeck.ecos.records.source.common;

import lombok.AllArgsConstructor;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.workflow.WorkflowTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.source.common.AttributesMixin;
import ru.citeck.ecos.utils.WorkflowUtils;
import ru.citeck.ecos.workflow.records.WorkflowRecordsDao;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class TasksMixin implements AttributesMixin<Class<RecordRef>, RecordRef> {

    public static final String ATTRIBUTE_NAME = "tasks";

    private final AlfNodesRecordsDAO alfNodesRecordsDAO;
    private final WorkflowRecordsDao workflowRecordsDao;

    private final WorkflowUtils workflowUtils;
    private final NodeService nodeService;

    @Autowired
    public TasksMixin(AlfNodesRecordsDAO alfNodesRecordsDAO,
                      WorkflowRecordsDao workflowRecordsDao,
                      ServiceRegistry serviceRegistry,
                      WorkflowUtils workflowUtils) {

        this.workflowUtils = workflowUtils;
        this.alfNodesRecordsDAO = alfNodesRecordsDAO;
        this.workflowRecordsDao = workflowRecordsDao;
        this.nodeService = serviceRegistry.getNodeService();
    }

    @PostConstruct
    public void setup() {
        alfNodesRecordsDAO.addAttributesMixin(this);
        workflowRecordsDao.addAttributesMixin(this);
    }

    @Override
    public List<String> getAttributesList() {
        return Collections.singletonList(ATTRIBUTE_NAME);
    }

    @Override
    public Object getAttribute(String s, RecordRef recordRef, MetaField metaField) {
        return new TasksValue(recordRef);
    }

    @Override
    public Class<RecordRef> getMetaToRequest() {
        return RecordRef.class;
    }

    @AllArgsConstructor
    private class TasksValue implements MetaValue {

        private static final String ACTIVE_HASH = "active-hash";
        private final RecordRef recordRef;

        private List<WorkflowTask> getActiveTasks() {

            List<WorkflowTask> tasks = null;

            String id = recordRef.getId();

            if (id.startsWith("workspace")) {

                if (!NodeRef.isNodeRef(id)) {
                    return Collections.emptyList();
                }
                NodeRef nodeRef = new NodeRef(id);
                if (!nodeService.exists(nodeRef)) {
                    return Collections.emptyList();
                }

                tasks = workflowUtils.getDocumentTasks(nodeRef, true);

            } else if (id.contains("$")) {

                tasks = workflowUtils.getWorkflowTasks(id, true);
            }

            if (tasks == null) {
                tasks = Collections.emptyList();
            }
            return tasks;
        }

        @Override
        public Object getAttribute(String name, MetaField field) {

            if (ACTIVE_HASH.equals(name)) {
                List<WorkflowTask> tasks = getActiveTasks();
                return tasks.stream()
                    .map(t -> Objects.hash(t.getId(), t.getProperties()))
                    .collect(Collectors.toList())
                    .hashCode();
            }

            return null;
        }
    }
}
