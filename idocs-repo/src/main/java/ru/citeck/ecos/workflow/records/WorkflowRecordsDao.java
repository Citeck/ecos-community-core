package ru.citeck.ecos.workflow.records;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.workflow.WorkflowDefinition;
import org.alfresco.service.cmr.workflow.WorkflowInstance;
import org.alfresco.service.cmr.workflow.WorkflowInstanceQuery;
import org.alfresco.service.cmr.workflow.WorkflowTask;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.CiteckWorkflowModel;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.EmptyValue;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.mutation.RecordsMutation;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.MutableRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;
import ru.citeck.ecos.utils.WorkflowUtils;
import ru.citeck.ecos.workflow.EcosWorkflowService;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WorkflowRecordsDao extends LocalRecordsDao
    implements LocalRecordsQueryWithMetaDao<WorkflowRecordsDao.WorkflowRecord>,
    LocalRecordsMetaDao<MetaValue>,
    MutableRecordsDao {

    public static final String ID = "workflow";

    private static final String DEFINITION_PREFIX = "def_";
    private static final int MIN_RECORDS_SIZE = 0;
    private static final int MAX_RECORDS_SIZE = 10000;

    private final EcosWorkflowService ecosWorkflowService;
    private final NamespaceService namespaceService;
    private final DictionaryService dictionaryService;
    private final WorkflowUtils workflowUtils;
    private final NodeService nodeService;

    @Autowired
    public WorkflowRecordsDao(EcosWorkflowService ecosWorkflowService,
                              NamespaceService namespaceService,
                              DictionaryService dictionaryService,
                              WorkflowUtils workflowUtils,
                              NodeService nodeService) {
        setId(ID);
        this.ecosWorkflowService = ecosWorkflowService;
        this.namespaceService = namespaceService;
        this.dictionaryService = dictionaryService;
        this.nodeService = nodeService;
        this.workflowUtils = workflowUtils;
    }

    @NotNull
    @Override
    public List<MetaValue> getLocalRecordsMeta(List<RecordRef> list, @NotNull MetaField metaField) {

        if (list.size() == 1 && list.get(0).getId().isEmpty()) {
            return Collections.singletonList(EmptyValue.INSTANCE);
        }

        return list.stream()
            .map(ref -> {
                if (ref.getId().isEmpty()) {
                    return EmptyValue.INSTANCE;
                }
                if (StringUtils.startsWith(ref.getId(), DEFINITION_PREFIX)) {
                    WorkflowDefinition definition = ecosWorkflowService.getDefinitionByName(ref.getId()
                        .replaceFirst(DEFINITION_PREFIX, ""));
                    if (definition != null) {
                        return new WorkflowDefinitionRecord(ecosWorkflowService.getDefinitionByName(ref.getId()
                            .replaceFirst(DEFINITION_PREFIX, "")), ref.getId());
                    } else {
                        return EmptyValue.INSTANCE;
                    }
                }
                WorkflowInstance instance = ecosWorkflowService.getInstanceById(ref.getId());
                if (instance != null) {
                    return new WorkflowRecord(ecosWorkflowService.getInstanceById(ref.getId()));
                } else {
                    return EmptyValue.INSTANCE;
                }
            })
            .collect(Collectors.toList());
    }

    @NotNull
    @Override
    public RecordsQueryResult<WorkflowRecord> queryLocalRecords(@NotNull RecordsQuery recordsQuery,
                                                                @NotNull MetaField metaField) {

        RecordsQueryResult<WorkflowRecord> result = new RecordsQueryResult<>();

        WorkflowRecordsDao.WorkflowQuery queryData = recordsQuery.getQuery(WorkflowRecordsDao.WorkflowQuery.class);

        WorkflowInstanceQuery query = new WorkflowInstanceQuery();
        if (queryData != null && queryData.active != null) {
            query.setActive(queryData.active);
        }

        int max = recordsQuery.getMaxItems();
        if (max <= MIN_RECORDS_SIZE) {
            max = MAX_RECORDS_SIZE;
        }

        int skipCount = recordsQuery.getSkipCount();

        List<WorkflowInstance> workflowInstances = ecosWorkflowService.getAllInstances(query, max, skipCount);

        List<WorkflowRecord> workflowRecords = workflowInstances.stream()
            .map(WorkflowRecord::new)
            .collect(Collectors.toList());

        result.setRecords(workflowRecords);
        return result;
    }

    @Override
    public RecordsMutResult mutateImpl(RecordsMutation mutation) {

        RecordsMutResult result = new RecordsMutResult();

        List<RecordMeta> handledMeta = mutation.getRecords().stream()
            .map(meta -> {
                if (StringUtils.startsWith(meta.getId().getId(), DEFINITION_PREFIX)) {
                    return handleDefWorkflow(meta);
                } else {
                    return cancelWorkflowIfRequired(meta);
                }
            })
            .collect(Collectors.toList());

        result.setRecords(handledMeta);
        return result;
    }

    private RecordMeta cancelWorkflowIfRequired(RecordMeta meta) {
        if (meta.hasAttribute("cancel")) {
            boolean cancel = meta.getAttribute("cancel").asBoolean();
            if (cancel) {
                WorkflowInstance mutatedInstance = ecosWorkflowService.cancelWorkflowInstance(meta.getId().getId());
                meta.setId(mutatedInstance.getId());
            }
        }
        if (meta.hasAttribute("cancel-root")) {
            boolean cancel = meta.getAttribute("cancel-root").asBoolean();
            if (cancel) {
                WorkflowInstance mutatedInstance = ecosWorkflowService.cancelWorkflowRootInstance(meta.getId().getId());
                meta.setId(mutatedInstance.getId());
            }
        }
        return meta;
    }

    private RecordMeta handleDefWorkflow(RecordMeta meta) {
        WorkflowDefinition definition = ecosWorkflowService.getDefinitionByName(meta.getId().getId()
            .replaceFirst(DEFINITION_PREFIX, ""));
        String id = ecosWorkflowService.startFormWorkflow(definition.getId(), prepareProps(meta.getAttributes()));
        return StringUtils.isNotBlank(id) ? new RecordMeta(ID + "@" + id) : new RecordMeta(meta.getId());
    }

    private Map<String, Object> prepareProps(ObjectData metaAttributes) {
        Map<String, Object> resultProps = new HashMap<>();
        metaAttributes.forEachJ((n, v) -> {

            Object value = v.asJavaObj();

            if (value instanceof String) {
                if (StringUtils.isNotBlank((String) value)) {
                    resultProps.put(n, value);
                }
            } else if (value instanceof List) {
                String stringName = n;
                if (stringName.contains("_")) {
                    stringName = stringName.replaceFirst("_", ":");
                }
                QName name = QName.resolveToQName(namespaceService, stringName);
                if (dictionaryService.getAssociation(name) != null) {
                    List<NodeRef> nodeRefs = new ArrayList<>();
                    for (Object jsonNode : (List) value) {
                        if (jsonNode instanceof String && NodeRef.isNodeRef((String) jsonNode)) {
                            nodeRefs.add(new NodeRef((String) jsonNode));
                        }
                    }
                    resultProps.put(n, nodeRefs);
                } else {
                    resultProps.put(n, value);
                }
            } else {
                resultProps.put(n, value);
            }
        });

        return resultProps;
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion recordsDeletion) {
        throw new UnsupportedOperationException("Deleting of workflow processes is not supporting!");
    }

    @AllArgsConstructor
    @NoArgsConstructor
    public class WorkflowRecord implements MetaValue {

        private WorkflowInstance instance;

        @Override
        public String getId() {
            return instance.getId();
        }

        @Override
        public Object getAttribute(String name, MetaField field) {
            switch (name) {
                case "previewInfo":
                    if (!instance.isActive()) {
                        return null;
                    }
                    WorkflowContentInfo contentInfo = new WorkflowContentInfo();
                    String url = "alfresco/api/workflow-instances/" + instance.getId() + "/diagram";
                    contentInfo.setUrl(url);
                    contentInfo.setExt("png");
                    contentInfo.setMimetype(MimetypeMap.MIMETYPE_IMAGE_PNG);
                    return contentInfo;
                case "document":
                    NodeRef wfPackageNodeRef = instance.getWorkflowPackage();
                    return nodeService.getProperty(wfPackageNodeRef, CiteckWorkflowModel.PROP_ATTACHED_DOCUMENT);
                case "preview-hash":
                    List<WorkflowTask> tasks = workflowUtils.getWorkflowTasks(instance.getId(), true);
                    return Objects.hash(
                        instance.isActive(),
                        tasks.stream()
                            .map(WorkflowTask::getId)
                            .collect(Collectors.toList())
                    );
            }
            return null;
        }

        @Override
        public String getDisplayName() {
            String dispName = instance.getDescription();
            if (StringUtils.isBlank(dispName) && instance.getDefinition() != null) {
                dispName = instance.getDefinition().getTitle();
            }
            return dispName;
        }

        @Override
        public RecordRef getRecordType() {
            return RecordRef.create("emodel", "type", "workflow");
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    public static class WorkflowDefinitionRecord implements MetaValue {

        private static final String WORKFLOW_PREFIX = "workflow_";

        private WorkflowDefinition definition;
        private String definitionId;

        @Override
        public String getId() {
            return definitionId;
        }

        @Override
        public Object getAttribute(String name, MetaField field) {
            switch (name) {
                case RecordConstants.ATT_FORM_KEY:
                    return WORKFLOW_PREFIX + definition.getName();
            }
            return null;
        }

        @Override
        public String getDisplayName() {
            String dispName = definition.getDescription();
            if (StringUtils.isBlank(dispName)) {
                dispName = definition.getTitle();
            }
            return dispName;
        }
    }

    @Data
    @NoArgsConstructor
    public static class WorkflowContentInfo {
        private String url;
        private String originalUrl;
        private String originalName;
        private String originalExt;
        private String ext;
        private String mimetype;
    }

    @Data
    public static class WorkflowQuery {
        private Boolean active;
    }
}
