package ru.citeck.ecos.graphql;

import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.LoadingCache;
import lombok.Getter;
import org.alfresco.repo.i18n.MessageService;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import ru.citeck.ecos.graphql.node.GqlAlfNode;
import ru.citeck.ecos.graphql.node.GqlQName;
import ru.citeck.ecos.model.lib.type.service.TypeDefService;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.graphql.GqlContext;
import ru.citeck.ecos.security.EcosPermissionService;
import ru.citeck.ecos.service.CiteckServices;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AlfGqlContext extends GqlContext {

    private LoadingCache<NodeRef, GqlAlfNode> nodes;
    private LoadingCache<Object, Optional<GqlQName>> qnames;

    private final ServiceRegistry serviceRegistry;

    @Getter
    private final DictionaryService dictionaryService;
    @Getter
    private final NamespaceService namespaceService;
    @Getter
    private final NodeService nodeService;
    @Getter
    private final MessageService messageService;
    @Getter
    private final EcosPermissionService ecosPermissionService;

    private final TypeDefService typeDefService;

    private final Map<String, Object> servicesCache = new ConcurrentHashMap<>();

    public AlfGqlContext(ServiceRegistry serviceRegistry) {
        this(serviceRegistry, null);
    }

    public AlfGqlContext(ServiceRegistry serviceRegistry, RecordsService recordsService) {

        this.serviceRegistry = serviceRegistry;
        this.dictionaryService = serviceRegistry.getDictionaryService();
        this.namespaceService = serviceRegistry.getNamespaceService();
        this.nodeService = serviceRegistry.getNodeService();
        this.messageService = serviceRegistry.getMessageService();
        this.ecosPermissionService = (EcosPermissionService) serviceRegistry.getService(EcosPermissionService.QNAME);
        typeDefService = getTypeDefService(serviceRegistry);

        nodes = CacheBuilder.newBuilder()
                            .maximumSize(500)
                            .build(CacheLoader.from(this::createNode));
        qnames = CacheBuilder.newBuilder()
                             .maximumSize(1000)
                             .build(CacheLoader.from(this::createQName));
    }

    private TypeDefService getTypeDefService(ServiceRegistry serviceRegistry) {
        QName typeDefServiceQname = QName.createQName("", CiteckServices.TYPE_DEF_SERVICE_BEAN_NAME);
        try {
            return (TypeDefService) serviceRegistry.getService(typeDefServiceQname);
        } catch (Exception ignored) {
            return null;
        }
    }

    public List<GqlAlfNode> getNodes(Collection<?> keys) {
        return keys.stream()
                   .map(this::getNode)
                   .filter(Optional::isPresent)
                   .map(Optional::get)
                   .collect(Collectors.toList());
    }

    public Optional<GqlAlfNode> getNode(Object key) {
        if (key instanceof GqlAlfNode) {
            return Optional.of((GqlAlfNode) key);
        }
        NodeRef nodeRef = null;
        if (key instanceof NodeRef) {
            nodeRef = (NodeRef) key;
        } else if (key instanceof String && NodeRef.isNodeRef((String) key)) {
            nodeRef = new NodeRef((String) key);
        }
        return nodeRef == null ? Optional.empty() : Optional.of(nodes.getUnchecked(nodeRef));
    }

    private GqlAlfNode createNode(NodeRef nodeRef) {
        return new GqlAlfNode(nodeRef, this);
    }

    public List<GqlQName> getQNames(Collection<?> keys) {
        return keys.stream()
                   .map(this::getQName)
                   .filter(Optional::isPresent)
                   .map(Optional::get)
                   .collect(Collectors.toList());
    }

    public Optional<GqlQName> getQName(Object qname) {
       return qname == null ? Optional.empty() : qnames.getUnchecked(qname);
    }

    private Optional<GqlQName> createQName(Object value) {
        Optional<GqlQName> result;
        if (value instanceof GqlQName) {
            result = Optional.of((GqlQName) value);
        } else if (value instanceof QName) {
            result = Optional.of(new GqlQName((QName) value, this));
        } else if (value instanceof String) {
            String str = (String) value;
            if (str.startsWith("{") || str.contains(":")) {
                QName resolvedQName = QName.resolveToQName(namespaceService, str);
                result = Optional.of(new GqlQName(resolvedQName, this));
            } else {
                result = Optional.empty();
            }
        } else {
            result = Optional.empty();
        }
        return result;
    }

    public SearchService getSearchService() {
        return serviceRegistry.getSearchService();
    }

    public ServiceRegistry getServiceRegistry() {
        return serviceRegistry;
    }

    public Optional<TypeDefService> getTypeDefService() {
        return Optional.ofNullable(this.typeDefService);
    }

    @SuppressWarnings("unchecked")
    public <T> T getService(QName name) {
        return (T) serviceRegistry.getService(name);
    }

    @SuppressWarnings("unchecked")
    public <T> T getService(String beanId) {
        return (T) servicesCache.computeIfAbsent(beanId, bean ->
            serviceRegistry.getService(QName.createQName(null, beanId))
        );
    }
}
