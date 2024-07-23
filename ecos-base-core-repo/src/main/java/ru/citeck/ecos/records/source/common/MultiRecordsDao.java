package ru.citeck.ecos.records.source.common;

import ru.citeck.ecos.action.group.ActionResult;
import ru.citeck.ecos.action.group.ActionResults;
import ru.citeck.ecos.action.group.ActionStatus;
import ru.citeck.ecos.action.group.GroupActionConfig;
import ru.citeck.ecos.records.RecordsUtils;
import ru.citeck.ecos.records.source.dao.RecordsActionExecutor;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.AbstractRecordsDao;
import ru.citeck.ecos.records2.source.dao.RecordsQueryDao;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Pavel Simonov
 */
public class MultiRecordsDao extends AbstractRecordsDao
                             implements RecordsQueryDao,
                                        RecordsActionExecutor {

    private List<RecordsQueryDao> recordsDao;
    private final Map<String, RecordsQueryDao> daoBySource = new ConcurrentHashMap<>();

    @Override
    public RecordsQueryResult<EntityRef> queryRecords(RecordsQuery query) {

        RecordsQueryResult<EntityRef> result = new RecordsQueryResult<>();

        RecordsQuery localQuery = new RecordsQuery(query);

        int sourceIdx = 0;
        EntityRef afterId = localQuery.getAfterId();
        if (afterId != EntityRef.EMPTY) {
            String source = afterId.getSourceId();
            while (sourceIdx < recordsDao.size() && !recordsDao.get(sourceIdx).getId().equals(source)) {
                sourceIdx++;
            }
        }

        while (sourceIdx < recordsDao.size() && result.getRecords().size() < query.getMaxItems()) {

            localQuery.setMaxItems(query.getMaxItems() - result.getRecords().size());
            RecordsQueryDao recordsDao = this.recordsDao.get(sourceIdx);
            RecordsQueryResult<EntityRef> daoRecords = recordsDao.queryRecords(localQuery);

            result.merge(daoRecords);

            if (++sourceIdx < this.recordsDao.size()) {

                result.setHasMore(true);

                if (localQuery.isAfterIdMode()) {
                    localQuery.setAfterId(null);
                } else {
                    long skip = localQuery.getSkipCount() - daoRecords.getTotalCount();
                    localQuery.setSkipCount((int) Math.max(skip, 0));
                }
            }
        }

        if (result.getTotalCount() == query.getMaxItems() && result.getHasMore()) {
            result.setTotalCount(result.getTotalCount() + 1);
        }

        return result;
    }

    @Override
    public ActionResults<EntityRef> executeAction(List<EntityRef> records, GroupActionConfig config) {
        ActionResults<EntityRef> results = new ActionResults<>();
        RecordsUtils.groupRefBySource(records).forEach((sourceId, sourceRecs) -> {
            RecordsQueryDao recordsDao = daoBySource.get(sourceId);
            if (recordsDao instanceof RecordsActionExecutor) {
                results.merge(((RecordsActionExecutor) recordsDao).executeAction(sourceRecs, config));
            } else {
                ActionStatus status = new ActionStatus(ActionStatus.STATUS_SKIPPED);
                status.setMessage("Source id " + sourceId + " doesn't support actions");
                for (EntityRef recordRef : sourceRecs) {
                    results.getResults().add(new ActionResult<>(recordRef, status));
                }
            }
        });
        return results;
    }

    public void setRecordsDao(List<RecordsQueryDao> records) {
        this.recordsDao = records;
        records.forEach(r -> daoBySource.put(r.getId(), r));
    }
}
