package ru.citeck.ecos.flowable.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.module.AbstractModuleComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.repository.MLText;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import ru.citeck.ecos.config.EcosConfigService;
import ru.citeck.ecos.flowable.services.FlowableModelerService;
import ru.citeck.ecos.model.ConfigModel;
import ru.citeck.ecos.utils.ResourceResolver;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * @author Roman Makarskiy
 */
@Slf4j
public class FlowableModelerImportProcessModuleComponent extends AbstractModuleComponent {

    /**
     * Services
     */
    private RetryingTransactionHelper retryingTransactionHelper;
    private FlowableModelerService flowableModelerService;
    @Autowired
    private EcosConfigService ecosConfigService;
    @Autowired
    private ResourceResolver resourceResolver;

    private static final String IMPORT_CONFIG_KEY = "flowable-process-import-to-modeler-already-executed";
    private static final String CONFIG_PROPERTY_PATH = "alfresco/module/ecos-flowable-repo/bootstrap/configs/flowable-process-import-to-modeler-already-executed.properties";

    /**
     * Execute internal
     */
    @Override
    protected void executeInternal() {
        AuthenticationUtil.runAsSystem(() -> retryingTransactionHelper.doInTransaction(() -> {
            if (importRequired()) {
                flowableModelerService.importProcessModel();
            }
            return null;
        }));
    }

    /**
     * Check - is import required
     * @return Check result
     */
    private boolean importRequired() throws IOException {
        if (flowableModelerService == null || !flowableModelerService.importIsPossible()) {
            log.info("Cannot import process model, because flowable integration is not initialized.");
            return false;
        }

        String config = (String) ecosConfigService.getParamValue(IMPORT_CONFIG_KEY);
        if (config == null) {
            createConfig();
        }

        return true;
    }

    private void createConfig() throws IOException {
        Resource resource = resourceResolver.getResource("classpath:" + CONFIG_PROPERTY_PATH);
        Properties properties = new Properties();
        properties.load(resource.getInputStream());

        Map<QName, Serializable> configProps = new HashMap<>();
        configProps.put(ConfigModel.PROP_KEY, properties.getProperty("config.key"));
        configProps.put(ContentModel.PROP_NAME, properties.getProperty("config.name"));
        configProps.put(ConfigModel.PROP_VALUE, properties.getProperty("config.value"));

        Locale ru = new Locale("ru");
        MLText title = new MLText();
        title.addValue(ru, properties.getProperty("config.title_ru"));
        title.addValue(Locale.ENGLISH, properties.getProperty("config.title_en"));
        configProps.put(ContentModel.PROP_TITLE, title);

        MLText description = new MLText();
        description.addValue(ru, properties.getProperty("config.description_ru"));
        description.addValue(Locale.ENGLISH, properties.getProperty("config.description_en"));
        configProps.put(ContentModel.PROP_DESCRIPTION, description);

        ecosConfigService.createConfig(configProps);
    }

    /** Setters */

    public void setRetryingTransactionHelper(RetryingTransactionHelper retryingTransactionHelper) {
        this.retryingTransactionHelper = retryingTransactionHelper;
    }

    public void setFlowableModelerService(FlowableModelerService flowableModelerService) {
        this.flowableModelerService = flowableModelerService;
    }
}
