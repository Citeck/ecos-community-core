package ru.citeck.ecos.i18n.api.records;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class I18NRecordsDao extends LocalRecordsDao implements LocalRecordsMetaDao<I18NRecordsDao.Value> {

    @NotNull
    @Override
    public List<Value> getLocalRecordsMeta(@NotNull List<RecordRef> records, @NotNull MetaField metaField) {
        return records.stream()
            .map(RecordRef::getId)
            .map(v -> new Value(v, null))
            .collect(Collectors.toList());
    }

    @Override
    public String getId() {
        return "i18n-value";
    }

    @RequiredArgsConstructor
    public static class Value implements MetaValue {

        private final String id;
        private final Locale locale;

        @Override
        public String getString() {
            return id;
        }

        @Override
        public String getDisplayName() {
            if (locale == null) {
                return Optional.ofNullable(I18NUtil.getMessage(id)).orElse(id);
            } else {
                return Optional.ofNullable(I18NUtil.getMessage(id, locale)).orElse(id);
            }
        }

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
