package ru.citeck.ecos.domain.auth;

import kotlin.jvm.functions.Function0;
import lombok.RequiredArgsConstructor;
import net.sf.acegisecurity.Authentication;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.security.AuthorityService;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.context.lib.ContextServiceFactory;
import ru.citeck.ecos.context.lib.auth.AuthUser;
import ru.citeck.ecos.context.lib.auth.component.AuthComponent;
import ru.citeck.ecos.context.lib.auth.component.SimpleAuthComponent;
import ru.citeck.ecos.context.lib.auth.data.AuthData;
import ru.citeck.ecos.context.lib.auth.data.SimpleAuthData;

import javax.annotation.PostConstruct;
import java.util.*;

@Configuration
public class AlfContextLibConfig extends ContextServiceFactory {

    private static final Map<String, String> OUTER_TO_INNER_USER_MAPPING;
    private static final Map<String, String> INNER_TO_OUTER_USER_MAPPING;

    static {
        OUTER_TO_INNER_USER_MAPPING = new HashMap<>();
        INNER_TO_OUTER_USER_MAPPING = new HashMap<>();

        OUTER_TO_INNER_USER_MAPPING.put(AuthUser.SYSTEM, AuthenticationUtil.SYSTEM_USER_NAME);
        OUTER_TO_INNER_USER_MAPPING.forEach((k, v) -> INNER_TO_OUTER_USER_MAPPING.put(v, k));
    }

    private AuthorityService authorityService;

    @Override
    @PostConstruct
    public void init() {
        super.init();
    }

    @Nullable
    @Override
    protected AuthComponent createAuthComponent() {
        return new AlfAuthComponent(authorityService);
    }

    @Autowired
    public void setAuthorityService(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }

    @RequiredArgsConstructor
    private static class AlfAuthComponent implements AuthComponent {

        private final SimpleAuthComponent simpleAuthComponent = new SimpleAuthComponent();

        private final AuthorityService authorityService;

        @NotNull
        @Override
        public AuthData getCurrentFullAuth() {
            String user = AuthenticationUtil.getFullyAuthenticatedUser();
            List<String> authorities = simpleAuthComponent.getCurrentFullAuth().getAuthorities();
            if (authorities.isEmpty() && StringUtils.isNotBlank(user)) {
                authorities = new ArrayList<>(authorityService.getAuthoritiesForUser(user));
            }
            return new SimpleAuthData(
                INNER_TO_OUTER_USER_MAPPING.getOrDefault(user, user),
                authorities
            );
        }

        @NotNull
        @Override
        public AuthData getCurrentRunAsAuth() {
            String user = AuthenticationUtil.getRunAsUser();
            List<String> authorities = simpleAuthComponent.getCurrentRunAsAuth().getAuthorities();
            if (authorities.isEmpty() && StringUtils.isNotBlank(user)) {
                authorities = new ArrayList<>(authorityService.getAuthoritiesForUser(user));
            }
            return new SimpleAuthData(
                INNER_TO_OUTER_USER_MAPPING.getOrDefault(user, user),
                authorities
            );
        }

        @Override
        public <T> T runAs(@NotNull AuthData auth, boolean full, @NotNull Function0<? extends T> action) {

            return simpleAuthComponent.runAs(auth, full, () -> {

                String user = auth.getUser();
                user = OUTER_TO_INNER_USER_MAPPING.getOrDefault(user, user);

                if (full) {
                    Authentication fullAuthBefore = AuthenticationUtil.getFullAuthentication();
                    AuthenticationUtil.setFullyAuthenticatedUser(user);
                    try {
                        return action.invoke();
                    } finally {
                        AuthenticationUtil.setFullAuthentication(fullAuthBefore);
                    }
                } else {
                    return AuthenticationUtil.runAs(action::invoke, user);
                }
            });
        }

        @NotNull
        @Override
        public List<String> getSystemAuthorities() {
            return Collections.emptyList();
        }
    }
}
