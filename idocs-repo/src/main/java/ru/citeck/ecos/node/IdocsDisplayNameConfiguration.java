package ru.citeck.ecos.node;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.commons.utils.StringUtils;
import ru.citeck.ecos.model.IdocsModel;

import javax.annotation.PostConstruct;

@Configuration
public class IdocsDisplayNameConfiguration {

    @Autowired
    private DisplayNameService displayNameService;

    @PostConstruct
    public void init() {
        displayNameService.register(IdocsModel.TYPE_CONTRACTOR, this::evalContractorDisplayName);
    }

    public String evalContractorDisplayName(AlfNodeInfo info) {
        String shortName = (String) info.getProperty(IdocsModel.PROP_SHORT_ORGANIZATION_NAME);
        if (StringUtils.isNotBlank(shortName)) {
            return shortName;
        }

        String fullName = (String) info.getProperty(IdocsModel.PROP_FULL_ORG_NAME);
        if (StringUtils.isNotBlank(fullName)) {
            return fullName;
        }

        return null;
    }
}
