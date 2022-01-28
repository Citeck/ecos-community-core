package ru.citeck.ecos.domain.authority.api.records;

import lombok.RequiredArgsConstructor;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.graphql.AlfGqlContext;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records.source.alf.meta.AlfNodeRecord;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.field.EmptyMetaField;
import ru.citeck.ecos.records2.request.mutation.RecordsMutation;
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts;
import ru.citeck.ecos.records3.record.atts.value.AttValue;
import ru.citeck.ecos.records3.record.dao.atts.RecordAttsDao;
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDao;

import java.util.Collections;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class AuthorityGroupRecordsDao implements RecordMutateDao, RecordAttsDao {

    private final AuthorityService authorityService;
    private final AlfNodesRecordsDAO alfNodesRecordsDao;

    @NotNull
    @Override
    public String mutate(@NotNull LocalRecordAtts atts) {

        String groupId = atts.getId();
        boolean createIfNotExists = false;

        if (groupId.isEmpty()) {
            DataValue id = atts.getAttributes().get("id");
            if (id.isTextual() && StringUtils.isNotBlank(id.asText())) {
                groupId = id.asText();
                createIfNotExists = true;
            }
        }
        if (groupId.isEmpty()) {
            throw new RuntimeException("Authority group ID can't be empty for mutation");
        }

        NodeRef nodeRef = authorityService.getAuthorityNodeRef("GROUP_" + groupId);
        if (nodeRef == null) {
            if (!createIfNotExists) {
                throw new RuntimeException("Authority group doesn't exists: " + groupId);
            }
            authorityService.createAuthority(
                AuthorityType.GROUP,
                groupId,
                groupId,
                authorityService.getDefaultZones()
            );
            nodeRef = authorityService.getAuthorityNodeRef("GROUP_" + groupId);
        }

        RecordsMutation mutation = new RecordsMutation();
        RecordRef ref = RecordRef.create("", nodeRef.toString());
        mutation.setRecords(Collections.singletonList(new RecordMeta(ref, atts.getAttributes())));

        return alfNodesRecordsDao.mutate(mutation).getRecords().get(0).getId().getId();
    }

    @Nullable
    @Override
    public Object getRecordAtts(@NotNull String groupId) {
        NodeRef nodeRef = authorityService.getAuthorityNodeRef("GROUP_" + groupId);
        return new GroupRecord(groupId, nodeRef);
    }

    @NotNull
    @Override
    public String getId() {
        return "authority-group";
    }

    private static class GroupRecord implements AttValue {

        private final String groupId;
        private final AlfNodeRecord alfRecord;

        public GroupRecord(String groupId, NodeRef nodeRef) {
            this.groupId = groupId;
            alfRecord = new AlfNodeRecord(RecordRef.create("", nodeRef.toString()));
            alfRecord.init(AlfGqlContext.getCurrent(), EmptyMetaField.INSTANCE);
        }

        @Nullable
        @Override
        public Object getId() throws Exception {
            return groupId;
        }

        @Nullable
        @Override
        public Object getAtt(String name) throws Exception {

            return alfRecord.getAttribute(name, EmptyMetaField.INSTANCE);
        }
    }
}
