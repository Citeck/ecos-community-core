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
package ru.citeck.ecos.behavior.common;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.dictionary.constraint.ListOfValuesConstraint;
import org.alfresco.repo.i18n.MessageService;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.workflow.WorkflowQNameConverter;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.*;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import ru.citeck.ecos.behavior.JavaBehaviour;
import ru.citeck.ecos.notification.DocumentNotificationSender;
import ru.citeck.ecos.processor.ProcessorHelper;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.security.NodeOwnerDAO;
import ru.citeck.ecos.service.AlfrescoServices;

import java.io.Serializable;
import java.util.*;

public class DocumentChangeSendNotificationBehaviour implements NodeServicePolicies.OnUpdatePropertiesPolicy, NodeServicePolicies.OnCreateAssociationPolicy,
        NodeServicePolicies.OnDeleteAssociationPolicy, NodeServicePolicies.OnCreateChildAssociationPolicy, NodeServicePolicies.OnDeleteChildAssociationPolicy,
        NodeServicePolicies.OnCreateNodePolicy {
    public enum PropertiesMode {INCLUDE, EXCLUDE}

    // common properties
    protected PolicyComponent policyComponent;
    protected NodeService nodeService;
    protected NamespaceService namespaceService;
    protected ServiceRegistry services;
    protected WorkflowQNameConverter qNameConverter;
    protected DictionaryService dictionaryService;
    protected DocumentNotificationSender sender;
    protected ProcessorHelper helper;
    protected MessageService messageService;

    // distinct properties
    protected QName className;
    protected String nameTemplate;
    protected PropertiesMode propertiesMode;
    protected List<String> allowedProperties;
    protected String subscriberAssoc;
    protected Map<String,List<String>> subscribers;
    private static final Log logger = LogFactory.getLog(DocumentChangeSendNotificationBehaviour.class);
    protected boolean enabled;
    protected boolean includeOwnerToSubs;
    protected boolean sendOnCreate;
    protected boolean defaultSubscribe;
    private NodeOwnerDAO nodeOwnerDAO;
    private PersonService personService;

    public void init() {
        policyComponent.bindClassBehaviour(NodeServicePolicies.OnUpdatePropertiesPolicy.QNAME, className,
                new JavaBehaviour(this, "onUpdateProperties", NotificationFrequency.TRANSACTION_COMMIT));

        policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnCreateAssociationPolicy.QNAME, className,
                new JavaBehaviour(this, "onCreateAssociation", NotificationFrequency.TRANSACTION_COMMIT));

        policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnDeleteAssociationPolicy.QNAME, className,
                new JavaBehaviour(this, "onDeleteAssociation", NotificationFrequency.TRANSACTION_COMMIT)
        );
        policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnCreateChildAssociationPolicy.QNAME, className,
                new JavaBehaviour(this, "onCreateChildAssociation", NotificationFrequency.TRANSACTION_COMMIT)
        );
        policyComponent.bindAssociationBehaviour(NodeServicePolicies.OnDeleteChildAssociationPolicy.QNAME, className,
                new JavaBehaviour(this, "onDeleteChildAssociation", NotificationFrequency.TRANSACTION_COMMIT)
        );
        policyComponent.bindClassBehaviour(NodeServicePolicies.OnCreateNodePolicy.QNAME, className,
                new JavaBehaviour(this, "onCreateNode", NotificationFrequency.TRANSACTION_COMMIT));

        if(propertiesMode == null) propertiesMode = PropertiesMode.INCLUDE;
    }

    @Override
    public void onUpdateProperties(final NodeRef nodeRef, Map<QName, Serializable> before, Map<QName, Serializable> after) {
        logger.debug("onUpdateProperties event");
        if (enabled && nodeService.exists(nodeRef)) {
            Map<QName, Serializable> properties = nodeService.getProperties(nodeRef);
            ArrayList<Object> listProperties = new ArrayList<>();
            HashMap<String, Object> addition = new HashMap<>();
            for (Map.Entry<QName, Serializable> entry : properties.entrySet()) {
                HashMap<String, Object> changedProperties = new HashMap<>();
                Object propBefore = (Object) before.get(entry.getKey());
                Object propAfter = (Object) after.get(entry.getKey());

                String propertyName = qNameConverter.mapQNameToName(entry.getKey());
                boolean isContains = allowedProperties != null && allowedProperties.contains(propertyName);
                if (propertiesMode == PropertiesMode.INCLUDE && isContains
                    || propertiesMode == PropertiesMode.EXCLUDE && !isContains) {

                    if (!Objects.equals(propBefore, propAfter)) {
                        PropertyDefinition propDefinition = dictionaryService.getProperty(entry.getKey());
                        String propTitle = null;
                        if (propDefinition != null) {
                            propTitle = propDefinition.getTitle();
                            List<ConstraintDefinition> cdef = propDefinition.getConstraints();
                            if (cdef != null) {
                                ListIterator constrIter = cdef.listIterator();
                                while (constrIter.hasNext()) {
                                    Constraint constrDef2 = ((ConstraintDefinition) constrIter.next()).getConstraint();
                                    if (constrDef2 instanceof ListOfValuesConstraint) {
                                        ListOfValuesConstraint listConstraint = (ListOfValuesConstraint) constrDef2;
                                        propBefore = listConstraint.getDisplayLabel(String.valueOf(propBefore), messageService);
                                        propAfter = listConstraint.getDisplayLabel(String.valueOf(propAfter), messageService);
                                    }

                                }
                            }
                        }
                        if (propTitle == null) {
                            AspectDefinition aspectDefinition = dictionaryService.getAspect(entry.getKey());
                            if (aspectDefinition != null) {
                                Map<QName, PropertyDefinition> apropers = aspectDefinition.getProperties();
                                if (apropers != null && apropers.get(entry.getKey()) != null) {
                                    propTitle = apropers.get(entry.getKey()).getTitle();
                                    List<ConstraintDefinition> cdef = apropers.get(entry.getKey()).getConstraints();
                                    if (cdef != null) {
                                        ListIterator constrIter = cdef.listIterator();
                                        while (constrIter.hasNext()) {
                                            Constraint constrDef2 = ((ConstraintDefinition) constrIter.next()).getConstraint();
                                            if (constrDef2 instanceof ListOfValuesConstraint) {
                                                ListOfValuesConstraint listConstraint = (ListOfValuesConstraint) constrDef2;
                                                propBefore = listConstraint.getDisplayLabel(String.valueOf(propBefore), messageService);
                                                propAfter = listConstraint.getDisplayLabel(String.valueOf(propAfter), messageService);
                                            }

                                        }
                                    }
                                }
                            }
                        }
                        if (propTitle == null) {
                            propTitle = entry.getKey().getLocalName();
                        }
                        changedProperties.put("title", propTitle);
                        changedProperties.put("before", propBefore);
                        changedProperties.put("after", propAfter);
                        listProperties.add(changedProperties);
                    }
                }
            }
            //check if edit doc
            if (before.get(ContentModel.PROP_NODE_UUID) != null) {
                logger.debug("onUpdateProperties edit mode");
                addition.put("mode", "edit_mode");
                if (!listProperties.isEmpty()) {
                    addition.put("properties", listProperties);
                    if (sender != null) {
                        sender.setAdditionArgs(addition);
                        sender.setDocumentSubscribers(selectSubscribers(nodeRef, "edit_assoc"));
                        sender.setSubject("Документ изменен");
                        sender.sendNotification(nodeRef);
                    }
                }
            } else {
                //on create doc
                logger.debug("created new document");
                if (sendOnCreate) {
                    logger.debug("sendOnCreate enabled");
                    addition.put("properties", listProperties);
                    addition.put("mode", "create_mode");
                    if (sender != null) {
                        sender.setAdditionArgs(addition);
                        sender.setDocumentSubscribers(selectSubscribers(nodeRef, "create"));
                        sender.setSubject("Создан документ");
                        sender.sendNotification(nodeRef);
                    }
                }
            }
        }
    }

    @Override
    public void onCreateAssociation(AssociationRef nodeAssocRef) {
        logger.debug("onCreateAssociation event");
        AssociationDefinition assoc = dictionaryService.getAssociation(nodeAssocRef.getTypeQName());

        String assocName = qNameConverter.mapQNameToName(nodeAssocRef.getTypeQName());
        boolean isContains = allowedProperties != null && allowedProperties.contains(assocName);
        if (enabled && (propertiesMode == PropertiesMode.INCLUDE && isContains
            || propertiesMode == PropertiesMode.EXCLUDE && !isContains)) {

            HashMap<String, Object> changedProperties = new HashMap<>();
            changedProperties.put("event", "added");
            ArrayList<Object> listProperties = new ArrayList<>();
            String propTitle = null;
            HashMap<String, Object> addition = new HashMap<>();
            if (assoc != null) {
                propTitle = assoc.getTitle();
            }
            changedProperties.put("type", propTitle);
            NodeRef nodeTarget = nodeAssocRef.getTargetRef();
            NodeRef nodeSource = nodeAssocRef.getSourceRef();
            if (nodeService.exists(nodeTarget)) {
                changedProperties.put("target", nodeTarget);
                changedProperties.put("targetRef", RecordRef.valueOf(nodeTarget.toString()));
            }
            if (nodeService.exists(nodeSource)) {
                changedProperties.put("source", nodeSource);
                changedProperties.put("sourceRef", RecordRef.valueOf(nodeTarget.toString()));
                listProperties.add(changedProperties);
                if (!listProperties.isEmpty()) {
                    addition.put("mode", "edit_mode");
                    addition.put("properties", listProperties);
                    if (sender != null && nodeService.exists(nodeSource)) {
                        sender.setAdditionArgs(addition);
                        sender.setDocumentSubscribers(selectSubscribers(nodeSource, "edit_assoc"));
                        sender.setSubject("Документ изменен");
                        sender.sendNotification(nodeSource);
                    }
                }
            }
        }
        if (defaultSubscribe) {
            addAssociasionSubscribersOnEdit(nodeAssocRef);
        }
    }

    @Override
    public void onDeleteAssociation(AssociationRef nodeAssocRef) {
        logger.debug("onDeleteAssociation event");
        HashMap<String, Object> changedProperties = new HashMap<>();
        changedProperties.put("event", "deleted");
        AssociationDefinition assoc = dictionaryService.getAssociation(nodeAssocRef.getTypeQName());

        String assocName = qNameConverter.mapQNameToName(nodeAssocRef.getTypeQName());
        boolean isContains = allowedProperties != null && allowedProperties.contains(assocName);
        if (enabled && (propertiesMode == PropertiesMode.INCLUDE && isContains
            || propertiesMode == PropertiesMode.EXCLUDE && !isContains)) {

            ArrayList<Object> listProperties = new ArrayList<>();
            String propTitle = null;
            HashMap<String, Object> addition = new HashMap<>();
            if (assoc != null) {
                propTitle = assoc.getTitle();
            }
            changedProperties.put("type", propTitle);
            NodeRef nodeTarget = nodeAssocRef.getTargetRef();
            NodeRef nodeSource = nodeAssocRef.getSourceRef();
            if (nodeService.exists(nodeTarget)) {
                changedProperties.put("target", nodeTarget);
                changedProperties.put("targetRef", RecordRef.valueOf(nodeTarget.toString()));
            }
            if (nodeService.exists(nodeSource)) {
                changedProperties.put("source", nodeSource);
                changedProperties.put("sourceRef", RecordRef.valueOf(nodeSource.toString()));
                listProperties.add(changedProperties);
                if (!listProperties.isEmpty()) {
                    addition.put("mode", "edit_mode");
                    addition.put("properties", listProperties);
                    if (sender != null && nodeService.exists(nodeSource)) {
                        sender.setAdditionArgs(addition);
                        sender.setDocumentSubscribers(selectSubscribers(nodeSource, "edit_assoc"));
                        sender.setSubject("Документ изменен");
                        sender.sendNotification(nodeSource);
                    }
                }
            }
        }
        deleteAssociasionSubscribersOnEdit(nodeAssocRef);
    }

    @Override
    public void onCreateChildAssociation(ChildAssociationRef childAssociationRef, boolean isNew) {
        AssociationDefinition assoc = dictionaryService.getAssociation(childAssociationRef.getTypeQName());
        logger.debug("onCreateChildAssociation event");

        String assocName = qNameConverter.mapQNameToName(childAssociationRef.getTypeQName());
        boolean isContains = allowedProperties != null && allowedProperties.contains(assocName);
        if (enabled && (propertiesMode == PropertiesMode.INCLUDE && isContains
            || propertiesMode == PropertiesMode.EXCLUDE && !isContains)) {

            HashMap<String, Object> changedProperties = new HashMap<>();
            changedProperties.put("event", "added");
            ArrayList<Object> listProperties = new ArrayList<>();
            String propTitle = null;
            HashMap<String, Object> addition = new HashMap<>();
            if (assoc != null) {
                propTitle = assoc.getName().getLocalName();
            }
            changedProperties.put("type", propTitle);
            NodeRef nodeTarget = childAssociationRef.getChildRef();
            NodeRef nodeSource = childAssociationRef.getParentRef();
            if (nodeService.exists(nodeTarget)) {
                changedProperties.put("target", nodeTarget);
                changedProperties.put("targetRef", RecordRef.valueOf(nodeTarget.toString()));
            }
            if (nodeService.exists(nodeSource)) {
                changedProperties.put("source", nodeSource);
                changedProperties.put("sourceRef", RecordRef.valueOf(nodeSource.toString()));
                listProperties.add(changedProperties);
                if (!listProperties.isEmpty()) {
                    addition.put("mode", "edit_mode");
                    addition.put("properties", listProperties);
                    if (sender != null) {
                        sender.setAdditionArgs(addition);
                        sender.setDocumentSubscribers(selectSubscribers(nodeSource, "edit_assoc"));
                        sender.setSubject("Документ изменен");
                        sender.sendNotification(nodeSource);
                    }
                }
            }
        }

    }

    @Override
    public void onDeleteChildAssociation(ChildAssociationRef childAssociationRef) {
        logger.debug("onDeleteChildAssociation event");
        AssociationDefinition assoc = dictionaryService.getAssociation(childAssociationRef.getTypeQName());

        String assocName = qNameConverter.mapQNameToName(childAssociationRef.getTypeQName());
        boolean isContains = allowedProperties != null && allowedProperties.contains(assocName);
        if (enabled && (propertiesMode == PropertiesMode.INCLUDE && isContains
            || propertiesMode == PropertiesMode.EXCLUDE && !isContains)) {

            HashMap<String, Object> changedProperties = new HashMap<>();
            changedProperties.put("event", "deleted");
            ArrayList<Object> listProperties = new ArrayList<>();
            String propTitle = null;
            HashMap<String, Object> addition = new HashMap<>();
            if (assoc != null) {
                propTitle = assoc.getName().getLocalName();
            }
            changedProperties.put("type", propTitle);
            NodeRef nodeTarget = childAssociationRef.getChildRef();
            NodeRef nodeSource = childAssociationRef.getParentRef();
            if (nodeService.exists(nodeTarget)) {
                changedProperties.put("target", nodeTarget);
                changedProperties.put("targetRef", RecordRef.valueOf(nodeTarget.toString()));
            }
            if (nodeService.exists(nodeSource)) {
                changedProperties.put("source", nodeSource);
                changedProperties.put("sourceRef", RecordRef.valueOf(nodeTarget.toString()));
                listProperties.add(changedProperties);
                if (!listProperties.isEmpty()) {
                    addition.put("mode", "edit_mode");
                    addition.put("properties", listProperties);
                    if (sender != null) {
                        sender.setAdditionArgs(addition);
                        sender.setDocumentSubscribers(selectSubscribers(nodeSource, "edit_assoc"));
                        sender.setSubject("Документ изменен");
                        sender.sendNotification(nodeSource);
                    }
                }
            }
        }
    }

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.services = serviceRegistry;
        this.namespaceService = services.getNamespaceService();
        this.dictionaryService = services.getDictionaryService();
        this.qNameConverter = new WorkflowQNameConverter(namespaceService);
        this.personService = services.getPersonService();
        this.messageService = (MessageService) services.getService(AlfrescoServices.MESSAGE_SERVICE);
    }

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setClassName(QName className) {
        this.className = className;
    }

    /**
     * Set properties mode
     * @param propertiesMode allowed values: include, exclude
     */
    public void setPropertiesMode(PropertiesMode propertiesMode) {
        this.propertiesMode = propertiesMode;
    }

    public void setAllowedProperties(List<String> allowedProperties) {
        this.allowedProperties = allowedProperties;
    }

    public void setSubscribers(Map<String,List<String>> subscribers) {
        this.subscribers = subscribers;
    }
    /**
     * Set NotificationSender.
     * @param sender
     */
    public void setSender(DocumentNotificationSender sender) {
        this.sender = sender;
    }
    public void setHelper(ProcessorHelper helper) {
        this.helper = helper;
    }
    /**
    * enabled
    * @param true or false
    */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled.booleanValue();
    }

    /**
    * send notification on create document
    * @param true or false
    */
    public void setSendOnCreate(Boolean sendOnCreate) {
        this.sendOnCreate = sendOnCreate.booleanValue();
    }

    /**
    * subscribe on notification by default
    * @param true or false
    */
    public void setDefaultSubscribe(Boolean defaultSubscribe) {
        this.defaultSubscribe = defaultSubscribe.booleanValue();
    }

    public Set<String> selectSubscribers(NodeRef nodeRef, String mode)
    {
        Set<String> subs = new HashSet<>();
        if (subscribers != null) {
            List<String> listSubs = subscribers.get(mode);
            ListIterator iter = listSubs.listIterator();
            while (iter.hasNext()) {
                String property = iter.next().toString();
                if (property!=null) {
                    QName subscriber = qNameConverter.mapNameToQName(property);
                    Collection<AssociationRef> assocs = nodeService.getTargetAssocs(nodeRef, subscriber);
                    logger.debug("assocs " + assocs.size());
                    for (AssociationRef assoc : assocs) {
                        NodeRef ref = assoc.getTargetRef();
                        String subscriberName = (String) nodeService.getProperty(ref, ContentModel.PROP_USERNAME);
                        logger.debug("subscriberName " + subscriberName);
                        subs.add(subscriberName);
                    }
                }
            }
        }
        return subs;
    }

    public void setSubscriberAssoc(String subscriberAssoc) {
        this.subscriberAssoc = subscriberAssoc;
    }

    /**
    * Include owner of document to recipients
    * @param true or false
    */
    public void setIncludeOwnerToSubs(Boolean includeOwnerToSubs) {
        this.includeOwnerToSubs = includeOwnerToSubs.booleanValue();
    }

    public void createAssociasionSubscribersOnEdit(NodeRef nodeRef) {
        if (subscribers != null) {
            List<String> listSubs = subscribers.get("edit");
            ListIterator iter = listSubs.listIterator();
            logger.debug("subscriberAssoc " + subscriberAssoc);
            if (subscriberAssoc != null) {
                QName subscriberAssocQName = qNameConverter.mapNameToQName(subscriberAssoc);
                while (iter.hasNext()) {
                    String property = iter.next().toString();
                    if (property != null) {
                        QName subscriber = qNameConverter.mapNameToQName(property);
                        Collection<AssociationRef> assocs = nodeService.getTargetAssocs(nodeRef, subscriber);
                        for (AssociationRef assoc : assocs) {
                            NodeRef ref = assoc.getTargetRef();
                            try {
                                nodeService.createAssociation(nodeRef, ref, subscriberAssocQName);
                            } catch (AssociationExistsException e) {
                                logger.error("Association already exist " + Arrays.toString(e.getStackTrace()));
                            }
                        }
                    }

                    if (includeOwnerToSubs) {
                        createSubscriberAssociationForOwner(nodeRef, subscriberAssocQName);
                    }
                }
            }
        }
    }

    public void createSubscriberAssociationForOwner(NodeRef nodeRef, QName subscriberAssocQName)
    {
        try
        {
            String owner = nodeOwnerDAO.getOwner(nodeRef);
            NodeRef ownerRef = personService.getPerson(owner, false);
            nodeService.createAssociation(nodeRef, ownerRef, subscriberAssocQName);
        }
        catch (AssociationExistsException e)
        {
            logger.error("Association already exist "+ Arrays.toString(e.getStackTrace()));
        }
    }

    public void addAssociasionSubscribersOnEdit(AssociationRef nodeAssocRef) {
        if (subscribers != null) {
            List<String> listSubs = subscribers.get("edit");
            ListIterator iter = listSubs.listIterator();
            logger.debug("subscriberAssoc " + subscriberAssoc);
            if (subscriberAssoc != null) {
                QName subscriberAssocQName = qNameConverter.mapNameToQName(subscriberAssoc);
                while (iter.hasNext()) {
                    String property = iter.next().toString();
                    if (property != null) {
                        QName subscriber = qNameConverter.mapNameToQName(property);
                        if (subscriber.equals(nodeAssocRef.getTypeQName())) {
                            NodeRef nodeTarget = nodeAssocRef.getTargetRef();
                            NodeRef nodeSource = nodeAssocRef.getSourceRef();
                            try {
                                if (nodeService.exists(nodeSource) && nodeService.exists(nodeTarget)) {
                                    nodeService.createAssociation(nodeSource, nodeTarget, subscriberAssocQName);
                                }
                            } catch (AssociationExistsException e) {
                                logger.error("Association already exist " + Arrays.toString(e.getStackTrace()));
                            }
                        }
                    }
                }
            }
        }
    }

    public void deleteAssociasionSubscribersOnEdit(AssociationRef nodeAssocRef) {
        if(subscribers!=null)
        {
            List<String> listSubs = subscribers.get("edit");
            ListIterator iter = listSubs.listIterator();
            logger.debug("subscriberAssoc "+subscriberAssoc);
            if(subscriberAssoc!=null)
            {
                QName subscriberAssocQName = qNameConverter.mapNameToQName(subscriberAssoc);
                while (iter.hasNext())
                {
                    String property = iter.next().toString();
                    if(property!=null)
                    {
                        QName subscriber = qNameConverter.mapNameToQName(property);
                        if(subscriber.equals(nodeAssocRef.getTypeQName()))
                        {
                            NodeRef nodeTarget = nodeAssocRef.getTargetRef();
                            NodeRef nodeSource = nodeAssocRef.getSourceRef();
                            try
                            {
                                if(nodeService.exists(nodeSource) && nodeService.exists(nodeTarget))
                                {
                                    nodeService.removeAssociation(nodeSource, nodeTarget, subscriberAssocQName);
                                }
                            }
                            catch (InvalidNodeRefException e)
                            {
                                logger.error("InvalidNodeRefException "+ Arrays.toString(e.getStackTrace()));
                            }
                        }
                    }
                }
            }
        }
    }
    public void setNodeOwnerDAO(NodeOwnerDAO nodeOwnerDAO) {
        this.nodeOwnerDAO = nodeOwnerDAO;
    }

    @Override
    public void onCreateNode(ChildAssociationRef childAssocRef)
    {
        NodeRef nodeRef = childAssocRef.getChildRef();
        if(nodeService.exists(nodeRef) && defaultSubscribe)
        {
            createAssociasionSubscribersOnEdit(nodeRef);
        }

    }

}
