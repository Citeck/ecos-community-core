package ru.citeck.ecos.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@Slf4j
@Configuration
public class EcosScheduledExecutorConfiguration {

    private static final String PROP_CORE_POOL_SIZE = "ecos.scheduled-executor.core-pool-size";
    private static final String PROP_MAX_POOL_SIZE = "ecos.scheduled-executor.max-pool-size";

    @Autowired
    @Qualifier("global-properties")
    private Properties properties;

    @Bean(name = "ecosScheduledExecutor")
    public ScheduledExecutorService getEcosScheduledExecutor() {

        int corePoolSize = Integer.parseInt(properties.getProperty(PROP_CORE_POOL_SIZE, "5"));
        int maxPoolSize = Integer.parseInt(properties.getProperty(PROP_MAX_POOL_SIZE, "10"));

        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(corePoolSize);
        executor.setMaximumPoolSize(maxPoolSize);

        String msg = "Create async task executor with parameters:\n" +
            "corePoolSize: " + executor.getCorePoolSize() + "\n" +
            "maxPoolSize: " + executor.getMaximumPoolSize();

        log.info(msg);

        return executor;
    }
}
