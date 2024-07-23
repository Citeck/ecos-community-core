package ru.citeck.ecos.i18n.api.records;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;
import ru.citeck.ecos.utils.EcosI18NUtils;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.webscripts.history.HistoryGetUtils;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class I18NRecordsDao extends LocalRecordsDao
    implements LocalRecordsMetaDao<I18NRecordsDao.Value>,
    LocalRecordsQueryWithMetaDao<I18NRecordsDao.Value> {

    private final HistoryGetUtils historyGetUtils;

    @Override
    public RecordsQueryResult<Value> queryLocalRecords(@NotNull RecordsQuery recordsQuery, @NotNull MetaField field) {
        if (TaskOutcomeLabelsQuery.LANG.equals(recordsQuery.getLanguage())) {
            TaskOutcomeLabelsQuery query = recordsQuery.getQuery(TaskOutcomeLabelsQuery.class);
            List<Value> result = query.outcomes
                .stream()
                .map(v -> new Value(
                    historyGetUtils.getOutcomeTitleKey(v.taskType, v.taskDefinitionKey, v.outcome),
                    null
                ))
                .collect(Collectors.toList());

            RecordsQueryResult<Value> res = new RecordsQueryResult<>();
            res.setRecords(result);
            res.setTotalCount(result.size());
            return res;
        }
        return null;
    }

    @NotNull
    @Override
    public List<Value> getLocalRecordsMeta(@NotNull List<EntityRef> records, @NotNull MetaField metaField) {
        return records.stream()
            .map(EntityRef::getLocalId)
            .map(v -> new Value(v, null))
            .collect(Collectors.toList());
    }

    @Override
    public String getId() {
        return "i18n-value";
    }

    @Data
    private static class TaskOutcomeLabelsQuery {
        public static final String LANG = "task-outcome-labels";
        private List<TaskOutcomeInfo> outcomes;
    }

    @Data
    private static class TaskOutcomeInfo {
        private String taskType;
        private String taskDefinitionKey;
        private String outcome;
    }

    @RequiredArgsConstructor
    public static class Value implements MetaValue {

        @NotNull
        private final String id;
        private final Locale locale;

        @Override
        public String getString() {
            return id;
        }

        @Override
        public String getDisplayName() {
            if (id.isEmpty()) {
                return "";
            }
            if (locale == null) {
                return Optional.ofNullable(I18NUtil.getMessage(id)).orElse(id);
            } else {
                return Optional.ofNullable(I18NUtil.getMessage(id, locale)).orElse(id);
            }
        }

        @Override
        public Object getJson() {
            Map<Locale, String> messages = new HashMap<>();
            for (Locale locale : EcosI18NUtils.LOCALES) {
                messages.put(locale, I18NUtil.getMessage(id, locale));
            }
            return messages;
        }

        @NotNull
        @Override
        public String getId() {
            return id;
        }

        @Override
        public Object getAttribute(@NotNull String name, @NotNull MetaField field) {
            return new Value(id, new Locale(name));
        }
    }
}
