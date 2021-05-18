/*
 * Copyright (C) 2005-2015 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
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

    private static final Log logger = LogFactory.getLog(MessageServiceImpl.class);

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
