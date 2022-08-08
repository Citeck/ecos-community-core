package ru.citeck.ecos.webapp.web.client.records;

import lombok.Data;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.webapp.lib.web.http.HttpHeaders;

@Data
public class RestRequestEntity {
    @NotNull
    private HttpHeaders headers = new HttpHeaders();
    @Nullable
    private byte[] body;
}
