package org.alfresco.repo.i18n;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.extensions.surf.util.I18NUtil;

import java.util.*;

/**
 * ECOS Override for MessageService
 */
public class MessageServiceImpl extends AlfMessageServiceImpl {

    @Value("${ecos.message-service.static-messages-enabled}")
    private boolean isStaticMessagesEnabled = false;

    @Override
    public String getMessage(final String messageKey, final Locale locale) {
        if (isStaticMessagesEnabled) {
            return I18NUtil.getMessage(messageKey, locale);
        }
        return super.getMessage(messageKey, locale);
    }

    public void setStaticMessagesEnabled(boolean staticMessagesEnabled) {
        isStaticMessagesEnabled = staticMessagesEnabled;
    }
}
