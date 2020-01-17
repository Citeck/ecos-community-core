/*
 * Copyright (C) 2008-2015 Citeck LLC.
 *
 * This file is part of Citeck EcoS
 *
 * Citeck EcoS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Citeck EcoS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Citeck EcoS. If not, see <http://www.gnu.org/licenses/>.
 */
package ru.citeck.ecos.notification;

import java.io.*;
import java.util.*;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.executer.MailActionExecuter;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.util.Pair;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.InputStreamSource;
import org.springframework.mail.javamail.MimeMessageHelper;

public class MailAttachActionExecutor extends MailActionExecuter {
    private static Log logger = LogFactory.getLog(MailAttachActionExecutor.class);

    private ContentService contentService;

    @Override
    public MimeMessageHelper prepareEmail(Action ruleAction, NodeRef actionedUponNodeRef, Pair<String, Locale> recipient, Pair<InternetAddress, Locale> sender) {
        MimeMessageHelper mimeMessageHelper = super.prepareEmail(ruleAction, actionedUponNodeRef, recipient, sender);
        MimeMessageHelper helper = mimeMessageHelper;
        logger.debug("actionedUponNodeRef " + actionedUponNodeRef);
        logger.debug("ruleAction.getParameterValues " + ruleAction.getParameterValues());
        Map<String, Object> template_modelParameterValue = (Map<String, Object>) ruleAction.getParameterValue("template_model");
        if (template_modelParameterValue != null) {
            Map<String, Object> argsParameterValues = (Map<String, Object>) template_modelParameterValue.get("args");
            if (argsParameterValues != null) {
                ArrayList attachments = (ArrayList) argsParameterValues.get("attachments");
                logger.debug("helper.getEncoding() 1 " + helper.getEncoding());
                String enc = helper.getEncoding();
                if (attachments != null) {
                    try {
                        logger.debug("attachments != null");
                        MimeMessage attachment = mimeMessageHelper.getMimeMessage();
                        MimeMessage attach = new MimeMessage(attachment);
                        helper = new MimeMessageHelper(attach, true, enc);
                        Object name = attachment.getContent();
                        logger.debug("name " + name);
                        if (name == null) {
                            throw new AlfrescoRuntimeException("You need to set body of the message");
                        }

                        helper.setText(name.toString(), true);
                    } catch (MessagingException var15) {
                        throw new AlfrescoRuntimeException("System can't create MimeMessage. " + var15.getMessage());
                    } catch (IOException var16) {
                        throw new AlfrescoRuntimeException("You need to set body of the message");
                    }

                    Iterator iter = attachments.iterator();

                    while (iter.hasNext()) {
                        Map<String, Serializable> attachment1 = (Map<String, Serializable>) iter.next();
                        logger.debug("attachment1 " + attachment1);
                        String name1 = (String) attachment1.get("name");
                        logger.debug("name1 " + name1);
                        final Object attachmentContentObject = attachment1.get("attachmentContent");
                        byte[] attachmentContent;
                        if (attachmentContentObject instanceof List) {
                            attachmentContent = new byte[((List) attachmentContentObject).size()];
                            for (int i = 0; i < ((List) attachmentContentObject).size(); i++) {
                                byte b = (byte) ((List) attachmentContentObject).get(i);
                                attachmentContent[i] = b;
                            }
                        } else if (attachmentContentObject instanceof NodeRef) {
                            ContentReader contentReader = contentService.getReader((NodeRef) attachmentContentObject, ContentModel.PROP_CONTENT);
                            try (InputStream in = contentReader.getContentInputStream();
                                 InputStream nodeIS = new BufferedInputStream(in, 4096)) {
                                attachmentContent = IOUtils.toByteArray(nodeIS);
                            } catch (Exception e) {
                                logger.error("MailAttachActionExecutor: cannot get byte array from content of node " + attachmentContentObject);
                                e.printStackTrace();
                                continue;
                            }
                        } else {
                            attachmentContent = (byte[]) attachmentContentObject;
                        }
                        logger.debug("attachmentContent " + attachmentContent.length);

                        InputStreamSource inputStreamSource = () -> new ByteArrayInputStream(attachmentContent);

                        try {
                            helper.addAttachment(name1, inputStreamSource);
                        } catch (MessagingException var14) {
                            logger.error("System can't add attachment. " + var14.getMessage());
                        }
                    }
                }
            }
        }

        return helper;
    }

    public void setContentService(ContentService contentService) {
        this.contentService = contentService;
    }
}
