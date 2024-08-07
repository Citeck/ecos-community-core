package ru.citeck.ecos.records.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.template.TemplateNode;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.cmr.workflow.WorkflowInstance;
import org.alfresco.service.cmr.workflow.WorkflowTask;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.document.CounterpartyResolver;
import ru.citeck.ecos.document.sum.DocSumService;
import ru.citeck.ecos.graphql.AlfGqlContext;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records.AlfRecordConstants;
import ru.citeck.ecos.records.models.AuthorityDTO;
import ru.citeck.ecos.records.models.UserDTO;
import ru.citeck.ecos.records.source.alf.AlfNodeMetaEdge;
import ru.citeck.ecos.records2.QueryContext;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
import ru.citeck.ecos.records2.graphql.meta.value.*;
import ru.citeck.ecos.records2.predicate.model.ComposedPredicate;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.mutation.RecordsMutation;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.MutableRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryDao;
import ru.citeck.ecos.role.CaseRoleService;
import ru.citeck.ecos.security.EcosPermissionService;
import ru.citeck.ecos.utils.AuthorityUtils;
import ru.citeck.ecos.utils.NodeUtils;
import ru.citeck.ecos.utils.TransactionUtils;
import ru.citeck.ecos.utils.WorkflowUtils;
import ru.citeck.ecos.webapp.api.constants.AppName;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.workflow.mirror.WorkflowMirrorService;
import ru.citeck.ecos.workflow.owner.OwnerAction;
import ru.citeck.ecos.workflow.owner.OwnerService;
import ru.citeck.ecos.workflow.records.WorkflowRecordsDao;
import ru.citeck.ecos.workflow.tasks.EcosTaskService;
import ru.citeck.ecos.workflow.tasks.TaskInfo;

import java.util.*;
import java.util.stream.Collectors;

import static ru.citeck.ecos.records.workflow.WorkflowTaskRecordsConstants.*;

@Slf4j
@Component
public class WorkflowTaskRecords extends LocalRecordsDao
    implements LocalRecordsMetaDao<MetaValue>,
    MutableRecordsDao,
    LocalRecordsQueryDao {

    private static final String DOCUMENT_FIELD_PREFIX = "_ECM_";
    private static final String OUTCOME_PREFIX = "outcome_";
    private static final String APP_EPROC = "eproc";


    private static final String ID = "wftask";

    private static final String GROUP_WORKFLOW_TASKS_REASSIGN_ALLOWED = "GROUP_WORKFLOW_TASKS_REASSIGN_ALLOWED";
    private static final String PERMS_WRITE = "Write";
    private static final String PERMS_READ = "Read";
    private static final String PERMS_REASSIGN = "Reassign";

    private final EcosTaskService ecosTaskService;
    private final AuthorityService authorityService;
    private final WorkflowTaskRecordsUtils workflowTaskRecordsUtils;
    private final OwnerService ownerService;
    private final DocSumService docSumService;
    private final CounterpartyResolver counterpartyResolver;
    private final WorkflowUtils workflowUtils;
    private final AuthorityUtils authorityUtils;
    private final NamespaceService namespaceService;
    private final DictionaryService dictionaryService;
    private final EcosTypeService ecosTypeService;
    private final NodeUtils nodeUtils;
    private final CaseRoleService caseRoleService;
    private final EcosPermissionService ecosPermissionService;
    private final WorkflowMirrorService workflowMirrorService;

    @Autowired
    public WorkflowTaskRecords(EcosTaskService ecosTaskService,
                               WorkflowTaskRecordsUtils workflowTaskRecordsUtils,
                               AuthorityService authorityService, OwnerService ownerService,
                               DocSumService docSumService,
                               CounterpartyResolver counterpartyResolver,
                               WorkflowUtils workflowUtils, AuthorityUtils authorityUtils,
                               NamespaceService namespaceService,
                               DictionaryService dictionaryService, EcosTypeService ecosTypeService,
                               NodeUtils nodeUtils,
                               CaseRoleService caseRoleService,
                               EcosPermissionService ecosPermissionService,
                               WorkflowMirrorService workflowMirrorService) {
        setId(ID);
        this.workflowMirrorService = workflowMirrorService;
        this.ecosPermissionService = ecosPermissionService;
        this.counterpartyResolver = counterpartyResolver;
        this.namespaceService = namespaceService;
        this.dictionaryService = dictionaryService;
        this.ecosTaskService = ecosTaskService;
        this.workflowTaskRecordsUtils = workflowTaskRecordsUtils;
        this.authorityService = authorityService;
        this.ownerService = ownerService;
        this.docSumService = docSumService;
        this.workflowUtils = workflowUtils;
        this.authorityUtils = authorityUtils;
        this.ecosTypeService = ecosTypeService;
        this.nodeUtils = nodeUtils;
        this.caseRoleService = caseRoleService;
    }

    @Override
    public RecordsMutResult mutateImpl(RecordsMutation mutation) {

        RecordsMutResult result = new RecordsMutResult();

        result.setRecords(mutation.getRecords()
            .stream()
            .map(meta -> new RecordMeta(meta, EntityRef.valueOf(meta.getId().getLocalId())))
            .map(this::mutate)
            .map(meta -> new RecordMeta(meta, EntityRef.create(getId(), meta.getId().toString())))
            .collect(Collectors.toList()));

        return result;
    }

    private RecordMeta mutate(RecordMeta meta) {

        String taskId = meta.getId().getLocalId();
        Optional<TaskInfo> taskInfoOpt = ecosTaskService.getTaskInfo(taskId);

        if (!taskInfoOpt.isPresent()) {
            throw new IllegalArgumentException("Task not found! id: " + taskId);
        }

        if (isChangeOwnerAction(meta)) {
            if (isUserPartOfReassignAllowedGroup(AuthenticationUtil.getFullyAuthenticatedUser())) {
                AuthenticationUtil.runAsSystem(() -> {
                    processChangeOwnerAction(meta, taskId);
                    return null;
                });
            } else {
                processChangeOwnerAction(meta, taskId);
            }
            return new RecordMeta(taskId);
        }

        TaskInfo taskInfo = taskInfoOpt.get();

        RecordMeta documentProps = new RecordMeta();
        Map<String, Object> taskProps = new HashMap<>();

        String[] outcome = new String[1];

        meta.forEachJ((n, v) -> processMutateProp(n, v, taskProps, documentProps, outcome));

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

    private void processMutateProp(
        String key,
        DataValue value,
        Map<String, Object> taskProps,
        RecordMeta documentProps,
        String[] outcome
    ) {
        if ("_formInfo".equals(key)) {
            taskProps.put(key, value.getAs(JsonNode.class));
            return;
        }
        if (key.startsWith(DOCUMENT_FIELD_PREFIX)) {
            documentProps.set(getEcmFieldName(key), value);
            return;
        }
        if (key.startsWith(OUTCOME_PREFIX)) {
            if (value.isBoolean() && value.asBoolean()) {
                outcome[0] = key.substring(OUTCOME_PREFIX.length());
            }
            return;
        }
        String name = key;
        if (name.contains(":")) {
            name = name.replaceFirst(":", "_");
        }

        if (value.isTextual()) {
            String valueStr = value.asText();
            if (isDate(name) && StringUtils.isEmpty(valueStr)) {
                valueStr = null;
            }
            if (authorityUtils.isAuthorityRef(valueStr)) {
                valueStr = authorityUtils.getNodeRefNotNull(valueStr).toString();
            }
            taskProps.put(name, valueStr);
        } else if (value.isBoolean()) {
            taskProps.put(name, value.asBoolean());
        } else if (value.isDouble()) {
            taskProps.put(name, value.asDouble());
        } else if (value.isInt()) {
            taskProps.put(name, value.asInt());
        } else if (value.isLong()) {
            taskProps.put(name, value.asLong());
        } else if (value.isNull()) {
            taskProps.put(name, null);
        } else if (value.isArray()) {
            Set<NodeRef> nodeRefs = new HashSet<>();
            for (DataValue jsonNode : value) {
                String stringNode = jsonNode.asText();
                if (authorityUtils.isAuthorityRef(stringNode)) {
                    nodeRefs.add(authorityUtils.getNodeRefNotNull(stringNode));
                } else if (nodeUtils.isNodeRef(stringNode)) {
                    nodeRefs.add(new NodeRef(stringNode));
                }
            }
            taskProps.put(name, nodeRefs);
        }
    }

    private boolean isChangeOwnerAction(RecordMeta meta) {
        DataValue changeOwner = meta.getAttribute(ATT_CHANGE_OWNER);
        return !changeOwner.isNull();
    }

    private boolean isUserPartOfReassignAllowedGroup(String userName) {
        if (StringUtils.isBlank(userName)) {
            return true;
        }
        if (authorityService.getAuthoritiesForUser(userName).contains(GROUP_WORKFLOW_TASKS_REASSIGN_ALLOWED)) {
            return true;
        }
        return false;
    }

    private void processChangeOwnerAction(RecordMeta meta, String taskId) {

        DataValue changeOwner = meta.getAttribute(ATT_CHANGE_OWNER);
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

        String normalizedOwner = authorityUtils.getAuthorityName(owner);

        TaskInfo taskInfo = ecosTaskService.getTaskInfo(taskId).orElse(null);
        if (taskInfo == null) {
            throw new IllegalStateException("taskInfo is null for taskId: " + taskId);
        }
        Set<String> roles = taskInfo.getCandidateRoles();
        RecordRef document = taskInfo.getDocument();
        if (document.getId().startsWith(NodeUtils.WORKSPACE_SPACES_STORE_PREFIX) && !roles.isEmpty()) {

            NodeRef documentNodeRef = new NodeRef(document.getId());

            for (String roleId : roles) {
                NodeRef roleRef = caseRoleService.getRole(documentNodeRef, roleId);
                if (roleRef == null) {
                    continue;
                }
                Set<NodeRef> assignees = caseRoleService.getAssignees(documentNodeRef, roleId);
                Map<NodeRef, String> delegates = new HashMap<>();
                for (NodeRef assignee : assignees) {
                    delegates.put(assignee, normalizedOwner);
                }
                if (!delegates.isEmpty()) {
                    caseRoleService.setDelegates(roleRef, delegates);
                }
            }
        }
        ownerService.changeOwner(taskId, action, normalizedOwner);
        if (document.getId().startsWith(NodeUtils.WORKSPACE_SPACES_STORE_PREFIX)) {
            TransactionUtils.doAfterBehaviours(() ->
                ecosPermissionService.updateNodePermissions(new NodeRef(document.getId()))
            );
        }
        workflowMirrorService.mirrorTask(taskId);
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
    public RecordsDelResult delete(@NotNull RecordsDeletion deletion) {
        throw new UnsupportedOperationException();
    }

    private List<WorkflowTask> getRecordsByWfService(WorkflowTaskRecords.TasksQuery query) {

        if (query == null) {
            return null;
        }

        String document = query.document;
        if (StringUtils.isBlank(document) && StringUtils.isBlank(query.workflowId)) {
            return null;
        }

        if (query.priorities != null || query.counterparties != null || query.docTypes != null
            || query.docEcosTypes != null) {
            return null;
        }

        RecordRef docRef = RecordRef.valueOf(query.document);
        String workflowId = null;

        if (EntityRef.isEmpty(docRef)) {
            workflowId = query.getWorkflowId();
        }

        if ((query.actors != null && query.actors.size() == 1)) {

            String actor = query.actors.get(0);
            boolean isCurrentUser = CURRENT_USER.equals(actor);

            if (isCurrentUser) {
                if (workflowId == null) {
                    return workflowUtils.getDocumentTasks(docRef, query.active, query.engine, true);
                } else {
                    return workflowUtils.getWorkflowTasks(workflowId, query.active, true);
                }
            }
        }

        if (query.actors == null) {
            String finalWfId = workflowId;
            return AuthenticationUtil.runAsSystem(() -> {
                if (finalWfId == null) {
                    return workflowUtils.getDocumentTasks(docRef, query.active, query.engine, false);
                } else {
                    return workflowUtils.getWorkflowTasks(finalWfId, query.active, false);
                }
            });
        }

        return null;
    }

    @Override
    public RecordsQueryResult<EntityRef> queryLocalRecords(RecordsQuery query) {

        WorkflowTaskRecords.TasksQuery tasksQuery = query.getQuery(WorkflowTaskRecords.TasksQuery.class);
        if (tasksQuery.document != null) {
            EntityRef docRecordRef = EntityRef.valueOf(tasksQuery.document);
            if (docRecordRef.getAppName().startsWith(APP_EPROC)) {
                docRecordRef = EntityRef.valueOf(docRecordRef.getLocalId());

                if (docRecordRef.getSourceId().isEmpty()) {
                    docRecordRef = docRecordRef.withSourceId(WorkflowRecordsDao.ID);
                }
            }

            if (docRecordRef.getSourceId().equals(WorkflowRecordsDao.ID)) {
                tasksQuery.setWorkflowId(docRecordRef.getLocalId());
                tasksQuery.setDocument(null);
            }
        }

        //try to search by workflow service to avoid problems with solr
        List<WorkflowTask> tasks = getRecordsByWfService(tasksQuery);

        if (tasks != null) {

            List<EntityRef> taskRefs = tasks.stream()
                .map(t -> EntityRef.valueOf(t.getId()))
                .collect(Collectors.toList());

            RecordsQueryResult<EntityRef> result = new RecordsQueryResult<>();
            result.setRecords(taskRefs);
            return result;
        }

        ComposedPredicate predicate = workflowTaskRecordsUtils.buildPredicateQuery(tasksQuery);
        if (predicate == null || predicate.getPredicates().isEmpty()) {
            return new RecordsQueryResult<>();
        }

        RecordsQueryResult<TaskIdQuery> taskQueryResult = workflowTaskRecordsUtils.queryTasks(predicate, query);

        return new RecordsQueryResult<>(taskQueryResult, task -> EntityRef.valueOf(task.getTaskId()));
    }

    @Override
    public List<MetaValue> getLocalRecordsMeta(List<EntityRef> records, MetaField metaField) {
        return records.stream().map(r -> {
            Optional<TaskInfo> info = ecosTaskService.getTaskInfo(r.getLocalId());
            return info.isPresent() ? new Task(info.get()) : new EmptyTask(r.getLocalId());
        }).collect(Collectors.toList());
    }

    @Data
    public static class TaskIdQuery {
        @MetaAtt("cm:name")
        public String taskId;

        @Override
        public String toString() {
            return taskId;
        }
    }

    @Data
    public static class TasksQuery {

        public String workflowId;
        public String engine;
        public List<String> assignees;
        public List<String> actors;
        public Boolean active;
        public String docStatus;
        public NodeRef docEcosStatus;
        public List<String> docTypes;
        public String document;
        public List<String> priorities;
        public List<String> counterparties;
        public List<String> docEcosTypes;

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

        public void setDocType(String docType) {
            if (docTypes == null) {
                docTypes = new ArrayList<>();
            }
            docTypes.add(docType);
        }

        public void setPriority(String priority) {
            if (priorities == null) {
                priorities = new ArrayList<>();
            }
            priorities.add(priority);
        }

        public void setCounterparty(String counterparty) {
            if (counterparties == null) {
                counterparties = new ArrayList<>();
            }
            counterparties.add(counterparty);
        }

        public void setDocEcosType(String docEcosType) {
            if (docEcosTypes == null) {
                docEcosTypes = new ArrayList<>();
            }
            docEcosTypes.add(docEcosType);
        }
    }

    public static class EmptyTask implements MetaValue {

        private final String id;

        EmptyTask(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public Object getAttribute(@NotNull String name, @NotNull MetaField field) {
            return EmptyValue.INSTANCE.getAttribute(name, field);
        }
    }

    public class Task implements MetaValue {

        private final TaskInfo taskInfo;

        private RecordRef documentRef;
        private RecordMeta documentInfo;
        private AlfGqlContext context;

        @Override
        public <T extends QueryContext> void init(T context, MetaField field) {

            this.context = (AlfGqlContext) context;

            Map<String, String> documentAttributes = new HashMap<>();
            RecordRef documentRef = getDocumentRef();
            Map<String, String> attributesMap = field.getInnerAttributesMap();

            Set<String> customAttributes = new HashSet<>();

            for (Map.Entry<String, String> entry : attributesMap.entrySet()) {
                String att = entry.getKey();
                switch (att) {
                    case ATT_DOC_DISP_NAME:
                        documentAttributes.put(ATT_DOC_DISP_NAME, "cm:title");
                        customAttributes.add(ATT_DOC_DISP_NAME);
                        break;
                    case ATT_DOC_STATUS_TITLE:
                        documentAttributes.put(ATT_DOC_STATUS_TITLE, "_status?disp");
                        customAttributes.add(ATT_DOC_STATUS_TITLE);
                        documentAttributes.put(ATT_DOC_STATUS_DISP_PROP, "idocs:documentStatus?disp");
                        customAttributes.add(ATT_DOC_STATUS_DISP_PROP);
                        break;
                    case ATT_DOC_STATUS:
                        documentAttributes.put(ATT_DOC_STATUS, "_status?str");
                        customAttributes.add(ATT_DOC_STATUS);
                        documentAttributes.put(ATT_DOC_STATUS_STR_PROP, "idocs:documentStatus?str");
                        customAttributes.add(ATT_DOC_STATUS_STR_PROP);
                        break;
                    case ATT_DOC_TYPE:
                        documentAttributes.put(ATT_DOC_TYPE, "type");
                        customAttributes.add(ATT_DOC_TYPE);
                        break;
                    default:
                        if (att.startsWith(DOCUMENT_FIELD_PREFIX)) {
                            String ecmFieldName = getEcmFieldName(att);
                            String fieldAtt = entry.getValue().replace(att, ecmFieldName);

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

        private DataValue simplify(DataValue node) {
            if (node == null) {
                return null;
            }
            if (node.isObject()) {
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
                DataValue node = documentInfo.get(name);
                if (node.isArray()) {
                    List<InnerMetaValue> result = new ArrayList<>();
                    node.forEach(jsonNode -> result.add(new InnerMetaValue(jsonNode)));
                    return result;
                }

                // in case when document hasn't 'caseStatusAssoc'
                if (node.isNull()) {
                    if (ATT_DOC_STATUS.equals(name)) {
                        return Collections.singletonList(new InnerMetaValue(documentInfo.get(ATT_DOC_STATUS_STR_PROP)));
                    } else if (ATT_DOC_STATUS_TITLE.equals(name)) {
                        return Collections.singletonList(new InnerMetaValue(documentInfo.get(ATT_DOC_STATUS_DISP_PROP)));
                    }
                }
                return new InnerMetaValue(node);
            }

            if (this.taskInfo == null) {
                return null;
            }

            if (AlfRecordConstants.ATT_FORM_KEY.equals(name)) {
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

            RecordRef document = getDocumentRef();
            String documentRefId = document.getId();
            NodeRef documentNodeRef = StringUtils.isNotBlank(documentRefId) && NodeRef.isNodeRef(documentRefId) ?
                new NodeRef(documentRefId) : null;

            switch (name) {
                case ATT_WORKFLOW:
                    WorkflowInstance workflowInstance = this.taskInfo.getWorkflow();
                    if (workflowInstance == null) {
                        return null;
                    }
                    return RecordRef.create(ATT_WORKFLOW, workflowInstance.getId());
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
                    if (documentNodeRef != null) {
                        return docSumService.getSum(documentNodeRef);
                    }
                    return null;
                case ATT_COUNTERPARTY:
                    if (documentNodeRef != null) {
                        NodeRef counterparty = counterpartyResolver.resolve(documentNodeRef);
                        return counterparty != null ? RecordRef.valueOf(counterparty.toString()) : null;
                    }
                    return null;
                case ATT_DOCUMENT:
                    return this.getDocumentRef();
                case ATT_DOC_ECOS_TYPE:
                    if (documentNodeRef != null) {
                        return ecosTypeService.getEcosType(documentNodeRef);
                    }
                    return null;
                case ATT_ETYPE:
                    return RecordRef.create("emodel", "type", "workflow-task");
                case ATT_PERMISSIONS:
                    return new Permissions(taskInfo);
            }

            if (name.contains(":")) {
                name = name.replaceFirst(":", "_");
            }

            return convertValueToAttResult(attributes.get(name));
        }

        @Override
        public RecordRef getRecordType() {
            return RecordRef.create("emodel", "type", "workflow-task");
        }

        private Object convertValueToAttResult(Object value) {

            if (value == null
                || value instanceof NullNode
                || value instanceof ecos.com.fasterxml.jackson210.databind.node.NullNode
                || (value instanceof DataValue && ((DataValue) value).isNull())) {

                return null;
            }

            if (value instanceof Collection) {
                List<Object> res = new ArrayList<>();
                @SuppressWarnings("unchecked")
                Iterable<Object> iterableObj = (Iterable<Object>) value;
                for (Object it : iterableObj) {
                    res.add(convertValueToAttResult(it));
                }
                return res;
            }
            if (value instanceof ScriptNode) {
                value = ((ScriptNode) value).getNodeRef();
            } else if (value instanceof TemplateNode) {
                value = ((TemplateNode) value).getNodeRef();
            }
            if (value instanceof NodeRef) {
                return RecordRef.create(AppName.ALFRESCO, "", value.toString());
            }
            if (value instanceof String) {
                String valueStr = (String) value;
                if (!valueStr.isEmpty() && valueStr.charAt(0) == 'w' && NodeRef.isNodeRef(valueStr)) {
                    return RecordRef.create(AppName.ALFRESCO, "", valueStr);
                }
            }
            return value;
        }

        @Override
        public String getDisplayName() {
            if (taskInfo != null) {
                return taskInfo.getTitle();
            }
            return null;
        }

        @Override
        public MetaEdge getEdge(String name, MetaField field) {
            if (name.startsWith(DOCUMENT_FIELD_PREFIX)) {
                name = name.replaceFirst(DOCUMENT_FIELD_PREFIX, "");
            }
            if (!name.contains(":") && name.contains("_")) {
                name = name.replaceFirst("_", ":");
            }
            return new AlfNodeMetaEdge(context, null, name, name, this);
        }

        private RecordRef getDocumentRef() {
            log.debug("Task getDocumentRef()");

            if (documentRef == null) {
                documentRef = taskInfo.getDocument();
            }

            log.debug("documentRef: " + documentRef);

            if (StringUtils.isEmpty(documentRef.getId())) {
                Map<String, Object> attributes = taskInfo.getAttributes();
                Object docObject = attributes.get("document");
                log.debug("docObject: " + docObject);

                if (docObject instanceof ScriptNode) {
                    NodeRef docNodeRef = ((ScriptNode) docObject).getNodeRef();

                    log.debug("docNodeRef: " + docNodeRef);

                    return RecordRef.create(AppName.ALFRESCO, "", String.valueOf(docNodeRef));
                }
            }

            String id = documentRef.getId();
            if (nodeUtils.isNodeRef(id) && StringUtils.isBlank(documentRef.getAppName())) {
                return RecordRef.create(AppName.ALFRESCO, "", id);
            }

            return documentRef;
        }
    }

    @RequiredArgsConstructor
    public class Permissions implements MetaValue {

        private final TaskInfo taskInfo;

        @Override
        public boolean has(String permission) {

            if (!permission.equalsIgnoreCase(PERMS_WRITE) && !permission.equalsIgnoreCase(PERMS_READ) &&
                !permission.equalsIgnoreCase(PERMS_REASSIGN)) {
                return true;
            }

            String userName = AuthenticationUtil.getFullyAuthenticatedUser();
            if (StringUtils.isBlank(userName)) {
                return false;
            }

            if (authorityService.isAdminAuthority(userName) ||
                    Objects.equals(userName, AuthenticationUtil.getSystemUserName())) {
                return true;
            }

            if (PERMS_REASSIGN.equalsIgnoreCase(permission) && isUserPartOfReassignAllowedGroup(userName)) {
                return true;
            }

            String assignee = taskInfo.getAssignee();
            if (StringUtils.isNotBlank(assignee)) {
                return Objects.equals(userName, assignee);
            }

            List<String> actors = taskInfo.getActors()
                .stream()
                .map(actor -> actor.startsWith("workspace://") ? authorityUtils.getAuthorityName(new NodeRef(actor)) : actor)
                .collect(Collectors.toList());

            if (actors.contains(userName)) {
                return true;
            }

            Set<String> authoritiesForUser = authorityService.getAuthoritiesForUser(userName);
            if (actors.stream().anyMatch(authoritiesForUser::contains)) {
                return true;
            }

            return false;
        }
    }
}
