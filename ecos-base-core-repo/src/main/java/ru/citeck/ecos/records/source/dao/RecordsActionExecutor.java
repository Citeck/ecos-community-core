package ru.citeck.ecos.records.source.dao;

import ru.citeck.ecos.action.group.ActionResults;
import ru.citeck.ecos.action.group.GroupActionConfig;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.source.dao.RecordsDao;

import java.util.List;

public interface RecordsActionExecutor extends RecordsDao {

    ActionResults<RecordRef> executeAction(List<RecordRef> records, GroupActionConfig config);
}
