package ru.citeck.ecos.domain.authority.api.records;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.utils.AuthorityUtils;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AuthorityRecordsDao extends LocalRecordsDao implements LocalRecordsMetaDao<Object> {

    public static final String ID = "authority";

    private final AuthorityUtils authorityUtils;

    @Autowired
    public AuthorityRecordsDao(AuthorityUtils authorityUtils) {
        setId(ID);
        this.authorityUtils = authorityUtils;
    }

    @Override
    public List<Object> getLocalRecordsMeta(List<RecordRef> records, MetaField metaField) {
        return records.stream().map(r -> new AuthorityValue(r.getId())).collect(Collectors.toList());
    }

    @RequiredArgsConstructor
    private final class AuthorityValue implements MetaValue {

        @NotNull
        private final String id;

        @NotNull
        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getString() {
            return id;
        }

        @Override
        public String getDisplayName() {
            return authorityUtils.getDisplayName(id);
        }
    }
}
