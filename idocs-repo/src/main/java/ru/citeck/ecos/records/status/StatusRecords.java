package ru.citeck.ecos.records.status;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roman Makarskiy
 */
@Component
public class StatusRecords extends LocalRecordsDao implements LocalRecordsMetaDao<StatusRecord>,
        LocalRecordsQueryWithMetaDao<StatusRecord> {

    private static final String ID = "status";

    private final StatusRecordsUtils statusRecordsUtils;

    {
        setId(ID);
    }

    @Autowired
    public StatusRecords(StatusRecordsUtils statusRecordsUtils) {
        this.statusRecordsUtils = statusRecordsUtils;
    }

    @Override
    public List<StatusRecord> getLocalRecordsMeta(List<RecordRef> records, MetaField metaField) {
        List<StatusRecord> result = new ArrayList<>();

        for (RecordRef recordRef : records) {
            String id = recordRef.getId();
            if (StringUtils.isBlank(id)) {
                result.add(new StatusRecord(new StatusDTO()));
                continue;
            }

            StatusDTO found = statusRecordsUtils.getByNameCaseOrDocumentStatus(id);
            result.add(new StatusRecord(found));
        }

        return result;
    }

    @Override
    public RecordsQueryResult<StatusRecord> queryLocalRecords(RecordsQuery recordsQuery, MetaField metaField) {
        StatusQuery query = recordsQuery.getQuery(StatusQuery.class);

        if (StringUtils.isNotBlank(query.getAllExisting())) {
            return statusRecordsUtils.getAllExistingStatuses(query.getAllExisting());
        }

        if (query.getAllAvailableToChange() != null) {
            return statusRecordsUtils.getAllAvailableToChangeStatuses(query.getAllAvailableToChange());
        }

        return statusRecordsUtils.getStatusByRecord(query.getRecord());
    }
}
