package ru.citeck.ecos.records.status;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao;
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao;
import ru.citeck.ecos.records3.record.dao.query.RecordsQueryDao;
import ru.citeck.ecos.records3.record.dao.query.dto.query.RecordsQuery;
import ru.citeck.ecos.records3.record.dao.query.dto.res.RecsQueryRes;

/**
 * @author Roman Makarskiy
 */
@Component
public class StatusRecords extends AbstractRecordsDao implements RecordAttsDao, RecordsQueryDao {

    public static final String ID = "status";

    private final StatusRecordsUtils statusRecordsUtils;

    @Autowired
    public StatusRecords(StatusRecordsUtils statusRecordsUtils) {
        this.statusRecordsUtils = statusRecordsUtils;
    }

    @Nullable
    @Override
    public Object getRecordAtts(@NotNull String recordId) {
        if (StringUtils.isBlank(recordId)) {
            return new StatusRecord(new StatusDto());
        }

        StatusDto found = statusRecordsUtils.getStatusById(recordId);
        return new StatusRecord(found);
    }

    @Nullable
    @Override
    public RecsQueryRes<StatusRecord> queryRecords(@NotNull RecordsQuery recordsQuery) {
        StatusQuery query = recordsQuery.getQuery(StatusQuery.class);

        if (StringUtils.isNotBlank(query.getAllExisting())) {
            return statusRecordsUtils.getAllExistingStatuses(query.getAllExisting());
        }

        if (query.getAllAvailableToChange() != null) {
            return statusRecordsUtils.getAllAvailableToChangeStatuses(query.getAllAvailableToChange());
        }

        return statusRecordsUtils.getStatusByRecord(query.getRecord());
    }

    @NotNull
    @Override
    public String getId() {
        return ID;
    }
}
