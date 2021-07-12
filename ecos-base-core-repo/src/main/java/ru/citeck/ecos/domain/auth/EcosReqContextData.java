package ru.citeck.ecos.domain.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

@Data
@AllArgsConstructor
public class EcosReqContextData {
    @Nullable
    private final String ecosUserHeader;
    @Nullable
    private final String authHeader;
    @Nullable
    private final String timezoneHeader;

    private final boolean isSystemRequest;

    private final float utcOffset;
}
