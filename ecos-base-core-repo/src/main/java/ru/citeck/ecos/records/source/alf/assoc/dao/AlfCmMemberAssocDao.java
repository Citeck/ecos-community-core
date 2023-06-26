package ru.citeck.ecos.records.source.alf.assoc.dao;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts;
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDao;
import ru.citeck.ecos.utils.AuthorityUtils;

import java.util.Collections;
import java.util.Set;

@Slf4j
@Component
public class AlfCmMemberAssocDao implements AlfAssocDao {

    private static final Set<QName> Q_NAMES = Collections.singleton(ContentModel.ASSOC_MEMBER);

    private final AuthorityUtils authorityUtils;
    private final RecordMutateDao authorityActionDao;

    @Autowired
    public AlfCmMemberAssocDao(AuthorityUtils authorityUtils,
                               @Qualifier("authorityGroupManagerActionRecordsDao") RecordMutateDao authorityActionDao) {
        this.authorityUtils = authorityUtils;
        this.authorityActionDao = authorityActionDao;
    }

    @Override
    @SneakyThrows
    public void create(NodeRef sourceRef, NodeRef targetRef, AssociationDefinition assoc) {
        AuthoritiesActionInfo authoritiesActionInfo = getAuthoritiesInfo(sourceRef, targetRef, true);
        LocalRecordAtts recordAtts = new LocalRecordAtts("", ObjectData.create(authoritiesActionInfo));
        authorityActionDao.mutate(recordAtts);
    }

    @Override
    @SneakyThrows
    public void remove(NodeRef sourceRef, NodeRef targetRef, AssociationDefinition assoc) {
        AuthoritiesActionInfo authoritiesActionInfo = getAuthoritiesInfo(sourceRef, targetRef, false);
        LocalRecordAtts recordAtts = new LocalRecordAtts("", ObjectData.create(authoritiesActionInfo));
        authorityActionDao.mutate(recordAtts);
    }

    private AuthoritiesActionInfo getAuthoritiesInfo(NodeRef sourceRef, NodeRef targetRef, boolean add) {

        String parentAuthName = authorityUtils.getAuthorityName(sourceRef);
        if (StringUtils.isBlank(parentAuthName)) {
            throw new IllegalArgumentException("Parent of cm:member association " +
                "is not an authority. NodeRef: " + sourceRef);
        }
        String childAuthName = authorityUtils.getAuthorityName(targetRef);
        if (StringUtils.isBlank(childAuthName)) {
            throw new IllegalArgumentException("Child of cm:member association " +
                "is not an authority. NodeRef: " + targetRef);
        }
        return new AuthoritiesActionInfo(parentAuthName, childAuthName, add);
    }

    @Override
    public Set<QName> getQNames() {
        return Q_NAMES;
    }

    @Override
    public float getOrder() {
        return 100f;
    }

    @Data
    @RequiredArgsConstructor
    private static class AuthoritiesActionInfo {

        private final String type = "add-or-remove";
        private final String parent;
        private final String child;
        private final Boolean add;

        @Override
        public String toString() {
            return "{" +
                "\"type\":\"" + type + "\"," +
                "\"parent\":\"" + parent + "\"," +
                "\"child\":\"" + child + "\"" +
                "\"add\":\"" + add + "\"" +
                "}";
        }
    }
}
