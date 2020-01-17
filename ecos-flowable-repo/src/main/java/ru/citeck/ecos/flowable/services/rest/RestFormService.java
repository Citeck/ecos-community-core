package ru.citeck.ecos.flowable.services.rest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.flowable.form.model.SimpleFormModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;

@Service
public class RestFormService {

    private static final String FLOWABLE_REST_API_KEY = "${flowable.rest-api.url}";

    private static final Log logger = LogFactory.getLog(RestFormService.class);

    @Value(FLOWABLE_REST_API_KEY)
    private String restApiUrl;

    @Value("${flowable.rest-api.form.form-definitions-by-key}")
    private String formDefinitionsByKeyUrl;
    @Value("${flowable.rest-api.form.form-model}")
    private String formModelUrl;

    private String baseUrl;

    private boolean initialized = false;

    @Autowired
    private FlowableRestTemplate restTemplate;

    @PostConstruct
    public void init() {

        if (FLOWABLE_REST_API_KEY.equals(restApiUrl)) {
            logger.error("Flowable rest api url is not defined!");
            return;
        }

        baseUrl = restApiUrl.endsWith("/") ? restApiUrl : restApiUrl + "/";
        baseUrl += "form-api/form-repository/";

        initialized = true;
    }

    public Optional<SimpleFormModel> getFormByKey(String formKey) {

        if (!initialized) {
            logger.warn("Flowable rest form service is not initialized! FormKey: " + formKey);
            return Optional.empty();
        }

        String url = baseUrl + formDefinitionsByKeyUrl;
        FormDefinitions definitions = restTemplate.getForObject(url, FormDefinitions.class, formKey);

        if (definitions.data != null && !definitions.data.isEmpty()) {

            String formDefinitionId = definitions.data.get(0).id;
            url = baseUrl + formModelUrl;

            return Optional.ofNullable(restTemplate.getForObject(url, SimpleFormModel.class, formDefinitionId));
        }

        return Optional.empty();
    }

    public boolean hasFormWithKey(String formKey) {
        if (initialized) {
            String url = baseUrl + formDefinitionsByKeyUrl;
            FormDefinitions definitions = restTemplate.getForObject(url, FormDefinitions.class, formKey);
            return definitions.data != null && !definitions.data.isEmpty();
        }
        return false;
    }

    private static class FormDefinitions {

        public Integer total;
        public Integer start;
        public String sort;
        public String order;
        public Integer size;
        public List<Data> data;

        public static class Data {
            public String id;
            public String url;
            public String category;
            public String name;
            public String key;
            public String description;
            public Integer version;
            public String resourceName;
            public String deploymentId;
            public String parentDeploymentId;
            public String tenantId;
        }
    }
}
