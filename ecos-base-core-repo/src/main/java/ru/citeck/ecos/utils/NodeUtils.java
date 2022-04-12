package ru.citeck.ecos.utils;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport.TxnReadState;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNode;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.dictionary.ChildAssociationDefinition;
import org.alfresco.service.cmr.dictionary.ClassDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.alfresco.util.GUID;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.node.DisplayNameService;
import ru.citeck.ecos.records2.RecordRef;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class NodeUtils {

    public static final QName QNAME = QName.createQName("", "nodeUtils");

    public static final String WORKSPACE_PREFIX = StoreRef.PROTOCOL_WORKSPACE + StoreRef.URI_FILLER;
    public static final String ARCHIVE_PREFIX = StoreRef.PROTOCOL_ARCHIVE + StoreRef.URI_FILLER;
    public static final String DELETED_PREFIX = StoreRef.PROTOCOL_DELETED + StoreRef.URI_FILLER;

    public static final String WORKSPACE_SPACES_STORE_PREFIX = WORKSPACE_PREFIX + "SpacesStore/";

    private static final String KEY_PENDING_DELETE_NODES = "DbNodeServiceImpl.pendingDeleteNodes";

    private static final List<String> NODE_REF_PREFIXES = Arrays.asList(
        WORKSPACE_PREFIX,
        ARCHIVE_PREFIX,
        DELETED_PREFIX
    );

    private NodeService nodeService;
    private SearchService searchService;
    private NamespaceService namespaceService;
    private DictionaryService dictionaryService;
    private DisplayNameService displayNameService;

    public String getDisplayName(NodeRef nodeRef) {
        return displayNameService.getDisplayName(nodeRef);
    }

    /**
     * Check is node pending for delete or not exist
     *
     * @param nodeRef Node reference
     * @return Check result
     */
    public boolean isValidNode(NodeRef nodeRef) {
        if (nodeRef == null || !nodeService.exists(nodeRef)) {
            return false;
        }
        if (AlfrescoTransactionSupport.getTransactionReadState() != TxnReadState.TXN_READ_WRITE) {
            return true;
        }
        return !TransactionalResourceHelper.getSet(KEY_PENDING_DELETE_NODES).contains(nodeRef);
    }

    public boolean isNodeRef(@Nullable Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof NodeRef) {
            return true;
        }
        if (value instanceof String) {
            String valueStr = (String) value;
            for (String prefix : NODE_REF_PREFIXES) {
                if (valueStr.startsWith(prefix) && NodeRef.isNodeRef(valueStr)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    /**
     * Get node by nodeRef or path
     */
    @NotNull
    public NodeRef getNodeRef(Object node) {
        NodeRef nodeRef = getNodeRefOrNull(node);
        if (nodeRef == null) {
            throw new IllegalArgumentException("NodeRef can't be evaluated from: '" + node + "'");
        }
        return nodeRef;
    }

    /**
     * Get node by nodeRef or path
     */
    @Nullable
    public NodeRef getNodeRefOrNull(Object node) {

        if (node == null) {
            return null;
        }
        if (node instanceof NodeRef) {
            return (NodeRef) node;
        }
        if (node instanceof RecordRef) {
            return getNodeRefOrNull(((RecordRef) node).getId());
        }

        if (!(node instanceof String)) {
            return null;
        }

        String nodeStr = (String) node;

        if (StringUtils.isBlank(nodeStr)) {
            return null;
        }

        if (isNodeRef(nodeStr)) {
            try {
                return new NodeRef(nodeStr);
            } catch (MalformedNodeRefException e) {
                return null;
            }
        }

        int workspaceIdx = nodeStr.indexOf("workspace://SpacesStore/");
        if (workspaceIdx >= 0) {
            return new NodeRef(nodeStr.substring(workspaceIdx));
        }

        if (nodeStr.startsWith("/") || nodeStr.contains("app:company_home")) {

            NodeRef root = nodeService.getRootNode(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
            List<NodeRef> results = searchService.selectNodes(root, nodeStr, null,
                namespaceService, false);
            return results.isEmpty() ? null : results.get(0);
        }

        return null;
    }

    /**
     * Get node property
     */
    public <T> T getProperty(NodeRef nodeRef, QName propName) {
        @SuppressWarnings("unchecked")
        T result = (T) nodeService.getProperty(nodeRef, propName);
        return result;
    }

    public void moveNode(NodeRef nodeRef, NodeRef destinationRef, QName assocType) {

        ChildAssociationRef parentAssoc = nodeService.getPrimaryParent(nodeRef);
        if (parentAssoc.getParentRef().equals(destinationRef)) {
            return;
        }

        if (assocType == null) {
            assocType = ContentModel.ASSOC_CONTAINS;
        }

        String name = (String) nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);
        String validName = getValidChildName(destinationRef, assocType, name);

        if (!name.equals(validName)) {
            nodeService.setProperty(nodeRef, ContentModel.PROP_NAME, name);
        }
        nodeService.moveNode(nodeRef, destinationRef, assocType, parentAssoc.getQName());
    }

    public String getValidChildName(NodeRef parentRef, String name) {
        return getValidChildName(parentRef, ContentModel.ASSOC_CONTAINS, name);
    }

    public static String getValidName(String name) {
        return name.replaceAll("[\"*\\\\><?/:|]", "_").replaceAll("[.\\s]+$", "");
    }

    public String getValidChildName(NodeRef parentRef, QName childAssoc, String name) {
        return AuthenticationUtil.runAsSystem(() -> getValidChildNameImpl(parentRef, childAssoc, name));
    }

    public String getValidChildNameImpl(NodeRef parentRef, QName childAssoc, String name) {
        AssociationDefinition assoc = dictionaryService.getAssociation(childAssoc);

        name = getValidName(name);

        if (!(assoc instanceof ChildAssociationDefinition) ||
            ((ChildAssociationDefinition) assoc).getDuplicateChildNamesAllowed()) {
            return name;
        }

        NodeRef child = nodeService.getChildByName(parentRef, childAssoc, name);
        if (child == null) {
            return name;
        }

        String extension = FilenameUtils.getExtension(name);

        if (StringUtils.isNotBlank(extension)) {
            extension = "." + extension;
        }
        String nameWithoutExt = FilenameUtils.removeExtension(name);

        int index = 0;
        String newNameWithIndex;

        do {
            newNameWithIndex = nameWithoutExt + " (" + (++index) + ")" + extension;
            child = nodeService.getChildByName(parentRef, childAssoc, newNameWithIndex);
        } while (child != null);

        return newNameWithIndex;
    }

    public NodeRef createNode(NodeRef parentRef, QName type, QName childAssoc, Map<QName, Serializable> props) {

        String name = (String) props.get(ContentModel.PROP_NAME);
        if (name == null) {
            name = GUID.generate();
        }

        String finalName = name;
        name = AuthenticationUtil.runAsSystem(() -> getValidChildName(parentRef, childAssoc, finalName));
        props.put(ContentModel.PROP_NAME, name);

        QName assocName = QName.createQNameWithValidLocalName(childAssoc.getNamespaceURI(), name);

        return nodeService.createNode(parentRef, childAssoc, assocName, type, props).getChildRef();
    }

    public boolean setAssocs(NodeRef nodeRef, Collection<NodeRef> targets, QName assocName) {
        return setAssocs(nodeRef, targets, assocName, false);
    }

    public boolean setAssocs(NodeRef nodeRef, Collection<NodeRef> targets, QName assocName, boolean primaryChildren) {

        if (!isValidNode(nodeRef) || assocName == null) {
            return false;
        }

        if (targets == null) {
            targets = Collections.emptySet();
        }

        Set<NodeRef> targetsSet = targets.stream().map(ref -> {
            String protocol = ref.getStoreRef().getProtocol();
            if (protocol.startsWith("alfresco/@") && protocol.contains("workspace")) {
                return new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, ref.getId());
            }
            return ref;
        }).collect(Collectors.toSet());

        AssociationDefinition assocDef = dictionaryService.getAssociation(assocName);

        if (assocDef != null) {

            List<NodeRef> storedRefs = getAssocsImpl(nodeRef, assocDef, true);

            Set<NodeRef> toAdd = targetsSet.stream()
                                           .filter(r -> !storedRefs.contains(r))
                                           .collect(Collectors.toSet());
            Set<NodeRef> toRemove = storedRefs.stream()
                                              .filter(r -> !targetsSet.contains(r))
                                              .collect(Collectors.toSet());

            if (!toAdd.isEmpty() || !toRemove.isEmpty()) {

                if (assocDef instanceof ChildAssociationDefinition) {

                    List<ChildAssociationRef> currentAssocs = getChildAssocs(nodeRef, assocDef, true);
                    Map<NodeRef, ChildAssociationRef> currentAssocByChild = new HashMap<>();
                    currentAssocs.forEach(a -> currentAssocByChild.put(a.getChildRef(), a));

                    for (NodeRef removeRef : toRemove) {
                        if (primaryChildren) {
                            nodeService.removeChildAssociation(currentAssocByChild.get(removeRef));
                        } else {
                            nodeService.removeSecondaryChildAssociation(currentAssocByChild.get(removeRef));
                        }
                    }
                    for (NodeRef addRef : toAdd) {
                        ChildAssociationRef primaryParent = nodeService.getPrimaryParent(addRef);
                        if (primaryChildren) {
                            nodeService.moveNode(addRef, nodeRef, assocName, primaryParent.getQName());
                            ClassDefinition assocClassDef = assocDef.getSourceClass();
                            if (assocClassDef.isAspect()) {
                                QName assocAspectQName = assocClassDef.getName();
                                if (!nodeService.hasAspect(nodeRef, assocAspectQName)) {
                                    nodeService.addAspect(nodeRef, assocAspectQName, null);
                                }
                            }
                        } else {
                            nodeService.addChild(nodeRef, addRef, assocName, primaryParent.getQName());
                        }
                    }
                } else {
                    for (NodeRef removeRef : toRemove) {
                        nodeService.removeAssociation(nodeRef, removeRef, assocName);
                    }
                    for (NodeRef addRef : toAdd) {
                        nodeService.createAssociation(nodeRef, addRef, assocName);
                    }
                }

                return true;
            }
        }
        return false;
    }

    /**
     * Create association with specified node
     *
     * @return true if association was created or false if association already exists or arguments are invalid
     */
    public boolean createAssoc(NodeRef sourceRef, NodeRef targetRef, QName assocName) {

        if (!isValidNode(sourceRef) || !isValidNode(targetRef) || assocName == null) {
            return false;
        }
        AssociationDefinition assocDef = dictionaryService.getAssociation(assocName);
        if (assocDef != null) {
            List<NodeRef> storedRefs = getAssocsImpl(sourceRef, assocDef, true);
            if (!storedRefs.contains(targetRef)) {
                nodeService.createAssociation(sourceRef, targetRef, assocName);
                return true;
            }
        }
        return false;
    }

    /**
     * Remove association with specified target node
     *
     * @return true if association was removed or false if association not exists or arguments are invalid
     */
    public boolean removeAssoc(NodeRef sourceRef, NodeRef targetRef, QName assocName) {

        if (!isValidNode(sourceRef) || !isValidNode(targetRef) || assocName == null) {
            return false;
        }
        AssociationDefinition assocDef = dictionaryService.getAssociation(assocName);
        if (assocDef != null) {
            List<NodeRef> storedRefs = getAssocsImpl(sourceRef, assocDef, true);
            if (storedRefs.contains(targetRef)) {
                nodeService.removeAssociation(sourceRef, targetRef, assocName);
                return true;
            }
        }
        return false;
    }

    /**
     * Get first node associated as target or child of specified sourceRef
     *
     * @return associated node
     */
    public Optional<NodeRef> getAssocTarget(NodeRef sourceRef, QName assocName) {
        return getAssocTargets(sourceRef, assocName).stream().findFirst();
    }

    /**
     * Get nodes associated as target or child of specified sourceRef
     *
     * @return list of associated nodes or empty list if arguments is not valid
     */
    public List<NodeRef> getAssocTargets(NodeRef sourceRef, QName assocName) {
        return getAssocs(sourceRef, assocName, true);
    }

    /**
     * Get nodes associated as source or parent of specified targetRef
     *
     * @return list of associated nodes or empty list if arguments is not valid
     */
    public List<NodeRef> getAssocSources(NodeRef targetRef, QName assocName) {
        return getAssocs(targetRef, assocName, false);
    }

    private List<NodeRef> getAssocs(NodeRef nodeRef, QName assocName, boolean nodeIsSource) {
        if (isValidNode(nodeRef) && assocName != null) {
            AssociationDefinition assocDef = dictionaryService.getAssociation(assocName);
            if (assocDef != null) {
                return getAssocsImpl(nodeRef, assocDef, nodeIsSource);
            }
        }
        return Collections.emptyList();
    }

    private List<ChildAssociationRef> getChildAssocs(NodeRef nodeRef,
                                                     AssociationDefinition assocDef,
                                                     boolean nodeIsSource) {

        List<ChildAssociationRef> assocsRefs;

        if (nodeIsSource) {
            assocsRefs = nodeService.getChildAssocs(nodeRef, assocDef.getName(), RegexQNamePattern.MATCH_ALL);
        } else {
            assocsRefs = nodeService.getParentAssocs(nodeRef, assocDef.getName(), RegexQNamePattern.MATCH_ALL);
        }

        return assocsRefs;
    }

    private List<NodeRef> getAssocsImpl(NodeRef nodeRef, AssociationDefinition assocDef, boolean nodeIsSource) {

        if (assocDef.isChild()) {

            return getChildAssocs(nodeRef, assocDef, nodeIsSource).stream()
                             .map(r -> nodeIsSource ? r.getChildRef() : r.getParentRef())
                             .collect(Collectors.toList());
        } else {

            List<AssociationRef> assocsRefs;

            if (nodeIsSource) {
                assocsRefs = nodeService.getTargetAssocs(nodeRef, assocDef.getName());
            } else {
                assocsRefs = nodeService.getSourceAssocs(nodeRef, assocDef.getName());
            }

            return assocsRefs.stream()
                             .map(r -> nodeIsSource ? r.getTargetRef() : r.getSourceRef())
                             .collect(Collectors.toList());
        }
    }

    public void fillNodeRefsList(Object value, List<NodeRef> resultList) {
        if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                fillNodeRefsList(item, resultList);
            }
        } else {
            NodeRef node = getNodeRefByObject(value);
            if (node != null) {
                resultList.add(node);
            }
        }
    }

    public NodeRef getNodeRefByObject(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof NodeRef) {
            return (NodeRef) value;
        } else if (value instanceof String) {
            String strValue = (String) value;
            if (StringUtils.isNotBlank(strValue)) {
                return new NodeRef(strValue);
            }
        } else if (value instanceof AssociationRef) {
            return ((AssociationRef) value).getTargetRef();
        } else if (value instanceof ChildAssociationRef) {
            return ((ChildAssociationRef) value).getChildRef();
        } else if (value instanceof ActivitiScriptNode) {
            return ((ActivitiScriptNode) value).getNodeRef();
        } else if (value instanceof ScriptNode) {
            return ((ScriptNode) value).getNodeRef();
        }

        return null;
    }

    @Autowired
    public void setDisplayNameService(DisplayNameService displayNameService) {
        this.displayNameService = displayNameService;
    }

    @Autowired
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        nodeService = (NodeService) serviceRegistry.getService(QName.createQName("","nodeService"));
        searchService = serviceRegistry.getSearchService();
        namespaceService = serviceRegistry.getNamespaceService();
        dictionaryService = serviceRegistry.getDictionaryService();
    }
}
