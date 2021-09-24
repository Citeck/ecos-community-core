package ru.citeck.ecos.domain.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import ru.citeck.ecos.commands.CommandsServiceFactoryConfig;
import ru.citeck.ecos.config.lib.consumer.bean.BeanConsumerService;
import ru.citeck.ecos.config.lib.consumer.bean.EcosConfig;
import ru.citeck.ecos.eapps.EcosAppsFactoryConfig;
import ru.citeck.ecos.records3.RecordsServiceFactory;

@Slf4j
@Component
public class EcosConfigPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    private ApplicationContext context;

    @Getter(lazy = true)
    private final BeanConsumerService beanConsumerService = context.getBean(BeanConsumerService.class);

    @Override
    public Object postProcessBeforeInitialization(Object bean, String id) throws BeansException {

        if (bean instanceof RecordsServiceFactory
                || bean instanceof EcosAppsFactoryConfig
                || bean instanceof CommandsServiceFactoryConfig
                || bean instanceof BeanConsumerService
                || !bean.getClass().getName().startsWith("ru.citeck.ecos")) {

            return bean;
        }

        MutableBoolean hasConfig = new MutableBoolean(false);
        ReflectionUtils.doWithFields(bean.getClass(), field -> {
            if (field.getAnnotation(EcosConfig.class) != null) {
                hasConfig.setTrue();
            }
        });
        if (hasConfig.isFalse()) {
            ReflectionUtils.doWithMethods(bean.getClass(), method -> {
                if (method.getAnnotation(EcosConfig.class) != null) {
                    hasConfig.setTrue();
                }
            });
        }
        if (hasConfig.isTrue()) {
            log.info("Found bean with EcosConfig: " + bean.getClass());
            getBeanConsumerService().registerConsumers(bean);
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String id) throws BeansException {
        return bean;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.context = applicationContext;
    }
}
