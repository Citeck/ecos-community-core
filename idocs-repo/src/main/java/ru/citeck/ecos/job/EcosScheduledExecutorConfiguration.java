package ru.citeck.ecos.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.props.EcosPropertiesService;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Slf4j
@Configuration
public class EcosScheduledExecutorConfiguration {

    private static final String PROP_CORE_POOL_SIZE = "ecos.scheduled-executor.core-pool-size";
    private static final String PROP_MAX_POOL_SIZE = "ecos.scheduled-executor.max-pool-size";

    private EcosPropertiesService propertiesService;

    @Bean(name = "ecosScheduledExecutor")
    public ScheduledExecutorService getEcosScheduledExecutor() {

        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
            propertiesService.getInt(PROP_CORE_POOL_SIZE, 5));

        executor.setMaximumPoolSize(propertiesService.getInt(PROP_MAX_POOL_SIZE, 10));

        String msg = "Create async task executor with parameters:\n" +
            "corePoolSize: " + executor.getCorePoolSize() + "\n" +
            "maxPoolSize: " + executor.getMaximumPoolSize();

        log.info(msg);

        return executor;
    }

    @Autowired
    public void setPropertiesService(EcosPropertiesService propertiesService) {
        this.propertiesService = propertiesService;
    }
}
