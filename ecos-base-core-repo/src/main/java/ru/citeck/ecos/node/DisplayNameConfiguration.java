package ru.citeck.ecos.node;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.MLText;
import org.alfresco.service.cmr.security.AuthorityType;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.io.Serializable;

@Configuration
public class DisplayNameConfiguration {

    @Autowired
    private DisplayNameService displayNameService;

    @PostConstruct
    public void init() {
        displayNameService.register(ContentModel.TYPE_PERSON, this::evalPersonDisplayName);
        displayNameService.register(ContentModel.TYPE_CMOBJECT, this::evalDefaultDisplayName);
        displayNameService.register(ContentModel.TYPE_AUTHORITY_CONTAINER, this::evalAuthorityContainerDisplayName);
    }

    private String evalPersonDisplayName(AlfNodeInfo info) {

        String firstName = (String) info.getProperty(ContentModel.PROP_FIRSTNAME);
        String lastName = (String) info.getProperty(ContentModel.PROP_LASTNAME);

        StringBuilder result = new StringBuilder();
        if (StringUtils.isNotBlank(firstName)) {
            result.append(firstName);
        }
        if (StringUtils.isNotBlank(lastName)) {
            if (result.length() > 0) {
                result.append(" ");
            }
            result.append(lastName);
        }

        if (result.length() == 0) {
            String userName = (String) info.getProperty(ContentModel.PROP_USERNAME);
            if (StringUtils.isNotBlank(userName)) {
                result.append(userName);
            } else {
                result.append(info.getNodeRef());
            }
        }

        return result.toString();
    }

    private String evalAuthorityContainerDisplayName(AlfNodeInfo info) {
        String displayName = (String) info.getProperty(ContentModel.PROP_AUTHORITY_DISPLAY_NAME);
        if (StringUtils.isNotBlank(displayName)) {
            return displayName;
        }

        String authorityName = (String) info.getProperty(ContentModel.PROP_AUTHORITY_NAME);
        String authNameWithoutPrefix;
        if (authorityName != null) {
            authNameWithoutPrefix = authorityName.replaceFirst(AuthorityType.GROUP.getPrefixString(), "");
        } else {
            authNameWithoutPrefix = "";
        }
        return authNameWithoutPrefix;
    }

    public String evalDefaultDisplayName(AlfNodeInfo info) {
        Serializable titleValue = info.getProperty(ContentModel.PROP_TITLE);
        String title;
        if (titleValue instanceof MLText) {
            title = ((MLText) titleValue).getDefaultValue();
        } else {
            title = (String) titleValue;
        }

        String name = (String) info.getProperty(ContentModel.PROP_NAME);

        return StringUtils.isNotBlank(title) ? title : name;
    }
}
