package ru.citeck.ecos.records;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.eureka.EurekaAlfInstanceConfig;
import ru.citeck.ecos.records3.RecordsProperties;

@Configuration
public class RecordsPropertiesConfiguration {

    @Autowired
    private EurekaAlfInstanceConfig eurekaAlfInstanceConfig;

    @Bean
    public RecordsProperties createRecordsProperties() {
        RecordsProperties properties = new RecordsProperties();
        properties.setAppName(eurekaAlfInstanceConfig.getAppname());
        properties.setAppInstanceId(eurekaAlfInstanceConfig.getInstanceId());
        return properties;
    }
}
