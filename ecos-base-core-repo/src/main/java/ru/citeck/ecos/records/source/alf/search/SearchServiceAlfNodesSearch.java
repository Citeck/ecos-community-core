package ru.citeck.ecos.records.source.alf.search;

import com.fasterxml.jackson.databind.util.ISO8601Utils;
import lombok.val;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.QueryConsistency;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.domain.model.alf.service.AlfAutoModelService;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records.source.alf.search.AlfNodesSearch.AfterIdType;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.request.query.SortBy;
import ru.citeck.ecos.records3.record.request.RequestContext;
import ru.citeck.ecos.records3.record.request.msg.MsgLevel;
import ru.citeck.ecos.utils.AuthorityUtils;
import ru.citeck.ecos.utils.PrefixRecordRefUtils;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class SearchServiceAlfNodesSearch {

    public static final String ECOS_PARAMS_DELIM = "__?";
    public static final String ECOS_TYPE_DELIM = ECOS_PARAMS_DELIM + "_type=";
    private static final String ECOS_TYPE_DELIM_REGEXP = Pattern.quote(ECOS_TYPE_DELIM);

    private static final Log logger = LogFactory.getLog(SearchServiceAlfNodesSearch.class);

    private static final String FROM_DB_ID_FTS_QUERY = "(%s) AND @sys\\:node\\-dbid:<%d TO MAX]";

    private static final Map<String, String> DEFAULT_PROPS_MAPPING;

    static {
        Map<String, String> defaultPropsMapping = new HashMap<>();
        defaultPropsMapping.put(RecordConstants.ATT_CREATED, "cm:created");
        defaultPropsMapping.put(RecordConstants.ATT_CREATOR, "cm:creator");
        defaultPropsMapping.put(RecordConstants.ATT_MODIFIED, "cm:modified");
        defaultPropsMapping.put(RecordConstants.ATT_MODIFIER, "cm:modifier");
        DEFAULT_PROPS_MAPPING = Collections.unmodifiableMap(defaultPropsMapping);
    }

    private SearchService searchService;
    private NamespaceService namespaceService;
    private AlfAutoModelService alfAutoModelService;
    private AuthorityUtils authorityUtils;

    @Autowired
    public SearchServiceAlfNodesSearch(SearchService searchService,
                                       AlfNodesRecordsDAO recordsSource,
                                       NamespaceService namespaceService,
                                       AuthorityUtils authorityUtils) {

        this.searchService = searchService;
        this.namespaceService = namespaceService;
        this.authorityUtils = authorityUtils;

        recordsSource.register(new SearchWithLanguage(SearchService.LANGUAGE_FTS_ALFRESCO, AfterIdType.DB_ID));
        recordsSource.register(new SearchWithLanguage(SearchService.LANGUAGE_LUCENE, AfterIdType.DB_ID));
        recordsSource.register(new SearchWithLanguage(SearchService.LANGUAGE_SOLR_ALFRESCO, AfterIdType.DB_ID));
        recordsSource.register(new SearchWithLanguage(SearchService.LANGUAGE_SOLR_FTS_ALFRESCO, AfterIdType.DB_ID));
        recordsSource.register(new SearchWithLanguage(SearchService.LANGUAGE_SOLR_CMIS, AfterIdType.CREATED));
        recordsSource.register(new SearchWithLanguage(SearchService.LANGUAGE_CMIS_ALFRESCO, AfterIdType.CREATED));
        recordsSource.register(new SearchWithLanguage(SearchService.LANGUAGE_CMIS_STRICT, AfterIdType.CREATED));
        recordsSource.register(new SearchWithLanguage(SearchService.LANGUAGE_XPATH));
    }

    private RecordsQueryResult<RecordRef> queryRecordsImpl(RecordsQuery recordsQuery, Long afterDbId, Date afterCreated) {

        val reqCtx = RequestContext.getCurrent();

        String[] queryWithType = recordsQuery.getQuery(DataValue.class)
            .asText()
            .split(ECOS_TYPE_DELIM_REGEXP);

        String query = queryWithType[0];
        RecordRef ecosTypeRef = RecordRef.valueOf(queryWithType.length > 1 ? queryWithType[1] : null);

        SearchParameters searchParameters = new SearchParameters();
        searchParameters.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
        searchParameters.setMaxItems(recordsQuery.getMaxItems());
        searchParameters.setBulkFetchEnabled(false);
        searchParameters.setLanguage(recordsQuery.getLanguage());

        String consistency = recordsQuery.getConsistency().name();
        searchParameters.setQueryConsistency(QueryConsistency.valueOf(consistency));

        boolean afterIdMode = false;
        String afterIdSortField = "";
        boolean ignoreQuerySort = false;

        if (afterDbId != null) {

            query = String.format(FROM_DB_ID_FTS_QUERY, query, afterDbId);
            afterIdSortField = "@" + ContentModel.PROP_NODE_DBID.toPrefixString(namespaceService);

            searchParameters.addSort(afterIdSortField, true);

            afterIdMode = true;

        } else if (afterCreated != null && recordsQuery.getLanguage().startsWith("cmis-")) {

            query = query.replaceAll("(?i)order by.+", "");
            if (!query.contains("where") && !query.contains("WHERE")) {
                query += " where";
            } else {
                query += " and";
            }
            query += " cmis:creationDate > '" + ISO8601Utils.format(afterCreated) + "' order by cmis:creationDate";
            ignoreQuerySort = true;

        } else {
            searchParameters.setSkipCount(recordsQuery.getSkipCount());
        }

        val finalQuery = query;
        addDebugMsg(reqCtx, () -> "Query: " + finalQuery);

        if ("()".equals(query) || StringUtils.isBlank(query))  {
            return new RecordsQueryResult<>();
        }

        query = query.replace("alfresco/@workspace://", "workspace://");
        query = PrefixRecordRefUtils.replaceAuthorityNodes(query, authorityUtils);

        searchParameters.setQuery(query);

        if (!ignoreQuerySort) {

            Map<String, String> propsMapping = DEFAULT_PROPS_MAPPING;
            if (alfAutoModelService != null) {
                Map<String, String> autoModelPropsMapping = alfAutoModelService.getPropsMapping(ecosTypeRef);
                if (autoModelPropsMapping != null && !autoModelPropsMapping.isEmpty()) {
                    propsMapping = new HashMap<>(propsMapping);
                    propsMapping.putAll(autoModelPropsMapping);
                }
            }
            for (SortBy sortBy : recordsQuery.getSortBy()) {
                String att = sortBy.getAttribute();
                String field = "@" + propsMapping.getOrDefault(att, att);
                if (!afterIdMode || !afterIdSortField.equals(field)) {
                    searchParameters.addSort(field, sortBy.isAscending());
                }
            }
        }

        ResultSet resultSet = null;
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Execute query with parameters: " + searchParameters);
            }
            resultSet = searchService.query(searchParameters);

            RecordsQueryResult<RecordRef> result = new RecordsQueryResult<>();
            result.setRecords(resultSet.getNodeRefs()
                                       .stream()
                                       .map(r -> RecordRef.valueOf(r.toString()))
                                       .collect(Collectors.toList()));
            result.setHasMore(resultSet.hasMore());
            result.setTotalCount(resultSet.getNumberFound());

            return result;

        } catch (Exception e) {
            throw new AlfrescoRuntimeException("Nodes search failed. Query: '" + recordsQuery + "'", e);
        } finally {
            if (resultSet != null) {
                resultSet.close();
            }
        }
    }

    private void addDebugMsg(RequestContext reqCtx, Supplier<String> msg) {
        if (reqCtx != null && reqCtx.isMsgEnabled(MsgLevel.DEBUG)) {
            reqCtx.addMsg(MsgLevel.DEBUG, msg.get());
        }
    }

    @Autowired(required = false)
    public void setAlfAutoModelService(AlfAutoModelService alfAutoModelService) {
        this.alfAutoModelService = alfAutoModelService;
    }

    private class SearchWithLanguage implements AlfNodesSearch {

        private final String language;
        private final AfterIdType afterIdType;

        SearchWithLanguage(String language, AfterIdType afterIdType) {
            this.language = language;
            this.afterIdType = afterIdType;
        }

        SearchWithLanguage(String language) {
            this(language, null);
        }

        @Override
        public RecordsQueryResult<RecordRef> queryRecords(RecordsQuery query, Long afterDbId, Date afterCreated) {
            return queryRecordsImpl(query, afterDbId, afterCreated);
        }

        @Override
        public AfterIdType getAfterIdType() {
            return afterIdType;
        }

        @Override
        public String getLanguage() {
            return language;
        }
    }
}
