package ru.citeck.ecos.domain.auth;

import kotlin.jvm.functions.Function0;
import net.sf.acegisecurity.Authentication;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        @NotNull
        @Override
        public AuthData getCurrentFullAuth() {
            return getUserAuth(AuthenticationUtil.getFullyAuthenticatedUser());
        }

        @NotNull
        @Override
        public AuthData getCurrentRunAsAuth() {
            return getUserAuth(AuthenticationUtil.getRunAsUser());
        }

        private AuthData getUserAuth(String user) {

            if (StringUtils.isBlank(user)) {
                return EmptyAuth.INSTANCE;
            }
            user = INNER_TO_OUTER_USER_MAPPING.getOrDefault(user, user);

            return new SimpleAuthData(user, Collections.emptyList());
        }

        @Override
        public <T> T runAs(@NotNull AuthData auth, boolean full, @NotNull Function0<? extends T> action) {

            String user = auth.getUser();
            user = OUTER_TO_INNER_USER_MAPPING.getOrDefault(user, user);

            if (full) {
                Authentication fullAuth = AuthenticationUtil.getFullAuthentication();
                AuthenticationUtil.setFullyAuthenticatedUser(user);
                try {
                    return action.invoke();
                } finally {
                    AuthenticationUtil.setFullAuthentication(fullAuth);
                }
            } else {
                return AuthenticationUtil.runAs(action::invoke, user);
            }
        }

        @NotNull
        @Override
        public List<String> getSystemAuthorities() {
            return Collections.emptyList();
        }
    }
}
