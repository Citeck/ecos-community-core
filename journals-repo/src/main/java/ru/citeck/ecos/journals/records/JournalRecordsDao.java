package ru.citeck.ecos.journals.records;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.graphql.journal.JGqlPageInfoInput;
import ru.citeck.ecos.journals.JournalType;
import ru.citeck.ecos.records.source.alf.search.CriteriaAlfNodesSearch;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.request.query.QueryConsistency;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.request.query.SortBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class JournalRecordsDao {

    private GqlQueryGenerator gqlQueryGenerator;
    private RecordsService recordsService;

    public RecordsQueryResult<RecordMeta> getRecordsWithData(JournalType journalType,
                                                             String query,
                                                             String language,
                                                             JGqlPageInfoInput pageInfo,
                                                             boolean debug) {

        RecordsQuery recordsQuery = createQuery(journalType.getDataSource(), query, language, pageInfo, debug);
        Map<String, String> attsToLoad = new LinkedHashMap<>();
        for (String att : journalType.getAttributes()) {
            attsToLoad.put(att, att);
        }
        attsToLoad.put("attr:aspects", "attr:aspects[]?str");
        return recordsService.queryRecords(recordsQuery, attsToLoad);
    }

    public String getJournalGqlSchema(JournalType type) {
        return gqlQueryGenerator.generate(type);
    }

    public RecordsQueryResult<RecordRef> getRecords(JournalType journalType,
                                                    String query,
                                                    String language,
                                                    JGqlPageInfoInput pageInfo,
                                                    boolean debug) {

        RecordsQuery recordsQuery = createQuery(journalType.getDataSource(), query, language, pageInfo, debug);
        return recordsService.queryRecords(recordsQuery);
    }

    public RecordsQuery createQuery(String sourceId,
                                    String query,
                                    String language,
                                    JGqlPageInfoInput pageInfo,
                                    boolean debug) {

        RecordsQuery recordsQuery = new RecordsQuery();
        recordsQuery.setQuery(query);
        if (StringUtils.isBlank(language)) {
            recordsQuery.setLanguage(CriteriaAlfNodesSearch.LANGUAGE);
        } else {
            recordsQuery.setLanguage(language);
        }
        recordsQuery.setMaxItems(pageInfo.getMaxItems());
        recordsQuery.setSortBy(pageInfo.getSortBy()
                                       .stream()
                                       .map(sort -> new SortBy(sort.getAttribute(), sort.isAscending()))
                                       .collect(Collectors.toList()));
        recordsQuery.setSkipCount(pageInfo.getSkipCount());
        recordsQuery.setConsistency(QueryConsistency.EVENTUAL);
        recordsQuery.setSourceId(sourceId);
        recordsQuery.setDebug(debug);

        return recordsQuery;
    }

    public void clearCache() {
        gqlQueryGenerator.clearCache();
    }

    public void setGqlQueryGenerator(GqlQueryGenerator gqlQueryGenerator) {
        this.gqlQueryGenerator = gqlQueryGenerator;
    }

    @Autowired
    public void setRecordsService(RecordsService recordsService) {
        this.recordsService = recordsService;
    }
}
