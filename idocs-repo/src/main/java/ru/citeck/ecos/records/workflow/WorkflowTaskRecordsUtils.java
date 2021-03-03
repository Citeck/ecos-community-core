package ru.citeck.ecos.records.workflow;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.icase.CaseStatusService;
import ru.citeck.ecos.model.WorkflowMirrorModel;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.*;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.request.query.QueryConsistency;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.utils.AuthorityUtils;
import ru.citeck.ecos.workflow.tasks.EcosTaskService;
import ru.citeck.ecos.workflow.tasks.TaskInfo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.citeck.ecos.records.workflow.WorkflowTaskRecordsConstants.CURRENT_USER;

@Slf4j
@Component
public class WorkflowTaskRecordsUtils {

    private static final char[] DOC_TYPES_MUST_CONTAINS_CHARS = new char[]{':', '{'};

    private final String DOC_TYPE_ATTR;
    private final String PRIORITY_ATTR;
    private final String COUNTERPARTY_ATTR;
    private final String DOCUMENT_ATTR;
    private final String CASE_STATUS_ATTR;
    private final String CASE_STATUS_ATTR_PROP;
    private final String DOC_ECOS_TYPE_ATTR;

    private final AuthorityUtils authorityUtils;
    private final NamespaceService namespaceService;
    private final RecordsService recordsService;
    private final EcosTaskService ecosTaskService;
    private final CaseStatusService caseStatusService;

    @Autowired
    public WorkflowTaskRecordsUtils(AuthorityUtils authorityUtils, NamespaceService namespaceService,
                                    RecordsService recordsService, EcosTaskService ecosTaskService,
                                    CaseStatusService caseStatusService) {
        this.authorityUtils = authorityUtils;
        this.namespaceService = namespaceService;
        this.recordsService = recordsService;
        this.ecosTaskService = ecosTaskService;
        this.caseStatusService = caseStatusService;

        DOC_TYPE_ATTR = WorkflowMirrorModel.PROP_DOCUMENT_TYPE.toPrefixString(namespaceService);
        PRIORITY_ATTR = WorkflowModel.PROP_PRIORITY.toPrefixString(namespaceService);
        COUNTERPARTY_ATTR = WorkflowMirrorModel.PROP_COUNTERPARTY.toPrefixString(namespaceService);
        DOCUMENT_ATTR = WorkflowMirrorModel.PROP_DOCUMENT.toPrefixString(namespaceService);
        CASE_STATUS_ATTR = WorkflowMirrorModel.PROP_CASE_STATUS.toPrefixString(namespaceService);
        CASE_STATUS_ATTR_PROP = WorkflowMirrorModel.PROP_CASE_STATUS_PROP.toPrefixString(namespaceService);
        DOC_ECOS_TYPE_ATTR = WorkflowMirrorModel.PROP_DOCUMENT_ECOS_TYPE.toPrefixString(namespaceService);
    }

    ComposedPredicate buildPredicateQuery(WorkflowTaskRecords.TasksQuery tasksQuery) {

        AndPredicate predicate = new AndPredicate();
        predicate.addPredicate(ValuePredicate.equal("TYPE", WorkflowModel.TYPE_TASK.toString()));

        appendActorsPredicate(tasksQuery.actors, predicate);
        appendActivePredicate(tasksQuery.active, predicate);
        appendCaseStatusPredicate(tasksQuery.docStatus, predicate);
        appendEcosCaseStatusPredicate(tasksQuery.docEcosStatus, predicate);
        appendDocTypesPredicate(tasksQuery.docTypes, predicate);
        appendDocumentParamPredicate(tasksQuery.document, predicate);
        appendPrioritiesPredicate(tasksQuery.priorities, predicate);
        appendCounterpartiesPredicate(tasksQuery.counterparties, predicate);
        appendDocEcosTypesPredicate(tasksQuery.docEcosTypes, predicate);

        if (predicate.getPredicates().isEmpty()) {
            return null;
        }

        return predicate;
    }

    private void appendActorsPredicate(List<String> actors, AndPredicate predicate) {
        if (CollectionUtils.isNotEmpty(actors)) {
            predicate.addPredicate(getActorsPredicate(actors));
        }
    }

    private OrPredicate getActorsPredicate(List<String> actors) {
        Set<String> actorRefs = actors.stream().flatMap(actor -> {
            if (CURRENT_USER.equals(actor)) {
                actor = AuthenticationUtil.getRunAsUser();
            } else if (NodeRef.isNodeRef(actor)) {
                actor = authorityUtils.getAuthorityName(new NodeRef(actor));
            }
            return Stream.concat(authorityUtils.getContainingAuthoritiesRefs(actor).stream(),
                Stream.of(authorityUtils.getNodeRef(actor)))
                .map(NodeRef::toString);
        }).collect(Collectors.toSet());

        OrPredicate orPred = new OrPredicate();
        actorRefs.forEach(a -> {
            ValuePredicate valuePredicate = new ValuePredicate();
            valuePredicate.setType(ValuePredicate.Type.CONTAINS);
            valuePredicate.setAttribute("wfm:actors");
            valuePredicate.setValue(a);
            orPred.addPredicate(valuePredicate);
        });

        return orPred;
    }

    private void appendActivePredicate(Boolean active, AndPredicate predicate) {
        if (active != null) {
            Predicate completionEmpty = new EmptyPredicate("bpm:completionDate");
            if (active) {
                predicate.addPredicate(completionEmpty);
            } else {
                predicate.addPredicate(new NotPredicate(completionEmpty));
            }
        }
    }

    private void appendCaseStatusPredicate(String caseStatus, AndPredicate predicate) {
        if (StringUtils.isBlank(caseStatus)) {
            return;
        }

        if (!NodeRef.isNodeRef(caseStatus)) {
            NodeRef ref = caseStatusService.getStatusByName(caseStatus.toLowerCase());
            if (ref != null) {
                caseStatus = ref.toString();
            }
        }

        predicate.addPredicate(ValuePredicate.equal(CASE_STATUS_ATTR, caseStatus));
    }

    private void appendEcosCaseStatusPredicate(NodeRef docEcosStatus, AndPredicate predicate) {
        if (docEcosStatus == null) {
            return;
        }

        if (caseStatusService.isAlfRef(docEcosStatus)) {
            String type = docEcosStatus.getStoreRef().toString();
            String status = docEcosStatus.getId();

            predicate.addPredicate(Predicates.and(
                ValuePredicate.equal(CASE_STATUS_ATTR_PROP, status),
                ValuePredicate.equal(DOC_ECOS_TYPE_ATTR, RecordRef.create("emodel", "type", type))
            ));
        } else {
            predicate.addPredicate(ValuePredicate.equal(CASE_STATUS_ATTR, docEcosStatus));
        }
    }

    private void appendDocTypesPredicate(List<String> docTypes, AndPredicate predicate) {
        if (CollectionUtils.isNotEmpty(docTypes)) {
            predicate.addPredicate(getDocTypesPredicate(docTypes));
        }
    }

    private OrPredicate getDocTypesPredicate(List<String> docTypes) {
        Set<String> types = docTypes.stream()
            .filter(this::isCorrectDocTypeFormat)
            .map(this::resolveDocType)
            .collect(Collectors.toSet());

        OrPredicate orPredicate = new OrPredicate();
        types.forEach(type -> orPredicate.addPredicate(ValuePredicate.equal(DOC_TYPE_ATTR, type)));
        return orPredicate;
    }

    private boolean isCorrectDocTypeFormat(String docType) {
        return StringUtils.containsAny(docType, DOC_TYPES_MUST_CONTAINS_CHARS);
    }

    private String resolveDocType(String rawDocType) {
        QName docType = QName.resolveToQName(namespaceService, rawDocType);
        if (docType != null) {
            return docType.toString();
        }

        log.warn("Document type qname '{}' is not found", rawDocType);
        return rawDocType;
    }

    private void appendDocumentParamPredicate(String documentParam, AndPredicate predicate) {
        if (StringUtils.isBlank(documentParam)) {
            return;
        }

        RecordRef recordRef = RecordRef.valueOf(documentParam);
        String id = recordRef.getId();

        if (isInvalidNodeRef(id, "document")) {
            return;
        }

        predicate.addPredicate(ValuePredicate.equal(DOCUMENT_ATTR, id));
    }

    private boolean isInvalidNodeRef(String nodeRef, String attName) {
        if (StringUtils.isNotBlank(nodeRef) && NodeRef.isNodeRef(nodeRef)) {
            return false;
        }

        log.warn("Param {} mus be nodeRef, but is '{}'", attName, nodeRef);
        return true;
    }

    private void appendPrioritiesPredicate(List<String> priorities, AndPredicate predicate) {
        if (CollectionUtils.isNotEmpty(priorities)) {
            predicate.addPredicate(getPrioritiesPredicate(priorities));
        }
    }

    private Predicate getPrioritiesPredicate(List<String> priorities) {
        OrPredicate orPredicate = new OrPredicate();
        priorities.forEach(priority -> orPredicate.addPredicate(ValuePredicate.equal(PRIORITY_ATTR, priority)));
        return orPredicate;
    }

    private void appendCounterpartiesPredicate(List<String> counterparties, AndPredicate predicate) {
        if (CollectionUtils.isNotEmpty(counterparties)) {
            predicate.addPredicate(getCounterpartiesPredicate(counterparties));
        }
    }

    private Predicate getCounterpartiesPredicate(List<String> counterparties) {
        OrPredicate orPredicate = new OrPredicate();

        for (String counterparty : counterparties) {
            RecordRef recordRef = RecordRef.valueOf(counterparty);
            String id = recordRef.getId();

            if (isInvalidNodeRef(id, "counterparty")) {
                continue;
            }

            ValuePredicate valuePredicate = new ValuePredicate();
            valuePredicate.setType(ValuePredicate.Type.CONTAINS);
            valuePredicate.setAttribute(COUNTERPARTY_ATTR);
            valuePredicate.setValue(id);
            orPredicate.addPredicate(valuePredicate);
        }

        return orPredicate;
    }

    private void appendDocEcosTypesPredicate(List<String> docEcosTypes, AndPredicate predicate) {
        if (CollectionUtils.isNotEmpty(docEcosTypes)) {
            predicate.addPredicate(getDocEcosTypesPredicate(docEcosTypes));
        }
    }

    private Predicate getDocEcosTypesPredicate(List<String> docEcosTypes) {
        OrPredicate orPredicate = new OrPredicate();
        docEcosTypes.forEach(ecosType -> orPredicate.addPredicate(ValuePredicate.equal(DOC_ECOS_TYPE_ATTR, ecosType)));
        return orPredicate;
    }

    RecordsQueryResult<WorkflowTaskRecords.TaskIdQuery> queryTasks(ComposedPredicate predicate, RecordsQuery query) {
        RecordsQuery taskRecordsQuery = new RecordsQuery();
        taskRecordsQuery.setLanguage(PredicateService.LANGUAGE_PREDICATE);
        taskRecordsQuery.setQuery(predicate);
        taskRecordsQuery.setSortBy(query.getSortBy());

        WorkflowTaskRecords.TasksQuery tasksQuery = query.getQuery(WorkflowTaskRecords.TasksQuery.class);
        boolean filterByActiveTaskRequired = Boolean.TRUE.equals(tasksQuery.active);

        if (query.getMaxItems() > -1 && filterByActiveTaskRequired) {
            taskRecordsQuery.setMaxItems(query.getMaxItems() * 5); //we need to increase the selection for filtering
        } else {
            taskRecordsQuery.setMaxItems(query.getMaxItems());
        }

        taskRecordsQuery.setSkipCount(query.getSkipCount());
        taskRecordsQuery.setDebug(query.isDebug());
        taskRecordsQuery.setConsistency(QueryConsistency.EVENTUAL);

        RecordsQueryResult<WorkflowTaskRecords.TaskIdQuery> result = recordsService.queryRecords(taskRecordsQuery,
            WorkflowTaskRecords.TaskIdQuery.class);

        if (filterByActiveTaskRequired) {
            filterActiveTask(result, query);
        }

        return result;
    }

    private void filterActiveTask(RecordsQueryResult<WorkflowTaskRecords.TaskIdQuery> taskQueryResult,
                                  RecordsQuery query) {
        log.debug("Start filterActiveTask...");

        final int maxItems = query.getMaxItems();
        long taskQueryResultTotalCount = taskQueryResult.getTotalCount();

        FilterByActiveTask resultOfFiltering = new FilterByActiveTask(taskQueryResult, maxItems);

        List<WorkflowTaskRecords.TaskIdQuery> filteredRecords = resultOfFiltering.getFilteredData();
        AtomicInteger recordsCount = resultOfFiltering.getRecordsCount();
        AtomicInteger filteredCount = resultOfFiltering.getFilteredCount();

        long totalCount = taskQueryResultTotalCount - filteredCount.get();

        taskQueryResult.setRecords(filteredRecords);
        taskQueryResult.setTotalCount(totalCount);

        log.debug(String.format("finish filtering:" +
                "\nresult:%s" +
                "\nmaxItems:%s" +
                "\ntaskQueryResultTotalCount:%s" +
                "\nrecordsCount:%s" +
                "\nfilteredCount:%s", filteredRecords, maxItems, taskQueryResultTotalCount, recordsCount,
            filteredCount));
    }

    private boolean taskIsActive(String taskId) {
        Optional<TaskInfo> taskInfo = ecosTaskService.getTaskInfo(taskId);
        boolean active = taskInfo.filter(info -> info.getAttribute("bpm_completionDate") == null).isPresent();

        if (log.isDebugEnabled()) {
            log.debug(String.format("Task <%s>, is active: %s", taskId, active));
        }

        return active;
    }

    boolean isReassignable(Map<String, Object> attributes, boolean hasOwner, boolean hasClaimOwner) {
        boolean bpmIsReassignable = Boolean.TRUE.equals(attributes.get("bpm_reassignable"));
        boolean isReassignableAllowed = bpmIsReassignable && (hasOwner || hasClaimOwner);
        boolean isReassignableDisabled = Boolean.FALSE.equals(attributes.get("cwf_isTaskReassignable"));
        return isReassignableAllowed && !isReassignableDisabled;
    }

    boolean isClaimable(Map<String, Object> attributes, boolean hasOwner, boolean hasClaimOwner,
                        boolean hasPooledActors) {
        boolean isClaimableAllowed = hasPooledActors && (!hasOwner && !hasClaimOwner);
        boolean isClaimableDisabled = Boolean.FALSE.equals(attributes.get("cwf_isTaskClaimable"));
        return isClaimableAllowed && !isClaimableDisabled;
    }

    boolean isAssignable(Map<String, Object> attributes, boolean hasOwner, boolean hasClaimOwner,
                         boolean hasPooledActors) {
        return isClaimable(attributes, hasOwner, hasClaimOwner, hasPooledActors);
    }

    boolean isReleasable(Map<String, Object> attributes, boolean hasOwner, boolean hasClaimOwner,
                         boolean hasPooledActors) {
        boolean isReleasableAllowed = hasPooledActors && (hasOwner || hasClaimOwner);
        boolean isReleasableDisabled = Boolean.FALSE.equals(attributes.get("cwf_isTaskReleasable"));
        return isReleasableAllowed && !isReleasableDisabled;
    }

    @Getter
    private class FilterByActiveTask {

        private AtomicInteger recordsCount = new AtomicInteger(0);
        private AtomicInteger filteredCount = new AtomicInteger(0);
        private List<WorkflowTaskRecords.TaskIdQuery> filteredData;

        FilterByActiveTask(RecordsQueryResult<WorkflowTaskRecords.TaskIdQuery> data, int maxItems) {
            filteredData = data.getRecords()
                .stream()
                .filter(taskIdQuery -> {
                    if (maxItems > -1 && maxItems <= recordsCount.getAndIncrement()) {
                        return false;
                    }

                    if (taskIsActive(taskIdQuery.getTaskId())) {
                        return true;
                    } else {
                        filteredCount.incrementAndGet();
                        return false;
                    }
                })
                .collect(Collectors.toList());
        }
    }

}
