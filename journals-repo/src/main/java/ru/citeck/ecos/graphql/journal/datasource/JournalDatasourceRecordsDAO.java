package ru.citeck.ecos.graphql.journal.datasource;

import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.graphql.AlfGqlContext;
import ru.citeck.ecos.graphql.journal.JGqlPageInfoInput;
import ru.citeck.ecos.graphql.journal.JGqlSortBy;
import ru.citeck.ecos.graphql.journal.record.JGqlRecordsConnection;
import ru.citeck.ecos.records2.QueryContext;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.meta.RecordsMetaService;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.request.query.SortBy;
import ru.citeck.ecos.records2.source.dao.AbstractRecordsDao;
import ru.citeck.ecos.records2.source.dao.RecordsQueryWithMetaDao;
import ru.citeck.ecos.search.SortOrder;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * This records DAO required for backward compatibility. Don't use it for new data sources
 *
 * @deprecated implement RecordsDao instead
 */
public class JournalDatasourceRecordsDAO extends AbstractRecordsDao implements RecordsQueryWithMetaDao {

    private ServiceRegistry serviceRegistry;
    private JournalDataSource dataSource;
    private RecordsMetaService recordsMetaService;

    @PostConstruct
    public void init() {
        Object datasouce = serviceRegistry.getService(QName.createQName("", getId()));
        if (datasouce == null) {
            throw new IllegalStateException("Datasource " + getId() + " is not found!");
        }
        if (datasouce instanceof JournalDataSource) {
            dataSource = (JournalDataSource) datasouce;
        } else {
            String typeName = datasouce.getClass().getName();
            throw new IllegalStateException("Datasource bean \"" + getId() +
                                            "\" has incorrect type. Class: " + typeName);
        }
    }

    @Override
    public RecordsQueryResult<RecordMeta> queryRecords(RecordsQuery query, String metaSchema) {

        List<JGqlSortBy> sortBy = new ArrayList<>();
        for (SortBy sort : query.getSortBy()) {
            String order = (sort.isAscending() ? SortOrder.ASCENDING : SortOrder.DESCENDING).getValue();
            sortBy.add(new JGqlSortBy(sort.getAttribute(), order));
        }

        RecordRef afterId = query.getAfterId();
        JGqlPageInfoInput pageInfo = new JGqlPageInfoInput(afterId != RecordRef.EMPTY ? afterId.getId() : null,
                                                           query.getMaxItems(),
                                                           sortBy,
                                                           query.getSkipCount());

        RecordsQueryResult<RecordMeta> result = new RecordsQueryResult<>();

        AlfGqlContext gqlContext = QueryContext.getCurrent();
        JGqlRecordsConnection records = dataSource.getRecords(gqlContext,
                                                              query.getQuery(DataValue.class).asText(),
                                                              query.getLanguage(),
                                                              pageInfo);

        result.setTotalCount(records.totalCount());
        result.setHasMore(records.pageInfo().isHasNextPage());
        result.merge(recordsMetaService.getMeta(records.records(), metaSchema));

        return result;
    }

    @Autowired
    public void setRecordsMetaService(RecordsMetaService recordsMetaService) {
        this.recordsMetaService = recordsMetaService;
    }

    @Autowired
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }
}
