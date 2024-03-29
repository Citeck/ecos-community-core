package ru.citeck.ecos.utils;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.domain.node.NodeDAO;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.node.DisplayNameService;
import ru.citeck.ecos.records2.RecordRef;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class NodeUtils {

    public static final QName QNAME = QName.createQName("", "nodeUtils");

    public static final String WORKSPACE_PREFIX = StoreRef.PROTOCOL_WORKSPACE + StoreRef.URI_FILLER;
    public static final String ARCHIVE_PREFIX = StoreRef.PROTOCOL_ARCHIVE + StoreRef.URI_FILLER;
    public static final String DELETED_PREFIX = StoreRef.PROTOCOL_DELETED + StoreRef.URI_FILLER;

    public static final String WORKSPACE_SPACES_STORE_PREFIX = WORKSPACE_PREFIX + "SpacesStore/";
    public static final int UUID_SIZE = 36;
    public static final Pattern UUID_PATTERN = Pattern.compile("^[\\da-fA-F]{8}-([\\da-fA-F]{4}-){3}[\\da-fA-F]{12}$");

    private static final String KEY_PENDING_DELETE_NODES = "DbNodeServiceImpl.pendingDeleteNodes";

    private static final List<String> NODE_REF_PREFIXES = Arrays.asList(
        WORKSPACE_PREFIX,
        ARCHIVE_PREFIX,
        DELETED_PREFIX
    );

    private NodeDAO nodeDao;
    private NodeService nodeService;
    private SearchService searchService;
    private NamespaceService namespaceService;
    private DictionaryService dictionaryService;
    private DisplayNameService displayNameService;

    public static boolean isNodeRefWithUuid(@Nullable String nodeRef) {
        return nodeRef != null
            && nodeRef.startsWith(WORKSPACE_SPACES_STORE_PREFIX)
            && nodeRef.length() == WORKSPACE_SPACES_STORE_PREFIX.length() + UUID_SIZE
            && UUID_PATTERN.matcher(nodeRef.substring(WORKSPACE_SPACES_STORE_PREFIX.length())).matches();
    }

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
                if (valueStr.startsWith(prefix) && NodeRef.isNodeRef(valueStr)
                        && isNodeIdLengthValid(prefix, valueStr)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    private boolean isNodeIdLengthValid(String prefix, String val) {
        int idLength = val.length() - val.indexOf('/', prefix.length()) - 1;
        return idLength <= UUID_SIZE;
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

        AssociationDefinition assocDef = dictionaryService.getAssociation(assocName);
        if (assocDef == null) {
            return false;
        }
        boolean isChildAssoc = assocDef instanceof ChildAssociationDefinition;

        boolean isOrderedTargets = targets instanceof List<?>
            || targets instanceof LinkedHashSet<?>
            || targets instanceof TreeSet<?>;

        Set<NodeRef> targetsSet = targets.stream().map(ref -> {
            String protocol = ref.getStoreRef().getProtocol();
            if (protocol.startsWith("alfresco/@") && protocol.contains("workspace")) {
                return new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, ref.getId());
            }
            return ref;
        }).collect(Collectors.toCollection(LinkedHashSet::new));

        Map<NodeRef, ChildAssociationRef> storedChildAssocsByChild = Collections.emptyMap();
        Map<NodeRef, AssociationRef> storedAssocsByTarget = Collections.emptyMap();
        List<NodeRef> storedRefs = new ArrayList<>();
        if (isChildAssoc) {
            storedChildAssocsByChild = new LinkedHashMap<>();
            List<ChildAssociationRef> childAssocs = getChildAssocs(nodeRef, assocDef, true);
            for (ChildAssociationRef assoc : childAssocs) {
                NodeRef childRef = assoc.getChildRef();
                storedChildAssocsByChild.put(childRef, assoc);
                storedRefs.add(childRef);
            }
        } else {
            storedAssocsByTarget = new LinkedHashMap<>();
            List<AssociationRef> targetAssocs = nodeService.getTargetAssocs(nodeRef, assocDef.getName());
            for (AssociationRef assocRef : targetAssocs) {
                NodeRef targetRef = assocRef.getTargetRef();
                storedAssocsByTarget.put(targetRef, assocRef);
                storedRefs.add(targetRef);
            }
        }

        Set<NodeRef> toAdd = targetsSet.stream()
            .filter(r -> !storedRefs.contains(r))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<NodeRef> toRemove = storedRefs.stream()
            .filter(r -> !targetsSet.contains(r))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        if (toAdd.isEmpty() && toRemove.isEmpty()) {
            // nothing to add and remove, but maybe we should change associations order?
            if (!isOrderedTargets || (targetsSet.isEmpty() && storedRefs.isEmpty())) {
                return false;
            }
            if (!isOrderedCollectionsContentEquals(targetsSet, storedRefs)) {
                int idx = 1;
                if (isChildAssoc) {
                    for (NodeRef childRef : targetsSet) {
                        nodeService.setChildAssociationIndex(storedChildAssocsByChild.get(childRef), idx);
                        idx++;
                    }
                } else {
                    for (NodeRef targetRef : targetsSet) {
                        AssociationRef assocRef = storedAssocsByTarget.get(targetRef);
                        nodeDao.setNodeAssocIndex(assocRef.getId(), idx);
                        idx++;
                    }
                }
                return true;
            }
            return false;

        } else {

            if (!toAdd.isEmpty()) {
                ClassDefinition assocClassDef = assocDef.getSourceClass();
                if (assocClassDef.isAspect()) {
                    QName assocAspectQName = assocClassDef.getName();
                    if (!nodeService.hasAspect(nodeRef, assocAspectQName)) {
                        nodeService.addAspect(nodeRef, assocAspectQName, null);
                    }
                }
            }

            if (isChildAssoc) {

                for (NodeRef refToRemove : toRemove) {
                    if (primaryChildren) {
                        nodeService.removeChildAssociation(storedChildAssocsByChild.get(refToRemove));
                    } else {
                        nodeService.removeSecondaryChildAssociation(storedChildAssocsByChild.get(refToRemove));
                    }
                }
                int idx = 1;
                for (NodeRef targetRef : targetsSet) {
                    ChildAssociationRef childAssoc = storedChildAssocsByChild.get(targetRef);
                    if (childAssoc == null) {
                        ChildAssociationRef primaryParent = nodeService.getPrimaryParent(targetRef);
                        if (primaryChildren) {
                            childAssoc = nodeService.moveNode(targetRef, nodeRef, assocName, primaryParent.getQName());
                        } else {
                            childAssoc = nodeService.addChild(nodeRef, targetRef, assocName, primaryParent.getQName());
                        }
                    }
                    if (isOrderedTargets) {
                        nodeService.setChildAssociationIndex(childAssoc, idx);
                    }
                    idx++;
                }
            } else {

                for (NodeRef removeRef : toRemove) {
                    nodeService.removeAssociation(nodeRef, removeRef, assocName);
                }

                int idx = 1;
                for (NodeRef targetRef : targetsSet) {
                    AssociationRef assocRef = storedAssocsByTarget.get(targetRef);
                    if (assocRef == null) {
                        assocRef = nodeService.createAssociation(nodeRef, targetRef, assocName);
                    }
                    if (isOrderedTargets) {
                        nodeDao.setNodeAssocIndex(assocRef.getId(), idx);
                    }
                    idx++;
                }
            }
            return true;
        }
    }

    // todo: move logic to ecos-commons
    private <T> boolean isOrderedCollectionsContentEquals(Collection<T> first, Collection<T> second) {
        if (first.size() != second.size()) {
            return false;
        }
        Iterator<T> firstIt = first.iterator();
        Iterator<T> secondIt = second.iterator();
        while (firstIt.hasNext() && secondIt.hasNext()) {
            T firstElement = firstIt.next();
            T secondElement = secondIt.next();
            if (!Objects.equals(firstElement, secondElement)) {
                return false;
            }
        }
        return true;
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
        nodeService = (NodeService) serviceRegistry.getService(QName.createQName("", "nodeService"));
        searchService = serviceRegistry.getSearchService();
        namespaceService = serviceRegistry.getNamespaceService();
        dictionaryService = serviceRegistry.getDictionaryService();
    }

    @Autowired
    @Qualifier("nodeDAO")
    public void setNodeDao(NodeDAO nodeDao) {
        this.nodeDao = nodeDao;
    }
}
