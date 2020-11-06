package ru.citeck.ecos.records;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.records3.RecordsProperties;

@Configuration
public class RecordsPropertiesConfiguration {

    @Bean
    public RecordsProperties createRecordsProperties() {
        //TODO: fix bugs before
        /*RecordsProperties properties = super.createProperties();
        properties.setAppName(appName);
        return properties;*/
        return new RecordsProperties();
    }
}
