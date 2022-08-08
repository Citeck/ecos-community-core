package ru.citeck.ecos.records.source.common;

import lombok.RequiredArgsConstructor;
import org.alfresco.service.cmr.repository.NodeRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.utils.StringUtils;
import ru.citeck.ecos.context.lib.auth.AuthContext;
import ru.citeck.ecos.model.lib.role.constants.RoleConstants;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records3.record.atts.value.AttValue;
import ru.citeck.ecos.records3.record.atts.value.AttValueCtx;
import ru.citeck.ecos.records3.record.mixin.AttMixin;
import ru.citeck.ecos.role.CaseRoleService;
import ru.citeck.ecos.utils.AuthorityUtils;
import ru.citeck.ecos.utils.NodeUtils;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class EcosRolesMixin implements AttMixin {

    private final NodeUtils nodeUtils;
    private final AuthorityUtils authorityUtils;
    private final CaseRoleService caseRoleService;
    private final AlfNodesRecordsDAO alfNodesRecordsDao;

    @PostConstruct
    public void setup() {
        alfNodesRecordsDao.addAttributesMixin(this);
    }

    @Nullable
    @Override
    public Object getAtt(@NotNull String name, @NotNull AttValueCtx attValueCtx) throws Exception {
        if (!RoleConstants.ATT_ROLES.equals(name)) {
            return null;
        }
        String caseId = attValueCtx.getLocalId();
        if (nodeUtils.isNodeRef(caseId)) {
            return new RolesAttValue(new NodeRef(caseId));
        }
        return null;
    }

    @NotNull
    @Override
    public Collection<String> getProvidedAtts() {
        return Collections.singleton(RoleConstants.ATT_ROLES);
    }

    @RequiredArgsConstructor
    private class RolesAttValue implements AttValue {

        private final NodeRef caseRef;

        @Nullable
        @Override
        public Object getAtt(String name) {
            switch (name) {
                case RoleConstants.ATT_IS_CURRENT_USER_MEMBER_OF:
                    return new IsMemberOfRoleValue(caseRef);
                case RoleConstants.ATT_ASSIGNEES_OF:
                    return new AssigneesOfRoleValue(caseRef);
            }
            return null;
        }
    }

    @RequiredArgsConstructor
    private class AssigneesOfRoleValue implements AttValue {

        private final NodeRef caseRef;

        @Nullable
        @Override
        public Object getAtt(String roleName) {
            return AuthContext.runAsSystemJ(() -> {
                Set<NodeRef> assignees = caseRoleService.getAssignees(caseRef, roleName);
                if (assignees == null) {
                    return Collections.emptyList();
                }
                return assignees.stream()
                    .map(authorityUtils::getAuthorityName)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toList());
            });
        }
    }

    @RequiredArgsConstructor
    private class IsMemberOfRoleValue implements AttValue {

        private final NodeRef caseRef;

        @Nullable
        @Override
        public Object getAtt(String roleName) {
            return AuthContext.runAsSystemJ(() -> {
                NodeRef userNodeRef = authorityUtils.getNodeRef(AuthContext.getCurrentUser());
                if (userNodeRef == null) {
                    return false;
                }
                return caseRoleService.isRoleMember(caseRef, roleName, userNodeRef);
            });
        }
    }
}
