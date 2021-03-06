package ru.citeck.ecos.eureka;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Data
@RequiredArgsConstructor
public class EcosServiceInstanceInfo {

    private final String host;
    private final String ip;
    private final Integer port;

    public EcosServiceInstanceInfo apply(EcosServiceInstanceInfo info) {

        return new EcosServiceInstanceInfo(
            StringUtils.isNotBlank(info.host) ? info.host : host,
            StringUtils.isNotBlank(info.ip) ? info.ip : ip,
            info.port != null ? info.port : port
        );
    }
}
