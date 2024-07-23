package ru.citeck.ecos.records;

import org.springframework.stereotype.Service;
import ru.citeck.ecos.action.group.ActionResult;
import ru.citeck.ecos.action.group.ActionResults;
import ru.citeck.ecos.action.group.ActionStatus;
import ru.citeck.ecos.action.group.GroupActionConfig;
import ru.citeck.ecos.records.source.dao.*;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class RecordGroupActionsService {

    private final Map<String, RecordsActionExecutor> actionExecutors = new ConcurrentHashMap<>();

    public ActionResults<EntityRef> executeAction(Collection<EntityRef> records,
                                                  GroupActionConfig processConfig) {

        ActionResults<EntityRef> results = new ActionResults<>();

        RecordsUtils.groupRefBySource(records).forEach((sourceId, refs) -> {

            Optional<RecordsActionExecutor> source = Optional.ofNullable(actionExecutors.get(sourceId));

            if (source.isPresent()) {

                results.merge(source.get().executeAction(refs, processConfig));

            } else {

                ActionStatus status = ActionStatus.skipped("RecordsDao can't execute action");
                results.addResults(refs.stream()
                                       .map(r -> new ActionResult<>(r, status))
                                       .collect(Collectors.toList()));
            }
        });
        return results;
    }

    public void setRecords(List<RecordsActionExecutor> recordsDao) {
        recordsDao.forEach(dao -> actionExecutors.put(dao.getId(), dao));
    }
}
