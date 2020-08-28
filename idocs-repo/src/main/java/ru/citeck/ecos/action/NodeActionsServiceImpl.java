package ru.citeck.ecos.action;

import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.CacheStats;
import ecos.com.google.common.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.I18NUtil;
import ru.citeck.ecos.action.node.NodeActionDefinition;
import ru.citeck.ecos.action.node.NodeActionsProvider;
import ru.citeck.ecos.action.node.NodeActionsService;
import ru.citeck.ecos.action.v2.NodeActionsV2Provider;
import ru.citeck.ecos.commons.data.ObjectData;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author deathNC on 30.04.2016.
 * @author Pavel Simonov
 */
@Slf4j
public class NodeActionsServiceImpl implements NodeActionsService {

    private static final String PARAM_ACTION_ID = "actionId";
    private static final String PARAM_ACTION_TITLE = "title";
    private static final String PARAM_ACTION_TYPE = "actionType";

    private static final List<String> EXCLUDE_FROM_CONFIG = Arrays.asList(PARAM_ACTION_ID, PARAM_ACTION_TITLE,
            PARAM_ACTION_TYPE);

    private List<NodeActionsV2Provider> v2providersList = new ArrayList<>();
    private final List<NodeActionsProvider> providersList = new ArrayList<>();

    private LoadingCache<ActionsCacheKey, NodeActions> cache;
    private long cacheAge = 600;
    private boolean enableCache = true;

    private NodeService nodeService;

    public void init() {
        cache = CacheBuilder.newBuilder()
                .expireAfterWrite(cacheAge, TimeUnit.SECONDS)
                .maximumSize(1000)
                .build(CacheLoader.from(this::getNodeActionsImpl));
    }

    public List<Map<String, String>> getNodeActionsRaw(NodeRef nodeRef) {

        NodeActions data;

        Date lastModified = getLastModified(nodeRef);
        ActionsCacheKey key = new ActionsCacheKey(lastModified, AuthenticationUtil.getRunAsUser(), nodeRef);

        if (enableCache) {
            data = cache.getUnchecked(key);
        } else {
            data = getNodeActionsImpl(key);
        }

        return data.actionsData;
    }

    @Override
    public List<ActionModule> getNodeActions(NodeRef nodeRef) {
        List<Map<String, String>> rawActions = getNodeActionsRaw(nodeRef);

        List<ActionModule> result = new ArrayList<>();

        for (Map<String, String> actionRaw : rawActions) {
            ActionModule action = new ActionModule();
            action.setId(actionRaw.get("actionId"));
            action.setName(actionRaw.get("title"));
            action.setType(actionRaw.get("actionType"));

            Map<String, String> config = actionRaw.entrySet()
                    .stream()
                    .filter(x -> !EXCLUDE_FROM_CONFIG.contains(x.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            action.setConfig(ObjectData.create(config));
            result.add(action);
        }

        //todo: cache?
        result.addAll(v2providersList.stream()
                                     .flatMap(p -> p.getActions(nodeRef).stream())
                                     .collect(Collectors.toList()));

        return result;
    }

    private NodeActions getNodeActionsImpl(ActionsCacheKey userNode) {

        List<Map<String, String>> actionsData = new ArrayList<>();

        int id = 0;
        for (NodeActionsProvider provider : providersList) {
            List<ru.citeck.ecos.action.node.NodeActionDefinition> list = provider.getNodeActions(userNode.getNodeRef());
            for (NodeActionDefinition action : list) {
                action.setActionId(Integer.toString(id++));
                if (action.isValid()) {
                    String newTitle = I18NUtil.getMessage(action.getTitle());
                    if (newTitle != null) {
                        action.setTitle(newTitle);
                    }
                    actionsData.add(action.getProperties());
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<String, String> entry : action.getProperties().entrySet()) {
                        sb.append(entry.getKey()).append(" = ").append(entry.getValue()).append("; ");
                    }
                    log.warn("Server action is invalid. Properties: " + sb.toString());
                }
            }
        }

        return new NodeActions(userNode.getLastModified(), actionsData);
    }

    private Date getLastModified(NodeRef nodeRef) {
        Date modified = (Date) nodeService.getProperty(nodeRef, ContentModel.PROP_MODIFIED);
        if (modified == null) {
            modified = new Date();
        }
        return modified;
    }

    public void clearCache(NodeRef nodeRef) {
        List<ActionsCacheKey> keysToInvalidate = new ArrayList<>();
        cache.asMap().forEach((k, v) -> {
            if (Objects.equals(k.getNodeRef(), nodeRef)) {
                keysToInvalidate.add(k);
            }
        });
        cache.invalidateAll(keysToInvalidate);
    }

    public void clearCache() {
        cache.invalidateAll();
    }

    public void setEnableCache(boolean enableCache) {
        this.enableCache = enableCache;
    }

    public CacheStats getCacheStats() {
        return cache.stats();
    }

    public void addActionProvider(NodeActionsProvider actionsProvider) {
        providersList.add(actionsProvider);
    }

    @Autowired
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.nodeService = serviceRegistry.getNodeService();
    }

    @Autowired(required = false)
    public void setV2providersList(List<NodeActionsV2Provider> v2providersList) {
        if (v2providersList == null) {
            v2providersList = new ArrayList<>();
        }
        this.v2providersList = v2providersList;
    }

    public void setCacheAge(long cacheAge) {
        this.cacheAge = cacheAge;
    }

    private static class NodeActions {

        final Date lastModified;
        final List<Map<String, String>> actionsData;

        NodeActions(Date lastModified, List<Map<String, String>> actionsData) {
            this.lastModified = lastModified;
            this.actionsData = actionsData;
        }
    }

    @Data
    @AllArgsConstructor
    private static class ActionsCacheKey {
        private Date lastModified;
        private String user;
        private NodeRef nodeRef;
    }
}
