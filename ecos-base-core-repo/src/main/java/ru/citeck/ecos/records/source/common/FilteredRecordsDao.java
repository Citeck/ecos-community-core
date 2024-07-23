package ru.citeck.ecos.records.source.common;

import org.apache.commons.lang.StringUtils;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.AbstractRecordsDao;
import ru.citeck.ecos.records2.source.dao.RecordsQueryDao;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class FilteredRecordsDao extends AbstractRecordsDao implements RecordsQueryDao {

    private RecordsQueryDao targetDAO;

    @Override
    public RecordsQueryResult<EntityRef> queryRecords(RecordsQuery query) {

        RecordsQuery localQuery = new RecordsQuery(query);
        int maxItems = localQuery.getMaxItems();
        localQuery.setMaxItems((int) (1.5f * maxItems));

        RecordsQueryResult<EntityRef> records = targetDAO.queryRecords(localQuery);
        records.setRecords(records.getRecords().stream().map(ref -> {
            if (StringUtils.isBlank(ref.getSourceId())) {
                return EntityRef.create(ref.getAppName(), targetDAO.getId(), ref.getLocalId());
            }
            return ref;
        }).collect(Collectors.toList()));

        Function<List<EntityRef>, List<EntityRef>> filter = getFilter(query);

        List<EntityRef> filtered = filter.apply(records.getRecords());
        List<EntityRef> resultRecords = new ArrayList<>();

        int itemsCount = Math.min(filtered.size(), maxItems);
        for (int i = 0; i < itemsCount; i++) {
            resultRecords.add(filtered.get(i));
        }

        int totalDiff = records.getRecords().size() - filtered.size();
        records.setTotalCount(records.getTotalCount() - totalDiff);
        records.setRecords(resultRecords);

        return records;
    }

    @Override
    public List<String> getSupportedLanguages() {
        return targetDAO.getSupportedLanguages();
    }

    protected abstract Function<List<EntityRef>, List<EntityRef>> getFilter(RecordsQuery query);

    public void setTargetDAO(RecordsQueryDao targetDAO) {
        this.targetDAO = targetDAO;
    }
}
