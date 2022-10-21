package ru.citeck.ecos.domain.auth;

import kotlin.jvm.functions.Function0;
import net.sf.acegisecurity.Authentication;
import net.sf.acegisecurity.GrantedAuthority;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.context.lib.ContextServiceFactory;
import ru.citeck.ecos.context.lib.auth.AuthConstants;
import ru.citeck.ecos.context.lib.auth.component.AuthComponent;
import ru.citeck.ecos.context.lib.auth.data.AuthData;
import ru.citeck.ecos.context.lib.auth.data.EmptyAuth;
import ru.citeck.ecos.context.lib.auth.data.SimpleAuthData;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

@Configuration
public class AlfContextLibConfig extends ContextServiceFactory {

    private static final Map<String, String> OUTER_TO_INNER_USER_MAPPING;
    private static final Map<String, String> INNER_TO_OUTER_USER_MAPPING;

    static {
        OUTER_TO_INNER_USER_MAPPING = new HashMap<>();
        INNER_TO_OUTER_USER_MAPPING = new HashMap<>();

        OUTER_TO_INNER_USER_MAPPING.put(AuthConstants.SYSTEM_USER, AuthenticationUtil.SYSTEM_USER_NAME);

        OUTER_TO_INNER_USER_MAPPING.forEach((k, v) -> INNER_TO_OUTER_USER_MAPPING.put(v, k));
    }

    @Override
    @PostConstruct
    public void init() {
        super.init();
    }

    @Nullable
    @Override
    protected AuthComponent createAuthComponent() {
        return new AlfAuthComponent();
    }

    private static class AlfAuthComponent implements AuthComponent {

        private final ThreadLocal<List<String>> authAuthorities = new ThreadLocal<>();

        @NotNull
        @Override
        public AuthData getCurrentFullAuth() {
            return getUserAuth(AuthenticationUtil.getFullyAuthenticatedUser(),
                Arrays.asList(AuthenticationUtil.getFullAuthentication().getAuthorities())
            );
        }

        @NotNull
        @Override
        public AuthData getCurrentRunAsAuth() {
            return getUserAuth(AuthenticationUtil.getRunAsUser(),
                Arrays.asList(AuthenticationUtil.getRunAsAuthentication().getAuthorities())
            );
        }

        private AuthData getUserAuth(String user, List<GrantedAuthority> grantedAuthorities) {

            if (StringUtils.isBlank(user)) {
                return EmptyAuth.INSTANCE;
            }
            user = INNER_TO_OUTER_USER_MAPPING.getOrDefault(user, user);

            if (CollectionUtils.isEmpty(grantedAuthorities)) {
                grantedAuthorities = Collections.emptyList();
            }

            // GrantedAuth does not contain all actual user authorities, so we need to get them from thread local.
            Set<String> authorities = grantedAuthorities
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

            if (authAuthorities.get() != null) {
                authorities.addAll(authAuthorities.get());
            }

            return new SimpleAuthData(user, new ArrayList<>(authorities));
        }

        @Override
        public <T> T runAs(@NotNull AuthData auth, boolean full, @NotNull Function0<? extends T> action) {

            String user = auth.getUser();
            user = OUTER_TO_INNER_USER_MAPPING.getOrDefault(user, user);

            authAuthorities.set(auth.getAuthorities());

            if (full) {
                Authentication fullAuth = AuthenticationUtil.getFullAuthentication();
                AuthenticationUtil.setFullyAuthenticatedUser(user);
                try {
                    return action.invoke();
                } finally {
                    authAuthorities.remove();
                    AuthenticationUtil.setFullAuthentication(fullAuth);
                }
            } else {
                try {
                    return AuthenticationUtil.runAs(action::invoke, user);
                } finally {
                    authAuthorities.remove();
                }
            }
        }

        @NotNull
        @Override
        public List<String> getSystemAuthorities() {
            return Collections.emptyList();
        }
    }
}
