package ru.citeck.ecos.records.source.dao;

import ru.citeck.ecos.action.group.ActionResults;
import ru.citeck.ecos.action.group.GroupActionConfig;
import ru.citeck.ecos.records2.source.dao.RecordsDao;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.List;

public interface RecordsActionExecutor extends RecordsDao {

    ActionResults<EntityRef> executeAction(List<EntityRef> records, GroupActionConfig config);
}
