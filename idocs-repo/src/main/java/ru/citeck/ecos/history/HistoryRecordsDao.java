package ru.citeck.ecos.history;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.PersonService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records.meta.value.MetaJsonNodeValue;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;
import ru.citeck.ecos.webscripts.history.DocumentHistoryGet;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class HistoryRecordsDao extends LocalRecordsDao
    implements LocalRecordsQueryWithMetaDao<MetaValue> {

    private static final String ID = "history";
    private static final String LANGUAGE_DOCUMENT = "document";
    private static final String LANGUAGE_CRITERIA = "criteria";

    private DocumentHistoryGet historyGet;
    @Autowired
    private PersonService personService;

    public HistoryRecordsDao() {
        setId(ID);
    }

    @Override
    public RecordsQueryResult<MetaValue> queryLocalRecords(RecordsQuery query, MetaField metaField) {

        String language = query.getLanguage();
        List<ObjectNode> events;

        if (StringUtils.isBlank(language)) {
            language = LANGUAGE_DOCUMENT;
        }
        if (!LANGUAGE_DOCUMENT.equals(language) && !LANGUAGE_CRITERIA.equals(language)) {
            throw new IllegalArgumentException("Language '" + language + "' is not supported!");
        }

        if (LANGUAGE_DOCUMENT.equals(language)) {
            Query queryData = query.getQuery(Query.class);

            String nodeRef = queryData.nodeRef;
            if (nodeRef != null) {
                int idx = nodeRef.lastIndexOf('@');
                if (nodeRef.contains("people")){
                    nodeRef = personService.getPerson(nodeRef.substring(idx + 1)).toString();
                } else if (idx > -1 && idx < nodeRef.length() - 1){
                    nodeRef = nodeRef.substring(idx + 1);
                }
            }
            if (!NodeRef.isNodeRef(nodeRef)) {
                events = Collections.emptyList();
            } else {
                events = historyGet.getHistoryEvents(nodeRef,
                    queryData.filter,
                    queryData.events,
                    queryData.taskTypes);
            }
        } else {
            int skipCount = query.getSkipCount();
            int maxItems = query.getMaxItems();
            int page = skipCount / maxItems;

            events = historyGet.getAllHistoryEvents(page, maxItems, null, null, null);
        }

        RecordsQueryResult<MetaValue> result = new RecordsQueryResult<>();
        result.setHasMore(false);
        result.setTotalCount(events.size());
        result.setRecords(getEventsMetaValues(events));

        return result;
    }

    private List<MetaValue> getEventsMetaValues(List<ObjectNode> events) {
        return events.stream()
            .map(e -> new MetaJsonNodeValue(e.get("nodeRef").asText(), e.get("attributes")))
            .collect(Collectors.toList());
    }

    @Autowired
    public void setHistoryGet(DocumentHistoryGet historyGet) {
        this.historyGet = historyGet;
    }

    public static class Query {
        public String nodeRef;
        public String filter;
        public String events;
        public String taskTypes;
    }
}
