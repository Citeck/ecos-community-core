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
package ru.citeck.ecos.node;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.security.authentication.AuthenticationException;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.*;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.cmr.security.MutableAuthenticationService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.workflow.WorkflowInstance;
import org.alfresco.service.cmr.workflow.WorkflowTask;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.alfresco.util.GUID;
import org.alfresco.util.ISO8601DateFormat;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.surf.util.I18NUtil;
import ru.citeck.ecos.attr.NodeAttributeService;
import ru.citeck.ecos.behavior.AssociationIndexing;
import ru.citeck.ecos.model.CiteckWorkflowModel;
import ru.citeck.ecos.model.EcosModel;
import ru.citeck.ecos.service.CiteckServices;
import ru.citeck.ecos.utils.RepoUtils;

import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class NodeInfoFactoryImpl implements NodeInfoFactory {
    private static Log logger = LogFactory.getLog(NodeInfoFactoryImpl.class);

    private static final String CONTENT_URL = "url";
    private static final String CONTENT_MIMETYPE = "mimetype";
    private static final String CONTENT_ENCODING = "encoding";
    private static final String CONTENT_CONTENT = "content";

    private static final String ERROR_BAD_CREDENTIALS = "message.error-bad-credentials";
    private static final String ERROR_NO_PERMISSIONS = "message.error-you-have-no-permissions";

    private ServiceRegistry serviceRegistry;
    private NodeService nodeService;
    private DictionaryService dictionaryService;
    private NamespaceService namespaceService;
    private ContentService contentService;
    private MimetypeService mimetypeService;
    private NodeAttributeService nodeAttributeService;
    private AssociationIndexing associationIndexing;
    private PersonService personService;
    private AuthorityService authorityService;
    private MutableAuthenticationService authenticationService;
    /////////////////////////////////////////////////////////////////
    //                     GENERAL INTERFACE                       //
    /////////////////////////////////////////////////////////////////

    /* (non-Javadoc)
     * @see ru.citeck.ecos.node.NodeInfoService#createNodeInfo()
     */
    @Override
    public NodeInfo createNodeInfo() {
        return new NodeInfo();
    }

    /* (non-Javadoc)
     * @see ru.citeck.ecos.node.NodeInfoService#createNodeInfo(org.alfresco.service.cmr.repository.NodeRef)
     */
    @Override
    public NodeInfo createNodeInfo(NodeRef nodeRef) {
        if (!nodeService.exists(nodeRef)) {
            return null;
        }

        // create node info
        NodeInfo nodeInfo = new NodeInfo();

        // set information
        ChildAssociationRef primaryParentRef = nodeService.getPrimaryParent(nodeRef);
        nodeInfo.setNodeRef(nodeRef);
        nodeInfo.setType(nodeService.getType(nodeRef));
        nodeInfo.setAspects(nodeService.getAspects(nodeRef));
        nodeInfo.setParent(primaryParentRef != null ? primaryParentRef.getParentRef() : null);
        nodeInfo.setProperties(nodeService.getProperties(nodeRef));
        nodeInfo.setTargetAssocs(getAssocMap(nodeService.getTargetAssocs(nodeRef, RegexQNamePattern.MATCH_ALL), false));
        nodeInfo.setChildAssocs(getChildMap(nodeService.getChildAssocs(nodeRef), false));

        // return info
        return nodeInfo;
    }

    /* (non-Javadoc)
     * @see ru.citeck.ecos.node.NodeInfoService#createNodeInfo(org.alfresco.service.cmr.workflow.WorkflowTask)
     */
    @Override
    public NodeInfo createNodeInfo(WorkflowTask task) {
        NodeInfo nodeInfo = new NodeInfo();

        Map<QName, Serializable> all = task.getProperties();
        removeNotExistingNodeRefs(all);
        Map<QName, Serializable> props = new HashMap<>(all.size());
        Map<QName, List<NodeRef>> assocs = new HashMap<>(all.size() / 2);
        splitPropsAndAssocs(all, props, assocs);

        QName taskType = QName.createQName(task.getName(), namespaceService);
        TypeDefinition typeDef = dictionaryService.getType(taskType);
        if (typeDef == null) {
            taskType = WorkflowModel.TYPE_WORKFLOW_TASK;
        }

        props.put(CiteckWorkflowModel.PROP_DUE_DATE_TIME, props.get(WorkflowModel.PROP_DUE_DATE));

        nodeInfo.setType(taskType);
        nodeInfo.setProperties(props);
        nodeInfo.setTargetAssocs(assocs);

        return nodeInfo;
    }

    /* (non-Javadoc)
     * @see ru.citeck.ecos.node.NodeInfoService#createNodeInfo(org.alfresco.service.cmr.workflow.WorkflowInstance)
     */
    @Override
    public NodeInfo createNodeInfo(WorkflowInstance workflow) {
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setProperty(WorkflowModel.PROP_WORKFLOW_INSTANCE_ID, workflow.getId());
        nodeInfo.setProperty(WorkflowModel.PROP_WORKFLOW_DEFINITION_ID, workflow.getDefinition().getId());
        nodeInfo.setProperty(WorkflowModel.PROP_WORKFLOW_DESCRIPTION, workflow.getDescription());
        nodeInfo.setProperty(WorkflowModel.PROP_START_DATE, workflow.getStartDate());
        nodeInfo.setProperty(QName.createQName(NamespaceService.BPM_MODEL_1_0_URI, "endDate"), workflow.getEndDate());
        nodeInfo.setProperty(WorkflowModel.PROP_WORKFLOW_DUE_DATE, workflow.getDueDate());
        nodeInfo.setProperty(QName.createQName(NamespaceService.BPM_MODEL_1_0_URI, "initiator"), String.valueOf(workflow.getInitiator()));
        nodeInfo.setProperty(WorkflowModel.PROP_WORKFLOW_PRIORITY, workflow.getPriority());
        nodeInfo.setProperty(WorkflowModel.ASPECT_WORKFLOW_PACKAGE, String.valueOf(workflow.getWorkflowPackage()));
        return nodeInfo;
    }

    @Override
    public NodeInfo createNodeInfo(Map<QName, Object> attributes) {
        NodeInfo nodeInfo = createNodeInfo();
        setAttributes(nodeInfo, attributes);
        return nodeInfo;
    }

    @Override
    public void setAttributes(NodeInfo nodeInfo, Map<QName, Object> attributes) {
        nodeAttributeService.setAttributes(nodeInfo, attributes);
    }

    /* (non-Javadoc)
     * @see ru.citeck.ecos.node.NodeInfoService#persist(org.alfresco.service.cmr.repository.NodeRef, ru.citeck.ecos.node.NodeInfo)
     */
    @Override
    public void persist(NodeRef nodeRef, NodeInfo nodeInfo) {
        persist(nodeRef, nodeInfo, false);
    }

    private QName getRequiredType(NodeRef nodeRef, QName originalType, QName requiredType) {
        if (requiredType == null) return originalType;
        if (requiredType.equals(originalType)) return originalType;
        if (dictionaryService.isSubClass(originalType, requiredType)) return originalType;
        if (dictionaryService.isSubClass(requiredType, originalType)) return requiredType;
        throw new RuntimeException("Can not change type of " + nodeRef + " from " + originalType + " to " + requiredType + ", as it is not child type");
    }

    /* (non-Javadoc)
     * @see ru.citeck.ecos.node.NodeInfoService#persist(org.alfresco.service.cmr.repository.NodeRef, ru.citeck.ecos.node.NodeInfo, boolean)
     */
    @Override
    public void persist(NodeRef nodeRef, NodeInfo nodeInfo, boolean full) {

        // firstly get classes requirements:
        QName originalType = nodeService.getType(nodeRef);
        QName requiredType = getRequiredType(nodeRef, originalType, nodeInfo.getType());
        Set<QName> originalAspects = nodeService.getAspects(nodeRef);
        Set<QName> requiredAspects = nodeInfo.getAspects() != null ? new HashSet<>(nodeInfo.getAspects()) : new HashSet<>();

        Map<QName, Serializable> properties = nodeInfo.getProperties();
        Map<QName, List<NodeRef>> targetAssocs = nodeInfo.getTargetAssocs();
        Map<QName, List<NodeRef>> sourceAssocs = nodeInfo.getSourceAssocs();
        Map<QName, List<NodeRef>> childAssocs = nodeInfo.getChildAssocs();

        Set<ClassDefinition> classes = new HashSet<>();

        if (properties != null) {
            for (QName propertyName : properties.keySet()) {
                PropertyDefinition property = dictionaryService.getProperty(propertyName);
                if (property != null) classes.add(property.getContainerClass());
            }
        }

        if (targetAssocs != null) {
            for (QName assocName : targetAssocs.keySet()) {
                AssociationDefinition association = dictionaryService.getAssociation(assocName);
                if (association != null) classes.add(association.getSourceClass());
            }
        }

        if (sourceAssocs != null) {
            for (QName assocName : sourceAssocs.keySet()) {
                AssociationDefinition association = dictionaryService.getAssociation(assocName);
                if (association != null) classes.add(association.getTargetClass());
            }
        }

        if (childAssocs != null) {
            for (QName assocName : childAssocs.keySet()) {
                AssociationDefinition association = dictionaryService.getAssociation(assocName);
                if (association != null) classes.add(association.getSourceClass());
            }
        }

        for (ClassDefinition classDef : classes) {
            if (classDef.isAspect()) {
                requiredAspects.add(classDef.getName());
            } else {
                requiredType = getRequiredType(nodeRef, requiredType, classDef.getName());
            }
        }

        if (!originalType.equals(requiredType)) {
            nodeService.setType(nodeRef, requiredType);
        }

        requiredAspects.removeAll(originalAspects);
        for (QName aspect : requiredAspects) {
            nodeService.addAspect(nodeRef, aspect, null);
        }

        if (properties != null) {
            persistProperties(nodeRef, properties, full);
        }

        if (targetAssocs != null) {
            RepoUtils.setAssocs(nodeRef, targetAssocs, true, full, nodeService, associationIndexing);
        }

        if (sourceAssocs != null) {
            RepoUtils.setAssocs(nodeRef, sourceAssocs, false, full, nodeService, associationIndexing);
        }

        if (childAssocs != null) {
            boolean primary = true;
            RepoUtils.setChildAssocs(nodeRef, childAssocs, primary, full, nodeService);
        }

        boolean isAuthorityContainer =
                requiredType != null && requiredType.equals(ContentModel.TYPE_AUTHORITY_CONTAINER);

        if (!isAuthorityContainer) {
            NodeRef parent = nodeInfo.getParent();
            QName parentAssoc = nodeInfo.getParentAssoc();
            if (parent != null && parentAssoc != null) {
                ChildAssociationRef primaryParent = nodeService.getPrimaryParent(nodeRef);
                if (!primaryParent.getParentRef().equals(parent)
                        || !primaryParent.getTypeQName().equals(parentAssoc)) {
                    nodeService.moveNode(nodeRef, parent, parentAssoc, primaryParent.getQName());
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see ru.citeck.ecos.node.NodeInfoService#persist(ru.citeck.ecos.node.NodeInfo, boolean)
     */
    @Override
    public NodeRef persist(NodeInfo nodeInfo, boolean full) {
        NodeRef nodeRef = nodeInfo.getNodeRef();

        QName nodeType = nodeInfo.getType();

        if (nodeRef != null) {
            if (ContentModel.TYPE_PERSON.equals(nodeService.getType(nodeRef))) {
                Map<QName, Serializable> properties = nodeInfo.getProperties();
                String userName = (String) nodeService.getProperty(nodeRef, ContentModel.PROP_USERNAME);
                String oldPass = (String) properties.get(EcosModel.PROP_OLD_PASS);
                String newPass = (String) properties.get(EcosModel.PROP_PASS);
                String newPassVerify = (String) properties.get(EcosModel.PROP_PASS_VERIFY);
                if (StringUtils.isNotEmpty(newPass)
                        && StringUtils.isNotEmpty(newPassVerify)) {
                    if (StringUtils.isNotEmpty(oldPass)) {
                        try {
                            authenticationService.updateAuthentication(userName, oldPass.toCharArray(), newPass.toCharArray());
                        } catch (AuthenticationException e) {
                            throw new AlfrescoRuntimeException(I18NUtil.getMessage(ERROR_BAD_CREDENTIALS));
                        }
                    } else {
                        String currentAuthUser = AuthenticationUtil.getFullyAuthenticatedUser();
                        if (authorityService.isAdminAuthority(currentAuthUser)) {
                            authenticationService.setAuthentication(userName, newPass.toCharArray());
                        } else {
                            throw new AlfrescoRuntimeException(I18NUtil.getMessage(ERROR_NO_PERMISSIONS));
                        }
                    }
                }
                properties.remove(EcosModel.PROP_OLD_PASS);
                properties.remove(EcosModel.PROP_PASS);
                properties.remove(EcosModel.PROP_PASS_VERIFY);
            }
            persist(nodeRef, nodeInfo, full);
            return nodeInfo.getNodeRef();
        }

        // otherwise parent / parentAssoc should be specified
        NodeRef parent = null;
        QName parentAssoc = null;
        QName parentAssocName = null;

        if (!nodeType.equals(ContentModel.TYPE_PERSON) && !nodeType.equals(ContentModel.TYPE_AUTHORITY_CONTAINER)) {
            parent = nodeInfo.getParent();
            parentAssoc = nodeInfo.getParentAssoc();

            if (parent == null || parentAssoc == null) {
                throw new IllegalArgumentException("Either nodeRef, or parent/parentAssoc/type should be specified");
            }

            parentAssocName = nodeInfo.getParentAssocName();
            if (parentAssocName == null) {
                parentAssocName = QName.createQName(parentAssoc.getNamespaceURI(), GUID.generate());
            }
        }

        // create person node
        if (nodeType.equals(ContentModel.TYPE_PERSON)) {
            parent = nodeInfo.getParent();
            String userName = (String) nodeInfo.getProperty(ContentModel.PROP_USERNAME);
            Map<QName, Serializable> properties = nodeInfo.getProperties();

            nodeInfo.setParent(null);
            nodeInfo.setParentAssoc(null);

            Map<QName, Serializable> personProperties = new HashMap<>();
            personProperties.put(ContentModel.PROP_FIRSTNAME, properties.get(ContentModel.PROP_FIRSTNAME));
            personProperties.put(ContentModel.PROP_LASTNAME, properties.get(ContentModel.PROP_LASTNAME));
            personProperties.put(ContentModel.PROP_USERNAME, properties.get(ContentModel.PROP_USERNAME));
            personProperties.put(ContentModel.PROP_EMAIL, properties.get(ContentModel.PROP_EMAIL));
            nodeRef = personService.createPerson(personProperties);

            authenticationService.createAuthentication((String) properties.get(ContentModel.PROP_USERNAME),
                    ((String) properties.get(EcosModel.PROP_PASS)).toCharArray());
            properties.remove(EcosModel.PROP_PASS);
            properties.remove(EcosModel.PROP_PASS_VERIFY);

            if (authenticationService.isAuthenticationMutable(userName)) {
                authenticationService.setAuthenticationEnabled(userName,
                        Boolean.FALSE.equals(properties.get(EcosModel.PROP_IS_PERSON_DISABLED)));
            }

            if (parent != null) {
                String authName = (String) nodeService.getProperty(parent, ContentModel.PROP_AUTHORITY_NAME);
                authorityService.addAuthority(authName, userName);
            }
        } else if (nodeType.equals(ContentModel.TYPE_AUTHORITY_CONTAINER)) {
            Map<QName, Serializable> authProperties = nodeInfo.getProperties();
            NodeRef authorityContainerParent = nodeInfo.getParent();

            String authorityContainerParentName = RepoUtils.getProperty(authorityContainerParent,
                    ContentModel.PROP_AUTHORITY_NAME, nodeService);
            String authorityContainerName = String.valueOf(authProperties.get(ContentModel.PROP_AUTHORITY_NAME));

            String fullName = authorityService.createAuthority(AuthorityType.GROUP, authorityContainerName);
            authorityService.addAuthority(authorityContainerParentName, fullName);

            nodeRef = authorityService.getAuthorityNodeRef(fullName);
        } else {
            ChildAssociationRef childAssocRef = nodeService.createNode(parent, parentAssoc, parentAssocName, nodeType);
            nodeRef = childAssocRef.getChildRef();
        }

        persist(nodeRef, nodeInfo, full);

        return nodeRef;
    }

    /////////////////////////////////////////////////////////////////
    //                       PRIVATE STUFF                         //
    /////////////////////////////////////////////////////////////////

    private Map<QName, List<NodeRef>> getAssocMap(List<AssociationRef> assocs, boolean sourceAssocs) {
        Map<QName, List<NodeRef>> assocMap = new HashMap<>();
        for (AssociationRef assoc : assocs) {
            QName qname = assoc.getTypeQName();
            List<NodeRef> nodes = assocMap.get(qname);
            if (nodes == null) {
                nodes = new ArrayList<>();
                assocMap.put(qname, nodes);
            }
            nodes.add(sourceAssocs ? assoc.getSourceRef() : assoc.getTargetRef());
        }
        return assocMap;
    }

    private Map<QName, List<NodeRef>> getChildMap(List<ChildAssociationRef> assocs, boolean parentAssocs) {
        Map<QName, List<NodeRef>> assocMap = new HashMap<>();
        for (ChildAssociationRef assoc : assocs) {
            QName qname = assoc.getTypeQName();
            List<NodeRef> nodes = assocMap.get(qname);
            if (nodes == null) {
                nodes = new ArrayList<>();
                assocMap.put(qname, nodes);
            }
            nodes.add(parentAssocs ? assoc.getParentRef() : assoc.getChildRef());
        }
        return assocMap;
    }

    private void persistProperties(NodeRef nodeRef, Map<QName, Serializable> properties, boolean full) {

        // split content properties from other properties
        Map<QName, Serializable> notContentProperties = new HashMap<>(properties.size());
        Map<QName, Serializable> contentProperties = new HashMap<>();
        for (Map.Entry<QName, Serializable> entry : properties.entrySet()) {
            Serializable value = entry.getValue();
            QName propertyName = entry.getKey();
            PropertyDefinition propDef = dictionaryService.getProperty(propertyName);
            QName propType = propDef != null ? propDef.getDataType().getName() : null;

            if (DataTypeDefinition.CONTENT.equals(propType)) {
                contentProperties.put(propertyName, value);
            } else {
                if (DataTypeDefinition.DATE.equals(propType)) {
                    if (value instanceof String) {
                        value = ISO8601DateFormat.parseDayOnly((String) value, TimeZone.getTimeZone("GMT"));
                    } else if (value instanceof Date) {
                        long millisInDay = TimeUnit.DAYS.toMillis(1);
                        long daysCount = TimeUnit.MILLISECONDS.toDays(((Date) value).getTime());
                        value = new Date(millisInDay * daysCount);
                    }
                }

                notContentProperties.put(propertyName, value);
            }
        }

        if (full) {
            nodeService.setProperties(nodeRef, notContentProperties);
        } else {
            nodeService.addProperties(nodeRef, notContentProperties);
        }

        for (Map.Entry<QName, Serializable> entry : contentProperties.entrySet()) {
            setContentProperty(nodeRef, entry.getKey(), entry.getValue());
        }
    }

    private void setContentProperty(NodeRef nodeRef, QName propertyName, Serializable value) {
        if (value == null) {
            nodeService.removeProperty(nodeRef, propertyName);
            return;
        }

        ContentWriter writer = contentService.getWriter(nodeRef, propertyName, true);
        if (value instanceof File) {
            File file = (File) value;
            writer.setMimetype(mimetypeService.guessMimetype(file.getName()));
            writer.putContent(file);
        } else if (value instanceof Map) {
            Map<?, ?> contentModel = (Map<?, ?>) value;
            String contentUrl = (String) contentModel.get(CONTENT_URL);
            String content = (String) contentModel.get(CONTENT_CONTENT);
            String mimetype = (String) contentModel.get(CONTENT_MIMETYPE);
            String encoding = (String) contentModel.get(CONTENT_ENCODING);

            if (mimetype != null) writer.setMimetype(mimetype);
            if (encoding != null) writer.setEncoding(encoding);

            if (contentUrl != null) {
                if (NodeRef.isNodeRef(contentUrl)) {
                    NodeRef contentNodeRef = new NodeRef(contentUrl);
                    if (!nodeRef.equals(contentNodeRef)) {
                        ContentReader reader = contentService.getReader(contentNodeRef, ContentModel.PROP_CONTENT);
                        if (mimetype == null) writer.setMimetype(reader.getMimetype());
                        if (encoding == null) writer.setEncoding(reader.getEncoding());
                        writer.putContent(reader);
                    }
                } else {
                    throw new IllegalArgumentException("Currently only nodeRef is supported as url");
                }
            } else if (content != null) {
                writer.putContent(content);
            }

        } else if (value instanceof ContentReader) {
            ContentReader reader = (ContentReader) value;
            writer.setMimetype(reader.getMimetype());
            writer.setEncoding(reader.getEncoding());
            writer.putContent(reader);
        } else if (value instanceof InputStream) {
            writer.putContent((InputStream) value);
        } else if (value instanceof String) {
            writer.setMimetype(MimetypeMap.MIMETYPE_TEXT_PLAIN);
            writer.putContent((String) value);
        } else if (value instanceof ContentData) {
            nodeService.setProperty(nodeRef, propertyName, value);
        } else {
            throw new IllegalArgumentException("Value class is not supported for setting content: " + value.getClass());
        }
    }

    private void splitPropsAndAssocs(Map<QName, Serializable> all, Map<QName, Serializable> props, Map<QName, List<NodeRef>> assocs) {
        for (Map.Entry<QName, Serializable> entry : all.entrySet()) {
            QName name = entry.getKey();
            Serializable value = entry.getValue();

            PropertyDefinition propDef = dictionaryService.getProperty(name);
            if (propDef != null) {
                props.put(name, value);
                continue;
            }

            AssociationDefinition assocDef = dictionaryService.getAssociation(name);
            if (assocDef != null) {
                if (!(value instanceof Collection)) {
                    value = (Serializable) Collections.singletonList(value);
                }

                if (value instanceof Collection) {
                    @SuppressWarnings("rawtypes")
                    Collection<?> objects = (Collection) value;
                    List<NodeRef> targets = new ArrayList<>(objects.size());
                    for (Object object : objects) {
                        if (object == null) {
                            // empty
                        } else if (object instanceof NodeRef) {
                            targets.add((NodeRef) object);
                        } else if (object instanceof String) {
                            targets.add(new NodeRef((String) object));
                        } else {
                            logger.warn("Unsupported class for converting to nodeRef: " + object.getClass());
                        }
                    }
                    assocs.put(name, targets);
                }

                continue;
            }

            logger.debug("Found non-registered property (association), ignoring it: " + name);
        }
    }

    private void removeNotExistingNodeRefs(Map<QName, Serializable> taskProps) {
        Predicate<Serializable> predicate = prop -> prop instanceof NodeRef && !nodeService.exists((NodeRef) prop);

        taskProps.forEach((name, value) -> {
            if (value instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<Serializable> collection = (Collection<Serializable>) value;

                if (collection.stream().anyMatch(predicate)) {
                    Collection<Serializable> newCollection = collection.stream()
                            .map(v -> predicate.test(v) ? null : v)
                            .collect(Collectors.toList());

                    taskProps.replace(name, (Serializable) newCollection);
                }
            } else {
                if (predicate.test(value)) {
                    taskProps.replace(name, null);
                }
            }
        });
    }

    /////////////////////////////////////////////////////////////////
    //                      SPRING INTERFACE                       //
    /////////////////////////////////////////////////////////////////

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setDictionaryService(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    public void setNamespaceService(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }

    public void setContentService(ContentService contentService) {
        this.contentService = contentService;
    }

    public void setMimetypeService(MimetypeService mimetypeService) {
        this.mimetypeService = mimetypeService;
    }

    public void setNodeAttributeService(NodeAttributeService nodeAttributeService) {
        this.nodeAttributeService = nodeAttributeService;
    }

    public void setPersonService(PersonService personService) {
        this.personService = personService;
    }

    public void setAuthorityService(AuthorityService authorityService) {
        this.authorityService = authorityService;
    }

    public void init() {
        if (this.nodeService == null)
            this.nodeService = serviceRegistry.getNodeService();
        if (this.dictionaryService == null)
            this.dictionaryService = serviceRegistry.getDictionaryService();
        if (this.namespaceService == null)
            this.namespaceService = serviceRegistry.getNamespaceService();
        if (this.contentService == null)
            this.contentService = serviceRegistry.getContentService();
        if (this.mimetypeService == null)
            this.mimetypeService = serviceRegistry.getMimetypeService();
        if (this.nodeAttributeService == null)
            this.nodeAttributeService = (NodeAttributeService) serviceRegistry.getService(CiteckServices.NODE_ATTRIBUTE_SERVICE);
    }

    public void setAssociationIndexing(AssociationIndexing associationIndexing) {
        this.associationIndexing = associationIndexing;
    }

    public void setAuthenticationService(MutableAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }
}
