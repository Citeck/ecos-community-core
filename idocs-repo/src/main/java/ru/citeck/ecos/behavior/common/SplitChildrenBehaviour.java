package ru.citeck.ecos.behavior.common;

import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.LoadingCache;
import org.alfresco.model.ContentModel;
import ru.citeck.ecos.domain.node.ChildAssocEntityLimit;
import org.alfresco.repo.domain.node.NodeDAO;
import org.alfresco.repo.domain.node.NodeEntity;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.repo.node.NodeServicePolicies.OnCreateChildAssociationPolicy;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
import org.alfresco.service.namespace.InvalidQNameException;
import org.apache.commons.lang.StringUtils;
import org.apache.ibatis.session.RowBounds;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import ru.citeck.ecos.behavior.OrderedBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.alfresco.util.ParameterCheck;
import org.apache.log4j.Logger;
import ru.citeck.ecos.service.AlfrescoServices;
import ru.citeck.ecos.utils.RepoUtils;

import java.io.Serializable;
import java.util.*;

/**
 * @author Pavel Simonov
 */
public class SplitChildrenBehaviour implements OnCreateChildAssociationPolicy {

    private static final Logger logger = Logger.getLogger(SplitChildrenBehaviour.class);

    private static final String SELECT_CHILD_ASSOCS_OF_PARENT_LIMITED = "custom.alfresco.node.select.children.select_ChildAssocsOfParent_Limited";

    private NamespaceService namespaceService;
    private ServiceRegistry serviceRegistry;
    private PolicyComponent policyComponent;
    private SearchService searchService;
    private NodeService nodeService;
    private MimetypeService mimetypeService;
    private SqlSessionTemplate template;
    private NodeDAO nodeDao;
    private QNameDAO qnameDAO;

    private int order = 250;

    private NodeRef nodeRef;
    private String node;

    private SplitBehaviour splitBehaviour;

    private boolean enabled = true;

    private QName containerType = ContentModel.TYPE_FOLDER;
    private QName childAssocType = ContentModel.ASSOC_CONTAINS;
    private QName rootType = null;

    private LoadingCache<Pair<NodeRef, String>, Optional<NodeRef>> containersCache;

    public void init() {

        if (rootType == null) {
            rootType = containerType;
        }

        containersCache = CacheBuilder.newBuilder()
                                      .maximumSize(400)
                                      .build(CacheLoader.from(this::queryContainerByName));

        if (StringUtils.isBlank(node) && rootType.getNamespaceURI().contains("www.alfresco.org")) {
            throw new IllegalArgumentException("You should specify node or use " +
                "custom root type. node: " + node + " Root type: " + rootType);
        }
        ParameterCheck.mandatory("splitBehaviour", splitBehaviour);

        splitBehaviour.init(serviceRegistry);

        this.policyComponent.bindAssociationBehaviour(
                OnCreateChildAssociationPolicy.QNAME, rootType, childAssocType,
                new OrderedBehaviour(this, "onCreateChildAssociation",
                                     NotificationFrequency.TRANSACTION_COMMIT, order)
        );
    }

    @Override
    public void onCreateChildAssociation(final ChildAssociationRef childAssociationRef, boolean b) {

        if (!enabled) {
            return;
        }

        AuthenticationUtil.runAsSystem(() -> {
            onNodeCreated(childAssociationRef);
            return null;
        });
    }

    private void onNodeCreated(final ChildAssociationRef childAssociationRef) {

        final NodeRef parent = childAssociationRef.getParentRef();
        final NodeRef child = childAssociationRef.getChildRef();

        if (!nodeService.exists(child) || !nodeService.exists(parent)
                || containerType.equals(nodeService.getType(child))) {
            return;
        }

        if ((node != null || nodeRef != null) && !parent.equals(getNodeRef())) {
            return;
        }

        NodeRef actualParent = nodeService.getPrimaryParent(child).getParentRef();

        if (!parent.equals(actualParent)) {
            return;
        }

        moveChild(childAssociationRef);
    }

    private void moveChild(ChildAssociationRef assocRef) {

        NodeRef parent = assocRef.getParentRef();
        NodeRef child = assocRef.getChildRef();

        List<String> path = splitBehaviour.getPath(parent, child);

        if (!path.isEmpty()) {

            NodeRef destination = getContainer(parent, path, true);

            String name;
            if (childAssocType.equals(ContentModel.ASSOC_CONTAINS)) {
                name = getUniqueName(destination, child);
                nodeService.setProperty(child, ContentModel.PROP_NAME, name);
            } else {
                name = (String) nodeService.getProperty(child, ContentModel.PROP_NAME);
            }

            QName assocQName = QName.createQName(assocRef.getQName().getNamespaceURI(), name);
            nodeService.moveNode(child, destination, childAssocType, assocQName);

            splitBehaviour.onSuccess(parent, child);
        }
    }

    private String getUniqueName(NodeRef destination, NodeRef child) {
        String originalName = RepoUtils.getOriginalName(child, nodeService, mimetypeService);
        String extension = RepoUtils.getExtension(child, "", nodeService, mimetypeService);
        return RepoUtils.getUniqueName(destination, childAssocType, child, originalName, extension, nodeService);
    }

    private NodeRef getContainer(NodeRef parent, List<String> path, boolean createIfNotExist) {
        NodeRef folderRef = parent;
        for (String name : path) {
            NodeRef child = getContainerByName(folderRef, name);

            logger.debug(String.format("Check path container {folderRef: '%s', child: '%s', name: '%s'}",
                folderRef, child, name));

            if (child == null) {
                if (createIfNotExist) {
                    Map<QName, Serializable> props = new HashMap<>();
                    props.put(ContentModel.PROP_NAME, name);
                    child = nodeService.createNode(folderRef, childAssocType,
                                                   QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, name),
                                                   containerType, props).getChildRef();
                } else {
                    return null;
                }
            }
            folderRef = child;
        }
        return folderRef;
    }

    private NodeRef getContainerByName(NodeRef parent, String name) {

        Pair<NodeRef, String> data = new Pair<>(parent, name);

        Optional<NodeRef> containerRef = containersCache.getUnchecked(data);
        if (containerRef.isPresent() && nodeService.exists(containerRef.get())) {
            ChildAssociationRef containerParent = nodeService.getPrimaryParent(containerRef.get());
            if (Objects.equals(parent, containerParent.getParentRef())) {
                return containerRef.get();
            }
        }
        containersCache.invalidate(data);
        return containersCache.getUnchecked(data).orElse(null);
    }

    private Optional<NodeRef> queryContainerByName(Pair<NodeRef, String> parentChild) {
        return queryContainerByName(parentChild.getFirst(), parentChild.getSecond());
    }

    public Optional<NodeRef> queryContainerByName(NodeRef parent, String childName) {

        Pair<Long, NodeRef> parentNodePair = getNodePairNotNull(parent);

        ChildAssocEntityLimit assocEntity = new ChildAssocEntityLimit();
        NodeEntity parentNode = new NodeEntity();
        parentNode.setId(parentNodePair.getFirst());
        assocEntity.setParentNode(parentNode);
        if (childAssocType != null) {
            if (!assocEntity.setTypeQNameAll(qnameDAO, childAssocType, false)) {
                throw new InvalidQNameException("Invalid QName child assoc type: " + childAssocType + ", nodeRef: " + nodeRef);
            }
        }
        assocEntity.setChildNodeNameAll(null, childAssocType, childName);
        assocEntity.setOrdered(true);
        assocEntity.setLimit(1);

        RowBounds rowBounds = new RowBounds(0, 1);
        List<ChildAssocEntityLimit> entities = template.selectList(SELECT_CHILD_ASSOCS_OF_PARENT_LIMITED, assocEntity, rowBounds);

        ChildAssocEntityLimit childAssocEntity = entities.size() > 0 ? entities.get(0) : null;
        if (childAssocEntity == null || childAssocEntity.getChildNode() == null) {
            return null;
        }

        return Optional.of(childAssocEntity.getChildNode().getNodeRef());
    }

    @NotNull
    private Pair<Long, NodeRef> getNodePairNotNull(@NotNull NodeRef nodeRef) throws InvalidNodeRefException {

        Pair<Long, NodeRef> unchecked = nodeDao.getNodePair(nodeRef);
        if (unchecked == null) {
            NodeRef.Status nodeStatus = nodeDao.getNodeRefStatus(nodeRef);
            throw new InvalidNodeRefException("Node does not exist: " + nodeRef + " (status:" + nodeStatus + ")", nodeRef);
        }
        return unchecked;
    }

    private NodeRef getNodeRef() {
        if (nodeRef == null && node != null) {
            if (NodeRef.isNodeRef(node)) {
                nodeRef = new NodeRef(node);
            } else {
                NodeRef root = nodeService.getRootNode(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
                List<NodeRef> results = searchService.selectNodes(root, node, null, namespaceService, false);
                nodeRef = !results.isEmpty() ? results.get(0) : null;
            }
        }
        return nodeRef;
    }

    public void setOrder(int order) {
        this.order = order;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setSplitBehaviour(SplitBehaviour splitBehaviour) {
        this.splitBehaviour = splitBehaviour;
    }

    public void setRootType(QName rootType) {
        this.rootType = rootType;
    }

    public void setContainerType(QName containerType) {
        this.containerType = containerType;
    }

    public void setChildAssocType(QName childAssocType) {
        this.childAssocType = childAssocType;
    }

    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
        nodeService = serviceRegistry.getNodeService();
        searchService = serviceRegistry.getSearchService();
        mimetypeService = serviceRegistry.getMimetypeService();
        namespaceService = serviceRegistry.getNamespaceService();
        policyComponent = (PolicyComponent) serviceRegistry.getService(AlfrescoServices.POLICY_COMPONENT);
    }

    public interface SplitBehaviour {
        void init(ServiceRegistry serviceRegistry);
        List<String> getPath(NodeRef parent, NodeRef node);
        void onSuccess(NodeRef parent, NodeRef node);
    }

    public static class DateSplit implements SplitBehaviour {

        public enum Depth {
            YEAR, YEAR_MONTH, YEAR_MONTH_DAY;
            final boolean hasYear = name().contains("YEAR");
            final boolean hasMonth = name().contains("MONTH");
            final boolean hasDay = name().contains("DAY");
        }

        private NodeService nodeService;

        private QName dateProperty = ContentModel.PROP_CREATED;
        private Boolean takeCurrentDateIfNull = null;

        private Depth depth = Depth.YEAR_MONTH_DAY;

        @Override
        public void init(ServiceRegistry serviceRegistry) {
            nodeService = serviceRegistry.getNodeService();
            if (takeCurrentDateIfNull == null) {
                takeCurrentDateIfNull = ContentModel.PROP_CREATED.equals(dateProperty);
            }
        }

        @Override
        public List<String> getPath(NodeRef parent, NodeRef node) {

            Date date = getDate(node);

            if (date != null) {

                Calendar cal = Calendar.getInstance();
                cal.setTime(date);

                List<String> path = new ArrayList<>();

                if (depth.hasYear) {
                    path.add(String.valueOf(cal.get(Calendar.YEAR)));
                }
                if (depth.hasMonth) {
                    path.add(String.valueOf(cal.get(Calendar.MONTH) + 1));
                }
                if (depth.hasDay) {
                    path.add(String.valueOf(cal.get(Calendar.DAY_OF_MONTH)));
                }

                return path;
            }

            return Collections.emptyList();
        }

        @Override
        public void onSuccess(NodeRef parent, NodeRef node) {

        }

        private Date getDate(NodeRef node) {
            Date result = (Date) nodeService.getProperty(node, dateProperty);
            return result == null && takeCurrentDateIfNull ? new Date() : result;
        }

        public void setDateProperty(QName dateProperty) {
            this.dateProperty = dateProperty;
        }

        public void setTakeCurrentDateIfNull(boolean takeCurrentDateIfNull) {
            this.takeCurrentDateIfNull = takeCurrentDateIfNull;
        }

        public void setDepth(Depth depth) {
            this.depth = depth;
        }
    }

    public static class UuidSplit implements SplitBehaviour {

        private int depth = 4;

        @Override
        public void init(ServiceRegistry serviceRegistry) {
        }

        @Override
        public List<String> getPath(NodeRef parent, NodeRef node) {

            String id = node.getId();
            List<String> result = new ArrayList<>();

            int idx = 0;
            int depthCounter = depth;
            while (depthCounter > 0 && idx < id.length()) {
                char ch = id.charAt(idx++);
                if (ch != '-') {
                    result.add(String.valueOf(ch));
                    depthCounter--;
                }
            }

            return result;
        }

        @Override
        public void onSuccess(NodeRef parent, NodeRef node) {
        }

        public void setDepth(int depth) {
            this.depth = depth;
        }
    }

    @Autowired
    @Qualifier("customSqlSessionTemplate")
    public void setTemplate(SqlSessionTemplate template) {
        this.template = template;
    }

    @Autowired
    @Qualifier("qnameDAO")
    public void setQnameDAO(QNameDAO qnameDAO) {
        this.qnameDAO = qnameDAO;
    }

    @Autowired
    @Qualifier("nodeDAO")
    public void setNodeDao(NodeDAO nodeDao) {
        this.nodeDao = nodeDao;
    }
}
