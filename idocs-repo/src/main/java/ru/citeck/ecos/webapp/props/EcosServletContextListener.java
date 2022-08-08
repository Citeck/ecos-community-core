package ru.citeck.ecos.webapp.props;

import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ContextLoader;

import javax.servlet.ServletContext;
import java.lang.reflect.Method;

/**
 * Hack with reflection to add our ApplicationContextInitializer to spring context.
 * In new versions of spring this solution may be replaced by spring.factories
 */
public class EcosServletContextListener implements WebApplicationInitializer {

    private static final String SET_INIT_PARAM_METHOD_NAME = "setInitParameter";

    @Override
    public void onStartup(ServletContext servletContext) {
        Object setParamRes;
        try {
            Method method = servletContext.getClass().getMethod(
                SET_INIT_PARAM_METHOD_NAME,
                String.class,
                String.class
            );
            setParamRes = method.invoke(
                servletContext,
                ContextLoader.CONTEXT_INITIALIZER_CLASSES_PARAM,
                EcosPropsContextInitializer.class.getCanonicalName()
            );
        } catch (Exception e) {
            throw new RuntimeException(SET_INIT_PARAM_METHOD_NAME + " method is not found", e);
        }
        if (!Boolean.TRUE.equals(setParamRes)) {
            throw new RuntimeException("Init parameter is not set. Result: " + setParamRes);
        }
    }
}
