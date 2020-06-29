package ru.citeck.ecos.workflow.records;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.workflow.WorkflowDefinition;
import org.alfresco.service.cmr.workflow.WorkflowInstance;
import org.alfresco.service.cmr.workflow.WorkflowInstanceQuery;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
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
import ru.citeck.ecos.workflow.EcosWorkflowService;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WorkflowRecordsDao extends LocalRecordsDao
    implements LocalRecordsQueryWithMetaDao<WorkflowRecordsDao.WorkflowRecord>,
    LocalRecordsMetaDao<MetaValue>,
    MutableRecordsDao {

    private static final String ID = "workflow";
    private static final String DEFINITION_PREFIX = "def_";
    private static final int MIN_RECORDS_SIZE = 0;
    private static final int MAX_RECORDS_SIZE = 10000;
    private final WorkflowRecord EMPTY_RECORD = new WorkflowRecord();

    private final EcosWorkflowService ecosWorkflowService;
    private final NamespaceService namespaceService;
    private final NodeService nodeService;

    @Autowired
    public WorkflowRecordsDao(EcosWorkflowService ecosWorkflowService,
                              NamespaceService namespaceService,
                              NodeService nodeService) {
        setId(ID);
        this.ecosWorkflowService = ecosWorkflowService;
        this.namespaceService = namespaceService;
        this.nodeService = nodeService;
    }

    @Override
    public List<MetaValue> getLocalRecordsMeta(List<RecordRef> list, MetaField metaField) {

        if (list.size() == 1 && list.get(0).getId().isEmpty()) {
            return Collections.singletonList(EMPTY_RECORD);
        }

        return list.stream()
            .map(ref -> {
                if (ref.getId().isEmpty()) {
                    return EMPTY_RECORD;
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

    @Override
    public RecordsQueryResult<WorkflowRecord> queryLocalRecords(RecordsQuery recordsQuery, MetaField metaField) {

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
            .map(this::cancelWorkflowIfRequired)
            .collect(Collectors.toList());

        handledMeta.forEach(this::handleDefWorkflow);

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
        return meta;
    }

    private void handleDefWorkflow(RecordMeta meta) {
        RecordRef recordRef = meta.getId();
        if (recordRef.getId() != null && StringUtils.startsWith(recordRef.getId(), DEFINITION_PREFIX)) {
            WorkflowDefinition definition = ecosWorkflowService.getDefinitionByName(recordRef.getId()
                .replaceFirst(DEFINITION_PREFIX, ""));
            ObjectData attributes = meta.getAttributes();
            log.warn(attributes.toString());
            Map<QName, Object> preparedProps = prepareProps(attributes);
            ecosWorkflowService.startFormWorkflow(definition.getId(), preparedProps);
        }
    }

    private Map<QName, Object> prepareProps(ObjectData metaAttributes) {
        Map<QName, Object> resultProps = new HashMap<>();
        metaAttributes.forEach((n, v) -> {

            String stringName = n;
            if (stringName.contains("_")) {
                stringName = stringName.replaceFirst("_", ":");
            }

            QName name = QName.resolveToQName(namespaceService, stringName);

            if (v.isTextual()) {
                String value = v.asText();
                if (StringUtils.isNotBlank(value)) {
                    resultProps.put(name, value);
                }
            } else if (v.isBoolean()) {
                resultProps.put(name, v.asBoolean());
            } else if (v.isDouble()) {
                resultProps.put(name, v.asDouble());
            } else if (v.isInt()) {
                resultProps.put(name, v.asInt());
            } else if (v.isLong()) {
                resultProps.put(name, v.asLong());
            } else if (v.isNull()) {
                resultProps.put(name, null);
            } else if (v.isArray()) {
                List<NodeRef> nodeRefs = new ArrayList<>();
                for (DataValue jsonNode : v) {
                    String stringNode = jsonNode.asText();
                    if (NodeRef.isNodeRef(stringNode)) {
                        nodeRefs.add(new NodeRef(stringNode));
                    }
                }
                resultProps.put(name, nodeRefs);
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
                    WorkflowContentInfo contentInfo = new WorkflowContentInfo();
                    String url = "alfresco/api/workflow-instances/" + instance.getId() + "/diagram";
                    contentInfo.setUrl(url);
                    contentInfo.setExt("png");
                    contentInfo.setMimetype(MimetypeMap.MIMETYPE_IMAGE_PNG);
                    return contentInfo;
                case "document":
                    NodeRef wfPackageNodeRef = instance.getWorkflowPackage();
                    return nodeService.getProperty(wfPackageNodeRef, CiteckWorkflowModel.PROP_ATTACHED_DOCUMENT);
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
    public class WorkflowDefinitionRecord implements MetaValue {

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
