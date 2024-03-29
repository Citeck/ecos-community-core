package ru.citeck.ecos.webapp.authority;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.context.lib.auth.AuthGroup;
import ru.citeck.ecos.context.lib.auth.AuthRole;
import ru.citeck.ecos.utils.AuthorityUtils;
import ru.citeck.ecos.webapp.api.authority.EcosAuthoritiesApi;
import ru.citeck.ecos.webapp.api.constants.AppName;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class EcosAuthorityServiceImpl implements EcosAuthoritiesApi {

    private final AuthorityUtils authorityUtils;

    @NotNull
    @Override
    public EntityRef getGroupRef(@NotNull String value) {
        return EntityRef.create(AppName.EMODEL, "authority-group", value);
    }

    @NotNull
    @Override
    public EntityRef getPersonRef(@NotNull String value) {
        return EntityRef.create(AppName.EMODEL, "person", value);
    }

    @Override
    public boolean isGroup(@Nullable Object value) {
        String name = authorityUtils.getAuthorityName(value);
        return name != null && name.startsWith(AuthGroup.PREFIX);
    }

    @Override
    public boolean isPerson(@Nullable Object value) {
        String name = authorityUtils.getAuthorityName(value);
        return name != null && !name.startsWith(AuthGroup.PREFIX) && !name.startsWith(AuthRole.PREFIX);
    }

    @Override
    public boolean isRole(@Nullable Object value) {
        String name = authorityUtils.getAuthorityName(value);
        return name != null && name.startsWith(AuthRole.PREFIX);
    }

    @NotNull
    @Override
    public String getAuthorityName(@Nullable Object authority) {
        return authorityUtils.getAuthorityName(authority);
    }

    @NotNull
    @Override
    public List<String> getAuthorityNames(@NotNull List<?> authorities) {
        return authorities.stream()
            .map(this::getAuthorityName)
            .collect(Collectors.toList());
    }

    @NotNull
    @Override
    public EntityRef getAuthorityRef(@Nullable Object authority) {
        String name = authorityUtils.getAuthorityName(authority);
        if (name.startsWith(AuthGroup.PREFIX)) {
            return EntityRef.create(
                AppName.EMODEL,
                "authority-group",
                name.substring(AuthGroup.PREFIX.length())
            );
        } else {
            return EntityRef.create(AppName.EMODEL, "person", name);
        }
    }

    @NotNull
    @Override
    public List<EntityRef> getAuthorityRefs(@NotNull List<?> authorities) {
        return authorities.stream()
            .map(this::getAuthorityRef)
            .collect(Collectors.toList());
    }
}
