package ru.citeck.ecos.records;

import com.fasterxml.jackson.databind.util.ISO8601Utils;
import org.alfresco.model.ContentModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.NamespaceException;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.config.EcosConfigService;
import ru.citeck.ecos.graphql.AlfGqlContext;
import ru.citeck.ecos.graphql.node.Attribute;
import ru.citeck.ecos.graphql.node.GqlAlfNode;
import ru.citeck.ecos.history.HistoryEventType;
import ru.citeck.ecos.model.HistoryModel;
import ru.citeck.ecos.records.meta.MetaMapValue;
import ru.citeck.ecos.records.source.alf.meta.AlfNodeAttValue;
import ru.citeck.ecos.records2.QueryContext;
import ru.citeck.ecos.records2.ServiceFactoryAware;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.request.query.SortBy;
import ru.citeck.ecos.records2.request.query.page.QueryPage;
import ru.citeck.ecos.records2.request.query.page.SkipPage;
import ru.citeck.ecos.records2.source.dao.AbstractRecordsDao;
import ru.citeck.ecos.records2.source.dao.RecordsQueryWithMetaDao;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts;
import ru.citeck.ecos.records3.record.atts.schema.SchemaAtt;
import ru.citeck.ecos.records3.record.atts.schema.write.AttSchemaWriter;
import ru.citeck.ecos.search.*;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.utils.AuthorityUtils;
import ru.citeck.ecos.utils.RepoUtils;
import ru.citeck.ecos.utils.search.SearchResult;
import ru.citeck.ecos.utils.search.SearchUtils;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Task statistic journal prototype
 *
 * @author Pavel Simonov
 */
@Component
public class TaskStatisticRecords extends AbstractRecordsDao implements RecordsQueryWithMetaDao, ServiceFactoryAware {

    public static final String ID = "task-statistic";

    private static final String PERFORM_TIME_RATIO = "history:performTimeRatio";
    private static final String ACTUAL_PERFORM_TIME = "history:actualPerformTime";
    private static final String COMPLETION_DATE = "history:completionDate";
    private static final String EXPECTED_PERFORM_TIME = "history:expectedPerformTime";
    private static final String STARTED_DATE = "event:date";
    private static final String SHOW_UNASSIGNED_IN_STATISTIC_CONFIG_KEY = "show-unassigned-in-statistic-config";

    private static final String PRED_STR_EQUALS = SearchPredicate.STRING_EQUALS.getValue();

    private static final Log logger = LogFactory.getLog(TaskStatisticRecords.class);
    private static final int TOTAL_COUNT_PAGE_SKIP_COUNT = 0;

    private final PersonService personService;
    private final SearchService searchService;
    private final NamespaceService namespaceService;
    private final NodeService nodeService;
    private final SearchCriteriaParser criteriaParser;
    private final AuthorityUtils authorityUtils;
    private final FTSQueryBuilder queryBuilder;
    private final SearchUtils searchUtils;
    private final EcosConfigService ecosConfigService;

    private RecordsService recordsService;
    private AttSchemaWriter attSchemaWriter;

    @Autowired
    public TaskStatisticRecords(ServiceRegistry serviceRegistry,
                                SearchCriteriaParser criteriaParser,
                                AuthorityUtils authorityUtils,
                                FTSQueryBuilder queryBuilder,
                                SearchUtils searchUtils,
                                EcosConfigService ecosConfigService) {
        setId(ID);
        this.personService = serviceRegistry.getPersonService();
        this.searchService = serviceRegistry.getSearchService();
        this.namespaceService = serviceRegistry.getNamespaceService();
        this.nodeService = serviceRegistry.getNodeService();
        this.authorityUtils = authorityUtils;
        this.criteriaParser = criteriaParser;
        this.queryBuilder = queryBuilder;
        this.searchUtils = searchUtils;
        this.ecosConfigService = ecosConfigService;
    }

    @Override
    public RecordsQueryResult<RecordAtts> queryRecords(RecordsQuery query, List<SchemaAtt> schema, boolean rawAtts) {

        Map<String, String> attsToReq = attSchemaWriter.writeToMap(schema);

        RecordsQueryResult<RecordAtts> records = new RecordsQueryResult<>();
        RecordsQueryResult<MetaValue> metaValues = getRecordsImpl(query);
        records.merge(metaValues);
        records.setHasMore(metaValues.getHasMore());
        records.setRecords(recordsService.getAtts(metaValues.getRecords(), attsToReq, rawAtts));

        long totalCount = getRecordsTotalCount(query);
        records.setTotalCount(totalCount);

        return records;
    }

    private long getRecordsTotalCount(RecordsQuery query) {
        query.setMaxItems(Integer.MAX_VALUE);

        QueryPage page = query.getPage();
        if (query.getPage() instanceof SkipPage) {
            page = ((SkipPage) query.getPage()).withSkipCount(TOTAL_COUNT_PAGE_SKIP_COUNT);
        }
        query.setPage(page);

        return getRecordsImpl(query).getTotalCount();
    }

    private RecordsQueryResult<MetaValue> getRecordsImpl(RecordsQuery query) {

        AlfGqlContext context = QueryContext.getCurrent();

        Boolean showUnassignedInStatisticConfig = strToBool((String) ecosConfigService.getParamValue(
                SHOW_UNASSIGNED_IN_STATISTIC_CONFIG_KEY), false);

        SearchResult<NodeRef> eventsSearchResult = getEvents(query, !showUnassignedInStatisticConfig);
        List<GqlAlfNode> eventsNodes = wrapNodes(eventsSearchResult.getItems(), context);

        if (!showUnassignedInStatisticConfig) {
            eventsNodes = mergeAssignEvents(eventsNodes);
        }

        List<MetaValue> records = new ArrayList<>();

        eventsNodes.forEach(eventNode -> {

            if (records.size() == query.getMaxItems()) {
                return;
            }

            String taskId = (String) eventNode.getProperties().get(HistoryModel.PROP_TASK_INSTANCE_ID);

            Map<String, Object> recordAttributes = getRecordAttributes(taskId, eventNode, showUnassignedInStatisticConfig, context);

            if (recordAttributes != null) {
                MetaMapValue record = new MetaMapValue(taskId);
                record.setAttributes(recordAttributes);
                records.add(record);
            }
        });

        RecordsQueryResult<MetaValue> result = new RecordsQueryResult<>();
        result.setHasMore(eventsSearchResult.getHasMore());
        result.setTotalCount(records.size());
        result.setRecords(records);

        return result;
    }

    private Map<String, Object> getRecordAttributes(String taskId,
                                                    GqlAlfNode eventNode,
                                                    Boolean showUnassignedInStatisticConfig,
                                                    AlfGqlContext context) {

        List<GqlAlfNode> taskEvents = getEvents(taskId, context);

        Optional<GqlAlfNode> startEvent = Optional.empty();
        Optional<GqlAlfNode> completeEvent = Optional.empty();
        Optional<GqlAlfNode> assignEvent = Optional.empty();

        for (GqlAlfNode node : taskEvents) {
            String name = (String) node.getProperties().get(HistoryModel.PROP_NAME);
            if (HistoryEventType.TASK_CREATE.equals(name)) {
                startEvent = Optional.of(node);
            } else if (showUnassignedInStatisticConfig && HistoryEventType.TASK_ASSIGN.equals(name)) {
                assignEvent = Optional.of(node);
            } else if (HistoryEventType.TASK_COMPLETE.equals(name)) {
                completeEvent = Optional.of(node);
            }
            if (startEvent.isPresent() && assignEvent.isPresent() && completeEvent.isPresent()) {
                break;
            }
        }

        return getRecord(startEvent.orElse(null),
                completeEvent.orElse(null),
                !showUnassignedInStatisticConfig ? eventNode : assignEvent.orElse(null),
                context);
    }

    private List<GqlAlfNode> getEvents(String taskId, AlfGqlContext context) {

        return FTSQuery.create()
                .value(HistoryModel.PROP_TASK_INSTANCE_ID, taskId)
                .query(searchService)
                .stream()
                .map(context::getNode)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private List<GqlAlfNode> mergeAssignEvents(List<GqlAlfNode> events) {

        List<GqlAlfNode> result = new ArrayList<>();
        Map<String, Integer> eventsIndex = new HashMap<>();

        for (GqlAlfNode assignEvent : events) {

            String taskId = (String) assignEvent.getProperties().get(HistoryModel.PROP_TASK_INSTANCE_ID);

            if (StringUtils.isNotBlank(taskId)) {

                Integer idx = eventsIndex.computeIfAbsent(taskId, id -> {
                    result.add(assignEvent);
                    return result.size() - 1;
                });

                GqlAlfNode existing = result.get(idx);
                if (existing != assignEvent) {
                    Date assignDate = (Date) assignEvent.getProperties().get(HistoryModel.PROP_DATE);
                    Date existingDate = (Date) existing.getProperties().get(HistoryModel.PROP_DATE);
                    if (existingDate == null || assignDate != null && assignDate.after(existingDate)) {
                        result.set(idx, assignEvent);
                    }
                }
            }
        }

        return result;
    }

    private List<GqlAlfNode> wrapNodes(List<NodeRef> nodeRefs, AlfGqlContext context) {
        return nodeRefs.stream()
                .map(context::getNode)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    private SearchResult<NodeRef> getEvents(RecordsQuery query, Boolean isAssign) {

        SearchCriteria criteria = criteriaParser.parse(query.getQuery());
        SearchCriteria processedCriteria = new SearchCriteria(namespaceService);

        String eventTypeProp = HistoryModel.PROP_NAME.toPrefixString(namespaceService);

        String eventType = isAssign ? HistoryEventType.TASK_ASSIGN : HistoryEventType.TASK_CREATE;
        processedCriteria.addCriteriaTriplet(eventTypeProp, PRED_STR_EQUALS, eventType);

        for (CriteriaTriplet triplet : criteria.getTriplets()) {

            String field = triplet.getField();
            String predicate = triplet.getPredicate();
            String value = triplet.getValue();

            QName fieldQName;
            try {
                fieldQName = QName.resolveToQName(namespaceService, triplet.getField());
            } catch (NamespaceException e) {
                logger.warn("Field " + triplet.getField() + " is not a valid QName. Ignore it");
                continue;
            }

            if (fieldQName.equals(HistoryModel.ASSOC_INITIATOR) && value != null && NodeRef.isNodeRef(value)) {
                NodeRef rootRef = new NodeRef(value);
                Set<String> authorities = authorityUtils.getContainedUsers(rootRef, false);
                if (!authorities.isEmpty()) {
                    value = authorityUtils.getNodeRefs(authorities)
                                          .stream()
                                          .map(Object::toString)
                                          .collect(Collectors.joining(","));
                }
            }

            processedCriteria.addCriteriaTriplet(field, predicate, value);
        }

        SearchParameters searchParameters = new SearchParameters();
        searchParameters.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
        searchParameters.setLanguage(SearchService.LANGUAGE_FTS_ALFRESCO);
        searchParameters.setMaxItems(query.getMaxItems() * 2);
        searchParameters.setSkipCount(query.getSkipCount());
        searchParameters.setQuery(queryBuilder.buildQuery(processedCriteria));

        String dateAttr = HistoryModel.PROP_DATE.toPrefixString(namespaceService);
        for (SortBy sortBy : query.getSortBy()) {
            if (sortBy.getAttribute().equals(dateAttr)) {
                searchParameters.addSort("@" + sortBy.getAttribute(), sortBy.isAscending());
            }
        }

        return searchUtils.query(searchParameters);
    }

    private Map<String, Object> getRecord(GqlAlfNode startEvent,
                                          GqlAlfNode endEvent,
                                          GqlAlfNode assignEvent,
                                          AlfGqlContext context) {

        if (startEvent == null) {
            return null;
        }

        Map<String, Object> recordAttributes = new HashMap<>();

        String documentAttrName = HistoryModel.ASSOC_DOCUMENT.toPrefixString(namespaceService);
        MetaValue docAttributeGql = getAssocAttribute(startEvent, documentAttrName, context);
        recordAttributes.put(documentAttrName, docAttributeGql);

        Map<QName, Serializable> startedProps = startEvent.getProperties();
        startedProps.forEach((k, v) -> {
            String key = k.toPrefixString(namespaceService);
            if (v instanceof QName) {
                AlfNodeAttValue value = new AlfNodeAttValue(v);
                value.init(context, null);
                recordAttributes.put(key, value);
            } else {
                recordAttributes.put(key, v);
            }
        });

        Date startDate = (Date) startedProps.get(HistoryModel.PROP_DATE);
        recordAttributes.put(STARTED_DATE, ISO8601Utils.format(startDate));

        Date dueDate = (Date) startedProps.get(HistoryModel.PROP_TASK_DUE_DATE);

        long expectedPerformTime = 0;
        if (dueDate != null) {
            long timeDiff = dueDate.getTime() - startDate.getTime();
            expectedPerformTime = Math.round(timeDiff / (1000f * 60 * 60));
            recordAttributes.put(EXPECTED_PERFORM_TIME, expectedPerformTime);
        }

        String initiatorAttrName = HistoryModel.ASSOC_INITIATOR.toPrefixString(namespaceService);

        if (assignEvent != null) {
            MetaValue initiator = getAssocAttribute(assignEvent, initiatorAttrName, context);
            if (initiator != null && initiator.getId() != null) {
                recordAttributes.put(initiatorAttrName, initiator);
            } else {
                recordAttributes.put(initiatorAttrName, getAssigneeFullName(assignEvent));
            }

        } else {
            List<NodeRef> pooledActors = (ArrayList<NodeRef>) startedProps.get(HistoryModel.PROP_TASK_POOLED_ACTORS);
            recordAttributes.put(initiatorAttrName, getPoolActorsNamesString(pooledActors));
        }

        if (endEvent != null) {
            Date completionDate = (Date) endEvent.getProperties().get(HistoryModel.PROP_DATE);
            recordAttributes.put(COMPLETION_DATE, ISO8601Utils.format(completionDate));
            long timeDiff = completionDate.getTime() - startDate.getTime();
            long actualPerformTime = Math.round(timeDiff / (1000f * 60 * 60));
            recordAttributes.put(ACTUAL_PERFORM_TIME, actualPerformTime);
            if (actualPerformTime > 0) {
                recordAttributes.put(PERFORM_TIME_RATIO, ((float) expectedPerformTime / actualPerformTime));
            } else {
                recordAttributes.put(PERFORM_TIME_RATIO, 1f);
            }
        }

        return recordAttributes;
    }

    private MetaValue getAssocAttribute(GqlAlfNode node, String key, AlfGqlContext context) {
        AlfNodeAttValue value = new AlfNodeAttValue(node.attribute(key)
                .node()
                .map(n -> new NodeRef(n.nodeRef()))
                .orElse(null));
        value.init(context, null);
        return value;
    }

    private String getPoolActorsNamesString(List<NodeRef> pooledActors) {
        List<String> poolActorsNames = new ArrayList<>();
        for (NodeRef pooledActor : pooledActors) {
            QName pooledActorType = nodeService.getType(pooledActor);
            String pooledActorName = "";
            if (pooledActorType.equals(ContentModel.TYPE_PERSON)) {
                pooledActorName = RepoUtils.getPersonFullName(pooledActor, nodeService);
            } else if (pooledActorType.equals(ContentModel.TYPE_AUTHORITY_CONTAINER)) {
                pooledActorName = StringUtils.defaultString((String) nodeService.getProperty(pooledActor,
                        ContentModel.PROP_AUTHORITY_DISPLAY_NAME), "");
            }
            poolActorsNames.add(pooledActorName);
        }
        return StringUtils.join(poolActorsNames, ", ");
    }

    private String getAssigneeFullName(GqlAlfNode assignEvent) {
        String creator = StringUtils.defaultString((String) assignEvent.getAttributeValue(
                ContentModel.PROP_CREATOR, Attribute.Type.PROP), "");
        return RepoUtils.getPersonFullName(creator, personService, nodeService);
    }

    private Boolean strToBool(String value, Boolean def) {
        return StringUtils.isNotBlank(value) ? !Boolean.FALSE.toString().equals(value) : def;
    }

    @Override
    public void setRecordsServiceFactory(RecordsServiceFactory serviceFactory) {
        recordsService = serviceFactory.getRecordsServiceV1();
        attSchemaWriter = serviceFactory.getAttSchemaWriter();
    }
}
