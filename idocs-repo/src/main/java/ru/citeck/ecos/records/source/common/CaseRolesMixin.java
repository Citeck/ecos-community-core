package ru.citeck.ecos.records.source.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.lib.role.dto.RoleDef;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.source.common.AttributesMixin;
import ru.citeck.ecos.records3.record.op.atts.service.value.AttValue;
import ru.citeck.ecos.role.CaseRoleService;
import ru.citeck.ecos.utils.AuthorityUtils;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CaseRolesMixin implements AttributesMixin<Class<RecordRef>, RecordRef> {

    private final AuthorityUtils authorityUtils;
    private final CaseRoleService caseRoleService;
    private final AlfNodesRecordsDAO alfNodesRecordsDao;
    private final AuthenticationService authenticationService;

    @Autowired
    public CaseRolesMixin(AuthorityUtils authorityUtils,
                          CaseRoleService caseRoleService,
                          AlfNodesRecordsDAO alfNodesRecordsDao,
                          AuthenticationService authenticationService) {
        this.authorityUtils = authorityUtils;
        this.caseRoleService = caseRoleService;
        this.alfNodesRecordsDao = alfNodesRecordsDao;
        this.authenticationService = authenticationService;
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
            if (name.equals("list")) {
                return caseRoleService.getRoles(documentId)
                    .stream()
                    .map(ref -> new CaseRoleInfo(ref, caseRoleService.getRoleDef(ref)))
                    .collect(Collectors.toList());
            }
            return new CaseRole(documentId, name);
        }
    }

    @AllArgsConstructor
    public static class CaseRoleInfo implements AttValue {

        private final NodeRef roleId;
        private final RoleDef roleDef;

        @Nullable
        @Override
        public Object getId() {
            return roleId;
        }

        @Nullable
        @Override
        public Object getAtt(String name) {
            switch (name) {
                case "name": return roleDef.getName();
                case "assignees": return roleDef.getAssignees();
                case "attribute": return roleDef.getAttribute();
            }
            return null;
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
                name = authenticationService.getCurrentUserName();
            }

            NodeRef authorityRef = authorityUtils.getNodeRef(name);
            if (authorityRef == null) {
                return false;
            }

            return caseRoleService.isRoleMember(document, roleId, authorityRef);
        }
    }
}
