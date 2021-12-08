package ru.citeck.ecos.flowable.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.config.EcosConfigService;
import ru.citeck.ecos.config.EcosConfigServiceJs;

/**
 * @author Roman Makarskiy
 */
@Component
public class EcosConfigServiceInj extends EcosConfigServiceJs implements FlowableEngineProcessService {

    private static final String FLOWABLE_ECOS_CONFIG_SERVICE_KEY = "ecosConfig";

    @Override
    public String getKey() {
        return FLOWABLE_ECOS_CONFIG_SERVICE_KEY;
    }

    @Autowired
    public void setEcosConfigService(EcosConfigService ecosConfigService) {
        super.setEcosConfigService(ecosConfigService);
    }
}
