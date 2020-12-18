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

import java.io.Serializable;
import java.util.*;

import org.alfresco.model.ContentModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.OwnableService;
import org.alfresco.service.cmr.security.PersonService;

import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.security.NodeOwnerDAO;

/**
 * Notification Sender for documents (ItemType = NodeRef).
 *
 * The following implementation is used:
 * - subject line: default
 * - template: retrieved by key = node type
 * - template args:
 *   {
 *     "document": "nodeRef"
 *   }
 * - recipients: only a document owner receives notification
 *
 * @author Sergey Tiunov
 */
public class DocumentNotificationSender extends AbstractNotificationSender<NodeRef>
{
    public static final String ARG_DOCUMENT = "document";
    public static final String ARG_ADDITION = "addition";
    public static final String ARG_MODIFIER = "modifier";
    public HashMap<String, Object> add;
    protected OwnableService ownableService;
    protected PersonService personService;
    protected AuthenticationService authenticationService;
    protected boolean sendToOwner;
    protected Set<String> documentSubscribers;
    private NodeOwnerDAO nodeOwnerDAO;

    @Override
    public void setServiceRegistry(ServiceRegistry services) {
        super.setServiceRegistry(services);
        this.ownableService = services.getOwnableService();
        this.authenticationService = services.getAuthenticationService();
        this.personService = services.getPersonService();
    }

    @Override
    protected NodeRef getNotificationTemplate(NodeRef item) {
        String type = nodeService.getType(item).toPrefixString(namespaceService);
        return getNotificationTemplate(type);
    }

    @Override
    protected Map<String, Serializable> getNotificationArgs(NodeRef item) {
        Map<String, Serializable> args = new HashMap<>();
        args.put(ARG_DOCUMENT, item);
        args.put(ARG_ADDITION, add);
        String userName = authenticationService.getCurrentUserName();
        NodeRef person = personService.getPerson(userName);
        String lastName = (String) nodeService.getProperty(person, ContentModel.PROP_FIRSTNAME);
        String firstName = (String) nodeService.getProperty(person, ContentModel.PROP_LASTNAME);
        args.put(ARG_MODIFIER, lastName + " " + firstName);
        return args;
    }

    @Override
    protected Map<String, Object> getEcosNotificationArgs(NodeRef item) {
        Map<String, Object> args = super.getEcosNotificationArgs(item);

        args.put("_record", RecordRef.valueOf(item.toString()));

        Map<String, Object> addiction = new HashMap<>();
        add.forEach((key, value) -> {
            if (value instanceof NodeRef) {
                addiction.put(key, RecordRef.valueOf(value.toString()));
            } else {
                addiction.put(key, value);
            }
        });
        args.put(ARG_ADDITION, addiction);

        return args;
    }

    @Override
    protected Collection<String> getNotificationRecipients(NodeRef item) {
        Set<String> recipients = new HashSet<>();
        // add default recipients:
        if (defaultRecipients != null) {
            recipients.addAll(defaultRecipients);
        }
        if (documentSubscribers != null) {
            recipients.addAll(documentSubscribers);
        }
        recipients.addAll(getRecipients(item, getNotificationTemplate(item), item));
        return recipients;
    }

    public void setAdditionArgs(HashMap<String, Object> addition) {
        this.add = addition;
    }
    /**
    * Include owner of document to recipients
    * @param true or false
    */
    public void setSendToOwner(Boolean sendToOwner) {
        this.sendToOwner = sendToOwner;
    }

    /**
     * Recipients provided as parameter documentSubscribers: "recepient field1", ...
     * @param document subscribers
     */
    public void setDocumentSubscribers(Set<String> documentSubscribers) {
        this.documentSubscribers = documentSubscribers;
    }

    public void setNodeOwnerDAO(NodeOwnerDAO nodeOwnerDAO) {
        this.nodeOwnerDAO = nodeOwnerDAO;
    }

    protected void sendToAssignee(NodeRef item, Set<String> authorities) {
        // empty
    }

    protected void sendToInitiator(NodeRef item, Set<String> authorities) {
        // empty
    }

    protected void sendToOwner(Set<String> authorities, NodeRef node) {
        String owner = nodeOwnerDAO.getOwner(node);
        authorities.add(owner);
    }

    protected void sendToSubscribers(NodeRef item, Set<String> authorities, List<String> taskSubscribers) {
    }
}
