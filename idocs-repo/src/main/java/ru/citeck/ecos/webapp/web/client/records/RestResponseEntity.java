package ru.citeck.ecos.webapp.web.client.records;

import lombok.Data;
import ru.citeck.ecos.webapp.lib.web.http.HttpHeaders;

@Data
public class RestResponseEntity {
    private HttpHeaders headers = new HttpHeaders();
    private byte[] body;
    private int status;
}
