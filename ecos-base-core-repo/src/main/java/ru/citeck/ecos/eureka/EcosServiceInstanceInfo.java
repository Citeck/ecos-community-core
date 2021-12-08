package ru.citeck.ecos.eureka;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

@Data
@RequiredArgsConstructor
public class EcosServiceInstanceInfo {

    private final String host;
    private final String ip;
    private final Integer port;
    private final Map<String, String> metadata;
    private final Boolean securePortEnabled;

    public EcosServiceInstanceInfo apply(EcosServiceInstanceInfo info) {

        return new EcosServiceInstanceInfo(
            StringUtils.isNotBlank(info.host) ? info.host : host,
            StringUtils.isNotBlank(info.ip) ? info.ip : ip,
            info.port != null ? info.port : port,
            info.metadata != null ? info.metadata : metadata,
            info.securePortEnabled != null ? info.securePortEnabled : securePortEnabled
        );
    }
}
