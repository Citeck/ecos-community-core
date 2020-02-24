package ru.citeck.ecos.node;

import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.model.IdocsModel;
import ru.citeck.ecos.commons.utils.StringUtils;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.util.Map;

@Configuration
public class IdocsDisplayNameConfiguration {

    @Autowired
    private DisplayNameService displayNameService;

    @PostConstruct
    public void init() {
        displayNameService.register(IdocsModel.TYPE_CONTRACTOR, this::evalContractorDisplayName);
    }

    public String evalContractorDisplayName(AlfNodeInfo info) {

        Map<QName, Serializable> props = info.getProperties();
        String shortName = (String) props.get(IdocsModel.PROP_SHORT_ORGANIZATION_NAME);
        String fullName = (String) props.get(IdocsModel.PROP_FULL_ORG_NAME);

        if (StringUtils.isNotBlank(shortName)) {
            return shortName;
        } else if (StringUtils.isNotBlank(fullName)) {
            return fullName;
        }

        return null;
    }
}
