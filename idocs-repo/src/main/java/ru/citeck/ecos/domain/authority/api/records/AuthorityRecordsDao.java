package ru.citeck.ecos.domain.authority.api.records;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records.source.alf.meta.AlfNodeRecord;
import ru.citeck.ecos.records2.QueryContext;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.EmptyValue;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.graphql.meta.value.field.EmptyMetaField;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.utils.AuthorityUtils;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

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

    @NotNull
    @Override
    public List<Object> getLocalRecordsMeta(@NotNull List<EntityRef> records, @NotNull MetaField metaField) {
        return records.stream().map(r -> new AuthorityValue(r.getLocalId())).collect(Collectors.toList());
    }

    @RequiredArgsConstructor
    private final class AuthorityValue implements MetaValue {

        @NotNull
        private final String id;
        @Getter(lazy = true)
        private final MetaValue alfMetaValue = evalAlfMetaValue();

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
        public Object getAttribute(@NotNull String name, @NotNull MetaField field) throws Exception {
            if (name.equals("nodeRef")) {
                return authorityUtils.getNodeRef(id);
            }
            if (name.equals("containedUsers")) {
                return authorityUtils.getContainedUsers(id, false);
            }
            return getAlfMetaValue().getAttribute(name, field);
        }

        @Override
        public String getDisplayName() {
            return authorityUtils.getDisplayName(id);
        }

        private MetaValue evalAlfMetaValue() {
            if (StringUtils.isBlank(id)) {
                return EmptyValue.INSTANCE;
            }
            NodeRef nodeRef = authorityUtils.getNodeRef(id);
            if (nodeRef == null) {
                return EmptyValue.INSTANCE;
            }
            AlfNodeRecord record = new AlfNodeRecord(RecordRef.valueOf(nodeRef.toString()));
            record.init(QueryContext.getCurrent(), EmptyMetaField.INSTANCE);
            return record;
        }
    }
}
