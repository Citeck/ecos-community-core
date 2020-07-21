package ru.citeck.ecos.records.source.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.source.common.AttributesMixin;
import ru.citeck.ecos.role.CaseRoleService;
import ru.citeck.ecos.utils.AuthorityUtils;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class CaseRolesMixin implements AttributesMixin<Class<RecordRef>, RecordRef> {

    private AuthorityUtils authorityUtils;
    private CaseRoleService caseRoleService;
    private AlfNodesRecordsDAO alfNodesRecordsDao;

    @Autowired
    public CaseRolesMixin(AuthorityUtils authorityUtils,
                          CaseRoleService caseRoleService,
                          AlfNodesRecordsDAO alfNodesRecordsDao) {
        this.authorityUtils = authorityUtils;
        this.caseRoleService = caseRoleService;
        this.alfNodesRecordsDao = alfNodesRecordsDao;
    }

    @PostConstruct
    public void setup() {
        alfNodesRecordsDao.addAttributesMixin(this);
    }

    @Override
    public List<String> getAttributesList() {
        return Collections.singletonList("case-roles");
    }

    @Override
    public Object getAttribute(String s, RecordRef recordRef, MetaField metaField) {
        NodeRef documentNodeRef = new NodeRef(recordRef.getId());
        return new CaseRoles(documentNodeRef);
    }

    @Override
    public Class<RecordRef> getMetaToRequest() {
        return RecordRef.class;
    }

    @Data
    @AllArgsConstructor
    public class CaseRoles implements MetaValue {

        private NodeRef documentId;

        @Override
        public Object getAttribute(String name, MetaField field) {
            return new CaseRole(documentId, name);
        }
    }

    @RequiredArgsConstructor
    public class CaseRole implements MetaValue {

        private static final String CURRENT_USER_EXPRESSION = "$CURRENT";
        private final NodeRef document;
        private final String roleId;

        @Override
        public boolean has(String name) {
            if (StringUtils.isBlank(name)) {
                return false;
            }

            if (name.equals(CURRENT_USER_EXPRESSION)) {
                name = AuthenticationUtil.getRunAsUser();
            }

            NodeRef authorityRef = authorityUtils.getNodeRef(name);
            if (authorityRef == null) {
                return false;
            }

            return caseRoleService.isRoleMember(document, roleId, authorityRef);
        }
    }
}
