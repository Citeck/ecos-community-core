package ru.citeck.ecos.journals.webscripts;

import ecos.com.fasterxml.jackson210.databind.JsonNode;
import ecos.com.fasterxml.jackson210.databind.node.JsonNodeFactory;
import ecos.com.fasterxml.jackson210.databind.node.ObjectNode;
import ecos.com.fasterxml.jackson210.databind.node.TextNode;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.alfresco.repo.i18n.MessageService;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.*;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.TemplateService;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.extensions.webscripts.*;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.journals.*;
import ru.citeck.ecos.model.JournalsModel;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.Predicate;
import ru.citeck.ecos.records2.predicate.model.ValuePredicate;
import ru.citeck.ecos.records2.querylang.QueryLangService;
import ru.citeck.ecos.records.source.alf.AlfDictionaryRecords;
import ru.citeck.ecos.records.source.alf.search.CriteriaAlfNodesSearch;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.search.SearchCriteria;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.server.utils.Utils;
import ru.citeck.ecos.utils.NodeUtils;
import ru.citeck.ecos.utils.RepoUtils;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author Pavel Simonov
 */
public class JournalConfigGet extends AbstractWebScript {

    private static final Log logger = LogFactory.getLog(JournalConfigGet.class);

    private static final String PARAM_JOURNAL = "journalId";

    private static final String META_RECORD_TEMPLATE = AlfDictionaryRecords.ID + "@%s";

    private LoadingCache<String, JournalRef> journalRefById;
    private LoadingCache<JournalRef, JournalRepoData> repoDataByJournalRef;

    private NodeUtils nodeUtils;
    private NodeService nodeService;
    private SearchService searchService;
    private MessageService messageService;
    private RecordsService recordsService;
    private QueryLangService queryLangService;
    private JournalService journalService;
    private ServiceRegistry serviceRegistry;
    private TemplateService templateService;
    private NamespaceService namespaceService;
    private DictionaryService dictionaryService;
    private CreateVariantsGet createVariantsGet;

    public JournalConfigGet() {
        journalRefById = CacheBuilder.newBuilder()
                .expireAfterWrite(600, TimeUnit.SECONDS)
                .maximumSize(200)
                .build(CacheLoader.from(this::findJournalRef));
        repoDataByJournalRef = CacheBuilder.newBuilder()
                .expireAfterWrite(600, TimeUnit.SECONDS)
                .maximumSize(200)
                .build(CacheLoader.from(this::getJournalRepoData));
    }

    @Override
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException {

        res.setContentType(Format.JSON.mimetype() + ";charset=UTF-8");

        String journalId = req.getParameter(PARAM_JOURNAL);
        Response response = executeImpl(journalId);

        Json.getMapper().write(res.getWriter(), response);
        res.setStatus(Status.STATUS_OK);
    }

    private Response executeImpl(String journalId) {

        if (StringUtils.isBlank(journalId)) {
            throw new WebScriptException(Status.STATUS_NOT_FOUND, "journalId is a mandatory parameter!");
        }

        JournalType journalType = journalService.getJournalType(journalId);
        NodeRef journalRef = NodeRef.isNodeRef(journalId) ? new NodeRef(journalId) : null;

        if (journalType == null) {
            throw new WebScriptException(Status.STATUS_NOT_FOUND, "Journal with id '" + journalId + "' is not found!");
        }

        List<String> attributes = journalType.getAttributes();
        List<Column> columns = new ArrayList<>();
        String sourceId = journalType.getDataSource();
        if (sourceId == null) {
            sourceId = "";
        }

        Map<String, String> options = journalType.getOptions();
        String type = MapUtils.getString(options, "type");

        JournalMeta journalMeta = getJournalMeta(journalType, type, journalRef);
        Map<String, AttInfo> columnInfo = getAttributesInfo(journalMeta.getMetaRecord(), attributes);
        journalMeta.setGroupActions(getGroupActions(journalType, columnInfo));

        for (String name : attributes) {

            Column column = new Column();

            column.setFormatter(getFormatter(journalType.getFormatter(name)));
            column.setDefault(journalType.isAttributeDefault(name));
            column.setGroupable(journalType.isAttributeGroupable(name));
            column.setSearchable(journalType.isAttributeSearchable(name));
            column.setSortable(journalType.isAttributeSortable(name));
            column.setVisible(journalType.isAttributeVisible(name));
            column.setAttribute(name);
            column.setParams(journalType.getAttributeOptions(name));
            column.setText(getColumnLabel(column));

            AttInfo info = columnInfo.get(name);
            column.setJavaClass(info.getJavaClass() != null ? info.getJavaClass().getName() : null);
            column.setEditorKey(info.getEditorKey());
            column.setType(info.getType());
            column.setEditable(!Boolean.TRUE.equals(info.notEditable));

            if (column.getParams() != null) {
                String schema = column.getParams().get("schema");
                if (StringUtils.isNotBlank(schema)) {
                    column.setSchema(schema);
                }
                String editable = column.getParams().get("editable");
                if (Boolean.FALSE.toString().equals(editable)) {
                    column.setEditable(false);
                }
            }

            columns.add(column);
        }

        Response response = new Response();
        response.setId(journalType.getId());
        response.setColumns(columns);
        response.setMeta(journalMeta);
        response.setSourceId(sourceId);
        response.setParams(journalType.getOptions());

        return response;
    }

    private Formatter getFormatter(JournalFormatter formatter) {

        if (formatter == null) {
            return null;
        }

        Formatter result = new Formatter();
        result.setName(formatter.getName());

        Map<String, String> journalParams = formatter.getParams();

        if (journalParams != null) {

            ObjectNode params = JsonNodeFactory.instance.objectNode();

            journalParams.forEach((k, v) -> {

                String value = v.trim();

                if (value.startsWith("{") || value.startsWith("[")) {
                    params.put(k, Json.getMapper().read(value));
                } else {
                    params.put(k, TextNode.valueOf(value));
                }
            });

            result.setParams(params);
        }

        return result;
    }

    private String getColumnLabel(Column column) {

        Map<String, String> params = column.getParams();
        if (params != null) {
            String custom = params.get("customLabel");
            if (custom != null) {
                String label = I18NUtil.getMessage(custom);
                return label != null ? label : custom;
            }
        }

        if (column.getAttribute().contains(":")) {

            QName attQName = QName.resolveToQName(namespaceService, column.getAttribute());

            if (attQName != null) {

                ClassAttributeDefinition attDef = dictionaryService.getProperty(attQName);

                if (attDef == null) {
                    attDef = dictionaryService.getAssociation(attQName);
                }

                if (attDef != null) {

                    String title = attDef.getTitle(messageService);
                    if (StringUtils.isNotBlank(title)) {
                        return title;
                    }
                }
            }
        }

        return column.getAttribute();
    }

    private JournalMeta getJournalMeta(JournalType journal, String type, NodeRef journalNodeRef) {

        JournalRef journalRef;
        if (journalNodeRef == null) {
            journalRef = journalRefById.getUnchecked(journal.getId());
        } else {
            journalRef = new JournalRef(journalNodeRef);
        }

        JournalRepoData journalData = repoDataByJournalRef.getUnchecked(journalRef);
        JournalMeta meta = new JournalMeta();

        meta.setActions(journal.getActions());

        if (StringUtils.isNotBlank(journal.getGroupBy())) {
            meta.setGroupBy(Json.getMapper().read(journal.getGroupBy()));
        }

        if (StringUtils.isNotBlank(journal.getPredicate())) {
            meta.setPredicate(Json.getMapper().read(journal.getPredicate()));
        }
        meta.setCreateVariants(createVariantsGet.getVariantsByJournalRef(journalData.getNodeRef(), true));

        fillMetaFromRepo(meta, journalData);

        if (meta.getPredicate() == null && StringUtils.isNotBlank(type)) {
            Predicate predicate = ValuePredicate.equal("TYPE", type);
            meta.setPredicate(Json.getMapper().convert(predicate, JsonNode.class));
        }

        Map<String, String> options = journal.getOptions();
        if (options == null) {
            options = Collections.emptyMap();
        }

        String metaRecord = options.get("metaRecord");

        if (StringUtils.isNotBlank(metaRecord)) {

            meta.setMetaRecord(metaRecord);

        } else if (StringUtils.isNotBlank(journal.getDataSource())) {

            meta.setMetaRecord(journal.getDataSource() + "@");

        } else if (StringUtils.isNotBlank(type)) {

            meta.setMetaRecord(String.format(META_RECORD_TEMPLATE, type));
        }

        return meta;
    }

    private void fillMetaFromRepo(JournalMeta meta, JournalRepoData journalData) {

        if (journalData.getNodeRef() == null) {
            return;
        }

        NodeRef journalRef = journalData.getNodeRef();
        meta.setNodeRef(String.valueOf(journalRef));
        meta.setTitle(nodeUtils.getDisplayName(journalRef));

        List<Criterion> criteriaList = new ArrayList<>();
        SearchCriteria criteria = new SearchCriteria(namespaceService);

        for (NodeRef criterionRef : journalData.getCriteria()) {

            Map<QName, Serializable> props = nodeService.getProperties(criterionRef);

            Criterion criterion = new Criterion();
            criterion.setField(toPrefix((QName) props.get(JournalsModel.PROP_FIELD_QNAME)));
            criterion.setPredicate((String) props.get(JournalsModel.PROP_PREDICATE));
            criterion.setValue(getCriterionValue((String) props.get(JournalsModel.PROP_CRITERION_VALUE)));

            criteria.addCriteriaTriplet(criterion.getField(), criterion.getPredicate(), criterion.getValue());

            criteriaList.add(criterion);
        }

        meta.setCriteria(criteriaList);

        if (meta.getPredicate() == null) {

            JsonNode criteriaJson = Json.getMapper().toJson(criteria);
            try {
                Object convertedQuery = queryLangService.convertLang(criteriaJson,
                        CriteriaAlfNodesSearch.LANGUAGE,
                        PredicateService.LANGUAGE_PREDICATE)
                        .orElseThrow(RuntimeException::new);
                meta.setPredicate(Json.getMapper().convert(convertedQuery, JsonNode.class));
            } catch (Exception e) {
                logger.error("Language conversion error. criteria: " + criteriaJson, e);
            }
        }
    }

    private List<GroupAction> getGroupActions(JournalType type, Map<String, AttInfo> columnInfo) {

        List<GroupAction> resultActions = new ArrayList<>();

        for (JournalGroupAction groupAction : type.getGroupActions()) {

            GroupAction action = new GroupAction();
            action.setId(groupAction.getId());
            action.setTitle(groupAction.getTitle());
            action.setParams(groupAction.getOptions());
            action.setType(groupAction.getType());
            if (groupAction.getViewClass() != null) {
                action.setFormKey("alf_" + groupAction.getViewClass());
            }

            resultActions.add(action);
        }

        for (String attribute : type.getAttributes()) {

            List<JournalBatchEdit> batchEdits = type.getBatchEdit(attribute);
            AttInfo attInfo = columnInfo.get(attribute);
            if (attInfo == null) {
                attInfo = new AttInfo();
                attInfo.setType("text");
                attInfo.setJavaClass(String.class);
            }

            for (JournalBatchEdit batchEdit : batchEdits) {

                GroupAction action = new GroupAction();
                action.setTitle(batchEdit.getTitle());

                Map<String, String> params = new HashMap<>(batchEdit.getOptions());
                params.put("form_option_batch-edit-attribute", attribute);

                action.setParams(params);
                action.setId("records-mutation");
                action.setType("selected");
                action.setFormKey("batch-edit-" + attInfo.getType());

                resultActions.add(action);
            }
        }

        return resultActions;
    }

    private String getCriterionValue(String value) {

        if (StringUtils.isBlank(value)) {
            return value;
        }

        String template = Utils.restoreFreemarkerVariables(value);
        if (Objects.equals(value, template) && !value.contains("<#")) {
            return value;
        }

        Map<String, Object> model = RepoUtils.buildDefaultModel(serviceRegistry);
        return templateService.processTemplateString("freemarker", template, model);
    }

    private JournalRepoData getJournalRepoData(JournalRef journalRef) {

        JournalRepoData repoData = new JournalRepoData();

        NodeRef nodeRef = journalRef.getNodeRef();
        repoData.setNodeRef(nodeRef);
        if (nodeRef != null) {
            repoData.setCriteria(nodeUtils.getAssocTargets(nodeRef, JournalsModel.ASSOC_SEARCH_CRITERIA));
        }

        return repoData;
    }

    private JournalRef findJournalRef(String journalId) {
        return new JournalRef(
                FTSQuery.create()
                        .type(JournalsModel.TYPE_JOURNAL).and()
                        .exact(JournalsModel.PROP_JOURNAL_TYPE, journalId)
                        .transactional()
                        .queryOne(searchService)
                        .orElse(null)
        );
    }

    private Map<String, AttInfo> getAttributesInfo(String metaRecord, List<String> attributes) {

        Map<String, String> attributesEdges = new HashMap<>();
        for (String attribute : attributes) {
            attributesEdges.put(attribute, ".edge(n:\"" + attribute + "\"){type,editorKey," +
                                                                           "javaClass,notEditable:protected}");
        }

        RecordRef metaRecordRef;
        if (StringUtils.isBlank(metaRecord)) {
            metaRecordRef = RecordRef.create(AlfDictionaryRecords.ID, metaRecord);
        } else {
            metaRecordRef = RecordRef.valueOf(metaRecord);
        }

        RecordMeta attInfoMeta = recordsService.getAttributes(metaRecordRef, attributesEdges);

        Map<String, AttInfo> result = new HashMap<>();

        for (String attribute : attributes) {

            AttInfo info = null;

            DataValue attInfoNode = attInfoMeta.get(attribute);
            if (attInfoNode.isObject()) {
                try {
                    info = Json.getMapper().convert(attInfoNode, AttInfo.class);
                } catch (Exception e) {
                    logger.warn("Error", e);
                }
            }

            result.put(attribute, info != null ? info : new AttInfo());
        }

        return result;
    }

    private String toPrefix(QName name) {
        return name.toPrefixString(namespaceService);
    }

    @Autowired
    public void setCreateVariantsGet(CreateVariantsGet createVariantsGet) {
        this.createVariantsGet = createVariantsGet;
    }

    @Autowired
    public void setNodeUtils(NodeUtils nodeUtils) {
        this.nodeUtils = nodeUtils;
    }

    @Autowired
    public void setJournalService(JournalService journalService) {
        this.journalService = journalService;
    }

    @Autowired
    public void setRecordsService(RecordsService recordsService) {
        this.recordsService = recordsService;
    }

    @Autowired
    public void setQueryLangService(QueryLangService queryLangService) {
        this.queryLangService = queryLangService;
    }

    @Autowired
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
        this.nodeService = serviceRegistry.getNodeService();
        this.searchService = serviceRegistry.getSearchService();
        this.messageService = serviceRegistry.getMessageService();
        this.templateService = serviceRegistry.getTemplateService();
        this.namespaceService = serviceRegistry.getNamespaceService();
        this.dictionaryService = serviceRegistry.getDictionaryService();
    }

    @Data
    static class JournalRepoData {
        NodeRef nodeRef;
        List<NodeRef> criteria;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    static class JournalRef {
        NodeRef nodeRef;
    }

    @Data
    static class Response {
        String id;
        String sourceId;
        JournalMeta meta;
        List<Column> columns;
        Map<String, String> params;
    }

    @Data
    static class JournalMeta {
        String nodeRef;
        List<Criterion> criteria;
        String title;
        JsonNode predicate;
        JsonNode groupBy;
        String metaRecord;
        List<CreateVariantsGet.ResponseVariant> createVariants;
        List<RecordRef> actions;
        List<GroupAction> groupActions;
    }

    @Data
    static class GroupAction {
        String id;
        String title;
        Map<String, String> params;
        String type;
        String formKey;
    }

    @Data
    static class Criterion {
        String field;
        String predicate;
        String value;
    }

    @Data
    static class Column {
        String text;
        String type;
        String editorKey;
        String javaClass;
        String attribute;
        String schema;
        Formatter formatter;
        Map<String, String> params;
        boolean isDefault;
        boolean isSearchable;
        boolean isSortable;
        boolean isVisible;
        boolean isGroupable;
        boolean isEditable;
    }

    @Data
    static class Formatter {
        String name;
        ObjectNode params;
    }

    @Data
    static class AttInfo {
        String type;
        String editorKey;
        Class<?> javaClass;
        Boolean notEditable;
    }
}
