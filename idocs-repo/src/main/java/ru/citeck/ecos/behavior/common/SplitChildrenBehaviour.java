package ru.citeck.ecos.behavior.common;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.NodeServicePolicies.OnCreateChildAssociationPolicy;
import org.alfresco.repo.policy.Behaviour.NotificationFrequency;
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
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.service.AlfrescoServices;
import ru.citeck.ecos.utils.RepoUtils;

import java.io.Serializable;
import java.util.*;

/**
 * @author Pavel Simonov
 */
public class SplitChildrenBehaviour implements OnCreateChildAssociationPolicy {

    private static final Logger logger = Logger.getLogger(SplitChildrenBehaviour.class);

    private NamespaceService namespaceService;
    private ServiceRegistry serviceRegistry;
    private PolicyComponent policyComponent;
    private SearchService searchService;
    private NodeService nodeService;
    private MimetypeService mimetypeService;

    private int order = 250;

    private NodeRef nodeRef;
    private String node;

    private SplitBehaviour splitBehaviour;

    private boolean enabled = true;

    private QName containerType = ContentModel.TYPE_FOLDER;
    private QName childAssocType = ContentModel.ASSOC_CONTAINS;

    private LoadingCache<Pair<NodeRef, String>, Optional<NodeRef>> containersCache;

    public void init() {

        containersCache = CacheBuilder.newBuilder()
                                      .maximumSize(400)
                                      .build(CacheLoader.from(this::queryContainerByName));

        ParameterCheck.mandatoryString("node", node);
        ParameterCheck.mandatory("splitBehaviour", splitBehaviour);

        splitBehaviour.init(serviceRegistry);

        this.policyComponent.bindAssociationBehaviour(
                OnCreateChildAssociationPolicy.QNAME, containerType, childAssocType,
                new OrderedBehaviour(this, "onCreateChildAssociation",
                                     NotificationFrequency.TRANSACTION_COMMIT, order)
        );
    }

    @Override
    public void onCreateChildAssociation(final ChildAssociationRef childAssociationRef, boolean b) {

        if (!enabled) {
            return;
        }

        final NodeRef parent = childAssociationRef.getParentRef();
        final NodeRef child = childAssociationRef.getChildRef();

        AuthenticationUtil.runAsSystem(() -> {

            if (parent.equals(getNodeRef()) && nodeService.exists(child)
                    && !containerType.equals(nodeService.getType(child))) {

                NodeRef actualParent = nodeService.getPrimaryParent(child).getParentRef();

                if (parent.equals(actualParent)) {
                    moveChild(childAssociationRef);
                }
            }
            return null;
        });
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

    private Optional<NodeRef> queryContainerByName(NodeRef parent, String name) {
        return FTSQuery.create()
                       .type(containerType).and()
                       .parent(parent).and()
                       .exact(ContentModel.PROP_NAME, name)
                       .transactional()
                       .queryOne(searchService);
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
}
