package ru.citeck.ecos.records;

import ru.citeck.ecos.action.group.ActionResult;
import ru.citeck.ecos.action.group.ActionResults;
import ru.citeck.ecos.action.group.ActionStatus;
import ru.citeck.ecos.action.group.GroupActionConfig;
import ru.citeck.ecos.records.source.dao.*;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsServiceFactory;
import ru.citeck.ecos.records2.source.dao.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RecordsServiceImpl extends ru.citeck.ecos.records2.RecordsServiceImpl {

    private final Map<String, RecordsActionExecutor> actionExecutors = new ConcurrentHashMap<>();

    public RecordsServiceImpl(RecordsServiceFactory factory) {
        super(factory);
    }

    public ActionResults<RecordRef> executeAction(Collection<RecordRef> records,
                                                  GroupActionConfig processConfig) {

        ActionResults<RecordRef> results = new ActionResults<>();

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

    @Override
    public void register(RecordsDao recordsSource) {

        super.register(recordsSource);

        if (recordsSource instanceof RecordsActionExecutor) {
            actionExecutors.put(recordsSource.getId(), (RecordsActionExecutor) recordsSource);
        }
    }
}
