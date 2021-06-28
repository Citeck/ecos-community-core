package ru.citeck.ecos.domain.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
@AllArgsConstructor
public class EcosAuthContextData {
    @Nullable
    private final String ecosUserHeader;
    @Nullable
    private final String authHeader;
}
