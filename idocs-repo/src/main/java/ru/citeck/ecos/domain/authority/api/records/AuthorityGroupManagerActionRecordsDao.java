package ru.citeck.ecos.domain.authority.api.records;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.rest.framework.core.exceptions.PermissionDeniedException;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.apache.commons.lang.StringUtils;
import org.ehcache.impl.internal.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.commons.utils.MandatoryParam;
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts;
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDao;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class AuthorityGroupManagerActionRecordsDao implements RecordMutateDao {

    private static final Set<String> PROTECTED_GROUPS = new HashSet<>(
        Collections.singletonList("GROUP_ALFRESCO_ADMINISTRATORS")
    );

    private final Map<String, Class<? extends AuthorityAction>> actionTypes = new ConcurrentHashMap<>();

    private final AuthenticationService authenticationService;
    private final AuthorityService authorityService;

    @Value("${ecos.authority-group-manager.config}")
    private String authorityGroupManagerConfigValue;
    private Map<String, Set<String>> managedGroupsByAuthority;

    @PostConstruct
    public void init() {
        actionTypes.put(AddOrRemoveAuthorityAction.ID, AddOrRemoveAuthorityAction.class);
        actionTypes.put(CreateGroupAction.ID, CreateGroupAction.class);
        if (StringUtils.isBlank(authorityGroupManagerConfigValue)
                || authorityGroupManagerConfigValue.charAt(0) == '$') {
            managedGroupsByAuthority = Collections.emptyMap();
        } else {
            managedGroupsByAuthority = new HashMap<>();
            String[] lines = authorityGroupManagerConfigValue.split(";");
            for (String line : lines) {
                if (StringUtils.isBlank(line)) {
                    continue;
                }
                String[] managerAndManaged = line.split(":");
                if (managerAndManaged.length != 2) {
                    throw new RuntimeException(
                        "Invalid managers config line: " +
                        "'" + authorityGroupManagerConfigValue + "'"
                    );
                }
                String manager = managerAndManaged[0].trim();
                Set<String> managedGroups = Arrays.stream(managerAndManaged[1].split(","))
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .collect(Collectors.toSet());

                managedGroupsByAuthority.put(manager, managedGroups);
            }
            log.info("Managed groups by authority: " + managedGroupsByAuthority);
        }
    }

    @NotNull
    @Override
    public String mutate(@NotNull LocalRecordAtts localRecordAtts) {

        String type = localRecordAtts.getAtt("type", "");

        Class<? extends AuthorityAction> actionClass = actionTypes.get(type);
        if (actionClass == null) {
            throw new IllegalArgumentException("Unknown action type: '" + localRecordAtts.getId() + "'");
        }

        AuthorityAction action = createInstance(actionClass);
        Json.getMapper().applyData(action, localRecordAtts.getAttributes());

        action.checkValidity();

        String currentUserName = authenticationService.getCurrentUserName();
        if (currentUserName == null) {
            currentUserName = "";
        }

        log.info("[" + currentUserName + "] Authority group action: " + action);

        boolean isAdminOrSystem = AuthenticationUtil.isRunAsUserTheSystemUser()
            || authorityService.isAdminAuthority(currentUserName);

        if (!isAdminOrSystem) {
            if (managedGroupsByAuthority.isEmpty()) {
                throw new PermissionDeniedException();
            }
            Set<String> userAuthorities = new HashSet<>(authorityService.getAuthoritiesForUser(currentUserName));
            userAuthorities.add(currentUserName);
            Set<String> managedGroups = new HashSet<>();

            for (String authority : userAuthorities) {
                Set<String> managedGroupsByAuthority = this.managedGroupsByAuthority.get(authority);
                if (managedGroupsByAuthority != null) {
                    managedGroups.addAll(managedGroupsByAuthority);
                }
            }
            if (managedGroups.isEmpty()) {
                throw new PermissionDeniedException();
            }
            action.checkPermissions(managedGroups, currentUserName);
        }
        action.execute(currentUserName);

        return localRecordAtts.getId();
    }

    @NotNull
    @Override
    public String getId() {
        return "authority-group-manager-action";
    }

    private AuthorityAction createInstance(Class<? extends AuthorityAction> clazz) {
        try {
            try {
                return clazz.getConstructor().newInstance();
            } catch (NoSuchMethodException e) {
                return clazz.getConstructor(AuthorityGroupManagerActionRecordsDao.class).newInstance(this);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void checkPermissionsForGroup(String group, boolean parent, Set<String> managedGroups) {

        if (PROTECTED_GROUPS.contains(group)) {
            throw new PermissionDeniedException();
        }

        Set<String> authoritiesToCheck;
        if (parent) {
            authoritiesToCheck = authorityService.getContainingAuthorities(AuthorityType.GROUP, group, false);
            if (!managedGroups.contains(group)
                    && authoritiesToCheck.stream().noneMatch(managedGroups::contains)) {

                throw new PermissionDeniedException();
            }
            if (managedGroupsByAuthority.containsKey(group)
                    || authoritiesToCheck.stream().anyMatch(managedGroupsByAuthority::containsKey)) {

                throw new PermissionDeniedException();
            }
        } else {
            authoritiesToCheck = authorityService.getContainedAuthorities(AuthorityType.GROUP, group, false);
        }

        if (authoritiesToCheck.stream().anyMatch(PROTECTED_GROUPS::contains)) {
            throw new PermissionDeniedException();
        }
    }

    @Data
    @NoArgsConstructor
    class AddOrRemoveAuthorityAction implements AuthorityAction {

        public static final String ID = "add-or-remove";

        private String parent;
        private String child;
        private Boolean add;

        @Override
        public void checkPermissions(Set<String> managedGroups, String currentUser) {
            if (child.equals(currentUser)) {
                throw new PermissionDeniedException();
            }
            checkPermissionsForGroup(parent, true, managedGroups);
            if (child.startsWith(AuthorityType.GROUP.getPrefixString())) {
                checkPermissionsForGroup(child, false, managedGroups);
            }
        }

        @Override
        public void checkValidity() {
            MandatoryParam.checkString("parent", parent);
            MandatoryParam.checkString("child", child);
            MandatoryParam.check("add", add);
        }

        @Override
        public void execute(String currentUser) {
            if (add) {
                log.info("[" + currentUser + "] Add authority. Parent: '" + parent + "' Child: '" + child + "'");
                authorityService.addAuthority(parent, child);
            } else {
                log.info("[" + currentUser + "] Remove authority. Parent: '" + parent + "' Child: '" + child + "'");
                authorityService.removeAuthority(parent, child);
            }
        }
    }

    @Data
    @NoArgsConstructor
    class CreateGroupAction implements AuthorityAction {

        public static final String ID = "create";

        private String parent;
        private String shortName;
        private String displayName;

        @Override
        public void checkPermissions(Set<String> managedGroups, String currentUser) {
            if (StringUtils.isNotBlank(parent)) {
                checkPermissionsForGroup(parent, true, managedGroups);
            }
        }

        @Override
        public void checkValidity() {
            MandatoryParam.checkString("shortName", shortName);
        }

        @Override
        public void execute(String currentUser) {
            String displayName = this.displayName;
            if (StringUtils.isBlank(displayName)) {
                displayName = shortName;
            }
            String fullName = AuthorityType.GROUP.getPrefixString() + shortName;
            log.info("[" + currentUser + "] Create group. Parent: '" + parent + "' New group: '" + fullName + "'");
            authorityService.createAuthority(
                AuthorityType.GROUP,
                shortName,
                displayName,
                authorityService.getDefaultZones()
            );
            if (StringUtils.isNotBlank(parent)) {
                authorityService.addAuthority(parent, fullName);
            }
        }
    }

    interface AuthorityAction {

        void checkValidity();

        void checkPermissions(Set<String> managedGroups, String currentUser);

        void execute(String currentUser);
    }
}
