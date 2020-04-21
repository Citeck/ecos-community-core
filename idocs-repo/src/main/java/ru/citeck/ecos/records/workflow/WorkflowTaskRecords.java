package ru.citeck.ecos.records.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.dictionary.*;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.cmr.workflow.WorkflowTask;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.document.sum.DocSumService;
import ru.citeck.ecos.predicate.model.ComposedPredicate;
import ru.citeck.ecos.records.RecordConstants;
import ru.citeck.ecos.records.models.AuthorityDTO;
import ru.citeck.ecos.records.models.UserDTO;
import ru.citeck.ecos.records2.QueryContext;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
import ru.citeck.ecos.records2.graphql.meta.value.InnerMetaValue;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.mutation.RecordsMutation;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.MutableRecordsDAO;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDAO;
import ru.citeck.ecos.records2.source.dao.local.RecordsMetaLocalDAO;
import ru.citeck.ecos.records2.source.dao.local.RecordsQueryLocalDAO;
import ru.citeck.ecos.utils.AuthorityUtils;
import ru.citeck.ecos.utils.WorkflowUtils;
import ru.citeck.ecos.workflow.owner.OwnerAction;
import ru.citeck.ecos.workflow.owner.OwnerService;
import ru.citeck.ecos.workflow.tasks.EcosTaskService;
import ru.citeck.ecos.workflow.tasks.TaskInfo;

import java.util.*;
import java.util.stream.Collectors;

import static ru.citeck.ecos.records.workflow.WorkflowTaskRecordsConstants.*;

@Component
public class WorkflowTaskRecords extends LocalRecordsDAO
        implements RecordsMetaLocalDAO<MetaValue>,
        MutableRecordsDAO,
        RecordsQueryLocalDAO {

    private static final String DOCUMENT_FIELD_PREFIX = "_ECM_";
    private static final String OUTCOME_PREFIX = "outcome_";

    private static final String ID = "wftask";

    private final EcosTaskService ecosTaskService;
    private final AuthorityService authorityService;
    private final WorkflowTaskRecordsUtils workflowTaskRecordsUtils;
    private final OwnerService ownerService;
    private final DocSumService docSumService;
    private final WorkflowUtils workflowUtils;
    private final AuthorityUtils authorityUtils;
    private final NamespaceService namespaceService;
    private final DictionaryService dictionaryService;

    @Autowired
    public WorkflowTaskRecords(EcosTaskService ecosTaskService,
                               WorkflowTaskRecordsUtils workflowTaskRecordsUtils,
                               AuthorityService authorityService, OwnerService ownerService,
                               DocSumService docSumService,
                               WorkflowUtils workflowUtils, AuthorityUtils authorityUtils,
                               NamespaceService namespaceService,
                               DictionaryService dictionaryService) {
        this.namespaceService = namespaceService;
        this.dictionaryService = dictionaryService;
        setId(ID);
        this.ecosTaskService = ecosTaskService;
        this.workflowTaskRecordsUtils = workflowTaskRecordsUtils;
        this.authorityService = authorityService;
        this.ownerService = ownerService;
        this.docSumService = docSumService;
        this.workflowUtils = workflowUtils;
        this.authorityUtils = authorityUtils;
    }

    @Override
    public RecordsMutResult mutate(RecordsMutation mutation) {

        RecordsMutResult result = new RecordsMutResult();

        result.setRecords(mutation.getRecords()
                .stream()
                .map(meta -> new RecordMeta(meta, RecordRef.valueOf(meta.getId().getId())))
                .map(this::mutate)
                .map(meta -> new RecordMeta(meta, RecordRef.create(getId(), meta.getId())))
                .collect(Collectors.toList()));

        return result;
    }

    private RecordMeta mutate(RecordMeta meta) {

        String taskId = meta.getId().getId();
        Optional<TaskInfo> taskInfoOpt = ecosTaskService.getTaskInfo(taskId);

        if (!taskInfoOpt.isPresent()) {
            throw new IllegalArgumentException("Task not found! id: " + taskId);
        }

        if (isChangeOwnerAction(meta)) {
            processChangeOwnerAction(meta, taskId);
            return new RecordMeta(taskId);
        }

        TaskInfo taskInfo = taskInfoOpt.get();

        RecordMeta documentProps = new RecordMeta();
        Map<String, Object> taskProps = new HashMap<>();

        String[] outcome = new String[1];

        meta.forEach((n, v) -> {
            if (n.startsWith(DOCUMENT_FIELD_PREFIX)) {
                documentProps.set(getEcmFieldName(n), v);
            }
            if (n.startsWith(OUTCOME_PREFIX)) {
                outcome[0] = n.substring(OUTCOME_PREFIX.length());
            } else {

                if (v.isTextual()) {
                    String value = v.asText();
                    if (isDate(n) && StringUtils.isEmpty(value)) {
                        value = null;
                    }
                    taskProps.put(n, value);
                } else if (v.isBoolean()) {
                    taskProps.put(n, v.asBoolean());
                } else if (v.isDouble()) {
                    taskProps.put(n, v.asDouble());
                } else if (v.isInt()) {
                    taskProps.put(n, v.asInt());
                } else if (v.isLong()) {
                    taskProps.put(n, v.asLong());
                } else if (v.isNull()) {
                    taskProps.put(n, null);
                } else if (v.isArray()) {
                    Set<NodeRef> nodeRefs = new HashSet<>();
                    for (JsonNode jsonNode : v) {
                        String stringNode = jsonNode.asText();
                        if (NodeRef.isNodeRef(stringNode)) {
                            nodeRefs.add(new NodeRef(stringNode));
                        }
                    }
                    taskProps.put(n, nodeRefs);
                }
            }
        });

        if (outcome[0] == null) {
            throw new IllegalStateException(OUTCOME_PREFIX + "* field is mandatory for task completion");
        }

        if (documentProps.getAttributes().size() > 0) {
            RecordRef documentRef = taskInfo.getDocument();
            if (documentRef != RecordRef.EMPTY) {
                RecordMeta docMutateMeta = new RecordMeta(documentRef);
                docMutateMeta.setAttributes(documentProps.getAttributes());
                RecordsMutation mutation = new RecordsMutation();
                mutation.setRecords(Collections.singletonList(docMutateMeta));
                recordsService.mutate(mutation);
            }
        }

        ecosTaskService.endTask(taskId, outcome[0], taskProps);
        return new RecordMeta(taskId);
    }

    private boolean isChangeOwnerAction(RecordMeta meta) {
        JsonNode changeOwner = meta.getAttribute(ATT_CHANGE_OWNER);
        return changeOwner != null && !changeOwner.isMissingNode() && !changeOwner.isNull();
    }

    private void processChangeOwnerAction(RecordMeta meta, String taskId) {
        JsonNode changeOwner = meta.getAttribute(ATT_CHANGE_OWNER);
        String paramAction = changeOwner.get(ATT_ACTION).asText();

        OwnerAction action = OwnerAction.valueOf(paramAction.toUpperCase());
        String owner = null;
        if (changeOwner.has(ATT_OWNER)) {
            String ownerParam = changeOwner.get(ATT_OWNER).asText();
            if (NodeRef.isNodeRef(ownerParam)) {
                ownerParam = authorityUtils.getAuthorityName(new NodeRef(ownerParam));
            }

            owner = CURRENT_USER.equals(ownerParam) ? AuthenticationUtil.getRunAsUser() : ownerParam;
        }

        ownerService.changeOwner(taskId, action, owner);
    }

    private String getEcmFieldName(String name) {
        return name.substring(DOCUMENT_FIELD_PREFIX.length()).replaceAll("_", ":");
    }

    private boolean isDate(String fieldName) {

        if (fieldName == null) {
            return false;
        }

        if (fieldName.startsWith(DOCUMENT_FIELD_PREFIX)) {
            fieldName = getEcmFieldName(fieldName);
        }

        if (fieldName.contains("_")) {
            fieldName = fieldName.replace("_", ":");
        }

        QName fieldQName = QName.resolveToQName(namespaceService, fieldName);
        PropertyDefinition property = dictionaryService.getProperty(fieldQName);

        if (property != null) {
            QName dataTypeQName = property.getDataType().getName();
            return DataTypeDefinition.DATE.equals(dataTypeQName);
        }

        return false;
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion deletion) {
        throw new UnsupportedOperationException();
    }

    private List<WorkflowTask> getRecordsByWfService(WorkflowTaskRecords.TasksQuery query) {

        if (query == null) {
            return null;
        }

        String document = query.document;
        if (StringUtils.isBlank(document)) {
            return null;
        }

        int idx = document.lastIndexOf('@');
        if (idx > -1 && idx < document.length() - 1) {
            document = document.substring(idx + 1);
        }

        NodeRef docRef = null;
        if (NodeRef.isNodeRef(document)) {
            docRef = new NodeRef(document);
        }

        if (docRef == null) {
            return null;
        }

        if ((query.actors != null && query.actors.size() == 1)) {

            String actor = query.actors.get(0);
            boolean isCurrentUser = CURRENT_USER.equals(actor);

            if (isCurrentUser) {
                return workflowUtils.getDocumentTasks(docRef, query.active, query.engine, isCurrentUser);
            }
        }

        return null;
    }

    @Override
    public RecordsQueryResult<RecordRef> getLocalRecords(RecordsQuery query) {

        WorkflowTaskRecords.TasksQuery tasksQuery = query.getQuery(WorkflowTaskRecords.TasksQuery.class);

        //try to search by workflow service to avoid problems with solr
        List<WorkflowTask> tasks = getRecordsByWfService(tasksQuery);

        if (tasks != null) {

            List<RecordRef> taskRefs = tasks.stream()
                    .map(t -> RecordRef.valueOf(t.getId()))
                    .collect(Collectors.toList());

            RecordsQueryResult<RecordRef> result = new RecordsQueryResult<>();
            result.setRecords(taskRefs);
            return result;
        }

        ComposedPredicate predicate = workflowTaskRecordsUtils.buildPredicateQuery(tasksQuery);
        if (predicate == null || predicate.getPredicates().isEmpty()) {
            return new RecordsQueryResult<>();
        }

        RecordsQueryResult<TaskIdQuery> taskQueryResult = workflowTaskRecordsUtils.queryTasks(predicate, query);

        return new RecordsQueryResult<>(taskQueryResult, task -> RecordRef.valueOf(task.getTaskId()));
    }

    @Override
    public List<MetaValue> getMetaValues(List<RecordRef> records) {
        return records.stream().map(r -> {
            Optional<TaskInfo> info = ecosTaskService.getTaskInfo(r.getId());
            return info.isPresent() ? new Task(info.get()) : new EmptyTask(r.getId());
        }).collect(Collectors.toList());
    }

    public static class TaskIdQuery {
        @MetaAtt("cm:name")
        @Getter @Setter public String taskId;

        @Override
        public String toString() {
            return taskId;
        }
    }

    public static class TasksQuery {

        @Getter @Setter public String workflowId;
        @Getter @Setter public String engine;
        @Getter @Setter public List<String> assignees;
        @Getter @Setter public List<String> actors;
        @Getter @Setter public Boolean active;
        @Getter @Setter public String docStatus;
        @Getter @Setter public String docType;
        @Getter @Setter public String document;

        public void setAssignee(String assignee) {
            if (assignees == null) {
                assignees = new ArrayList<>();
            }
            assignees.add(assignee);
        }

        public void setActor(String actor) {
            if (actors == null) {
                actors = new ArrayList<>();
            }
            actors.add(actor);
        }
    }

    public class EmptyTask implements MetaValue {

        private final String id;

        EmptyTask(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }
    }

    public class Task implements MetaValue {

        private RecordRef documentRef;
        private RecordMeta documentInfo;
        private TaskInfo taskInfo;

        @Override
        public <T extends QueryContext> void init(T context, MetaField field) {

            Map<String, String> documentAttributes = new HashMap<>();
            RecordRef documentRef = getDocumentRef();
            Map<String, String> attributesMap = field.getInnerAttributesMap();

            Set<String> customAttributes = new HashSet<>();

            for (String att : attributesMap.keySet()) {
                switch (att) {
                    case ATT_DOC_DISP_NAME:
                        documentAttributes.put(ATT_DOC_DISP_NAME, "cm:title");
                        customAttributes.add(ATT_DOC_DISP_NAME);
                        break;
                    case ATT_DOC_STATUS_TITLE:
                        documentAttributes.put(ATT_DOC_STATUS_TITLE, "icase:caseStatusAssoc.cm:title");
                        customAttributes.add(ATT_DOC_STATUS_TITLE);
                        break;
                    case ATT_DOC_STATUS:
                        documentAttributes.put(ATT_DOC_STATUS, "icase:caseStatusAssoc.cm:name");
                        customAttributes.add(ATT_DOC_STATUS);
                        break;
                    case ATT_DOC_TYPE:
                        documentAttributes.put(ATT_DOC_TYPE, "_type");
                        customAttributes.add(ATT_DOC_TYPE);
                        break;
                    default:
                        if (att.startsWith(DOCUMENT_FIELD_PREFIX)) {
                            String ecmFieldName = getEcmFieldName(att);
                            String fieldAtt = attributesMap.get(att).replace(att, ecmFieldName);

                            documentAttributes.put(att, fieldAtt);
                        }
                }
            }
            if (documentAttributes.isEmpty() || documentRef == null) {
                documentInfo = new RecordMeta();
            } else {
                documentInfo = recordsService.getRawAttributes(getDocumentRef(), documentAttributes);
                for (String customAtt : customAttributes) {
                    documentInfo.set(customAtt, simplify(documentInfo.get(customAtt)));
                }
            }
        }

        private JsonNode simplify(JsonNode node) {
            if (node == null) {
                return null;
            }
            if (node instanceof ObjectNode) {
                if (node.size() > 0) {
                    String name = node.fieldNames().next();
                    if (name.startsWith("att")) {
                        return simplify(node.get(name));
                    } else {
                        return node;
                    }
                } else {
                    return null;
                }
            } else {
                return node;
            }
        }

        public Task(TaskInfo taskInfo) {
            this.taskInfo = taskInfo;
        }

        @Override
        public String getId() {
            return taskInfo.getId();
        }

        @Override
        public Object getAttribute(String name, MetaField field) {

            if (EcosTaskService.FIELD_COMMENT.equals(name)) {
                return null;
            }

            if (documentInfo.has(name)) {
                return new InnerMetaValue(documentInfo.get(name));
            }

            if (RecordConstants.ATT_FORM_KEY.equals(name)) {
                String formKey = taskInfo.getFormKey();
                if (StringUtils.isBlank(formKey)) {
                    return null;
                }
                if (formKey.startsWith("alf_")) {
                    return formKey;
                }
                return Arrays.asList(formKey, "alf_" + formKey);
            }

            Map<String, Object> attributes = taskInfo.getAttributes();

            boolean hasPooledActors = CollectionUtils.isNotEmpty((List<?>) attributes.get("bpm_pooledActors"));
            boolean hasOwner = attributes.get("cm_owner") != null;
            boolean hasClaimOwner = attributes.get("claimOwner") != null;

            switch (name) {
                case ATT_SENDER:
                    String userName = (String) attributes.get("cwf_sender");
                    NodeRef userRef = authorityService.getAuthorityNodeRef(userName);
                    RecordRef userRecord = RecordRef.create("", userRef.toString());
                    return recordsService.getMeta(userRecord, AuthorityDTO.class);
                case ATT_ASSIGNEE:
                    String assignee = taskInfo.getAssignee();
                    if (StringUtils.isBlank(assignee)) {
                        return null;
                    }

                    NodeRef assigneeRef = authorityService.getAuthorityNodeRef(assignee);
                    RecordRef assigneeRecordRef = RecordRef.create("", assigneeRef.toString());
                    return recordsService.getMeta(assigneeRecordRef, AuthorityDTO.class);
                case ATT_CANDIDATE:
                    String candidate = taskInfo.getCandidate();
                    if (StringUtils.isBlank(candidate)) {
                        return null;
                    }

                    NodeRef candidateRef = authorityService.getAuthorityNodeRef(candidate);
                    RecordRef candidateRecordRef = RecordRef.create("", candidateRef.toString());
                    return recordsService.getMeta(candidateRecordRef, AuthorityDTO.class);
                case ATT_ACTORS:
                    return taskInfo.getActors()
                            .stream()
                            .map(actor -> {
                                RecordRef rr = RecordRef.create("", actor);
                                AuthorityDTO dto = recordsService.getMeta(rr, AuthorityDTO.class);
                                if (StringUtils.isNotBlank(dto.getAuthorityName())) {
                                    Set<String> containedUsers = authorityService.getContainedAuthorities(
                                            AuthorityType.USER, dto.getAuthorityName(), false);
                                    List<UserDTO> users = containedUsers.stream()
                                            .map(s -> recordsService.getMeta(RecordRef.create("",
                                                    authorityService.getAuthorityNodeRef(s).toString()),
                                                    UserDTO.class))
                                            .collect(Collectors.toList());
                                    dto.setContainedUsers(users);
                                }
                                return dto;
                            })
                            .collect(Collectors.toList());
                case ATT_DUE_DATE:
                    return attributes.get("bpm_dueDate");
                case ATT_STARTED:
                    return attributes.get("bpm_startDate");
                case ATT_LAST_COMMENT:
                    return attributes.get("cwf_lastcomment");
                case ATT_TITLE:
                    return taskInfo.getTitle();
                case ATT_DESCRIPTION:
                    return taskInfo.getDescription();
                case ATT_REASSIGNABLE:
                    return workflowTaskRecordsUtils.isReassignable(attributes, hasOwner, hasClaimOwner);
                case ATT_CLAIMABLE:
                    return workflowTaskRecordsUtils.isClaimable(attributes, hasOwner, hasClaimOwner, hasPooledActors);
                case ATT_RELEASABLE:
                    return workflowTaskRecordsUtils.isReleasable(attributes, hasOwner, hasClaimOwner, hasPooledActors);
                case ATT_ASSIGNABLE:
                    return workflowTaskRecordsUtils.isAssignable(attributes, hasOwner, hasClaimOwner, hasPooledActors);
                case ATT_ACTIVE:
                    return attributes.get("bpm_completionDate") == null;
                case ATT_DOC_SUM:
                    String ref = getDocumentRef().getId();
                    if (NodeRef.isNodeRef(ref)) {
                        NodeRef document = new NodeRef(ref);
                        return docSumService.getSum(document);
                    }
                    return null;
            }

            return attributes.get(name);
        }

        private RecordRef getDocumentRef() {
            if (documentRef == null) {
                documentRef = taskInfo.getDocument();
            }
            return documentRef;
        }
    }
}
