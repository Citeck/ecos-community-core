package ru.citeck.ecos.domain.patch;

import lombok.Getter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commands.CommandsServiceFactoryConfig;
import ru.citeck.ecos.config.lib.consumer.bean.BeanConsumerService;
import ru.citeck.ecos.eapps.EcosAppsFactoryConfig;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.webapp.lib.patch.annotaion.EcosPatch;
import ru.citeck.ecos.webapp.lib.patch.annotaion.PatchBeansArtifactsProvider;

@Component
public class EcosPatchBeansPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    private ApplicationContext context;

    @Getter(lazy = true)
    private final PatchBeansArtifactsProvider patchBeans = context.getBean(PatchBeansArtifactsProvider.class);

    @Override
    public Object postProcessBeforeInitialization(Object bean, String id) throws BeansException {

        if (bean instanceof RecordsServiceFactory
            || bean instanceof EcosAppsFactoryConfig
            || bean instanceof CommandsServiceFactoryConfig
            || bean instanceof BeanConsumerService
            || !bean.getClass().getName().startsWith("ru.citeck.ecos")) {

            return bean;
        }

        Class<?> clazz = bean.getClass();

        if (clazz.isSynthetic() || clazz.isAnonymousClass()) {
            return bean;
        }

        if (clazz.getAnnotation(EcosPatch.class) != null) {
            getPatchBeans().registerBeanIdPatch(bean, id);
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
