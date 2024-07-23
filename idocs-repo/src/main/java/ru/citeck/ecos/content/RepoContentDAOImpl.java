package ru.citeck.ecos.content;

import org.alfresco.model.ContentModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.content.dao.ContentDAO;
import ru.citeck.ecos.content.dao.NodeDataReader;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records2.IterableRecords;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.search.ftsquery.BinOperator;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.search.ftsquery.OperatorExpected;
import ru.citeck.ecos.utils.LazyNodeRef;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Config registry. Allow to cache content parsing result and update it when content was changed
 * @param <T> type of parsed content data
 *
 * @author Pavel Simonov
 */
public class RepoContentDAOImpl<T> implements RepoContentDAO<T> {

    private static final Log logger = LogFactory.getLog(RepoContentDAOImpl.class);
    private static final int MEMORY_LEAK_THRESHOLD = 100000;

    private LazyNodeRef rootRef;

    private QName configNodeType;
    private QName contentFieldName = ContentModel.PROP_CONTENT;
    private QName childAssocType = ContentModel.ASSOC_CONTAINS;

    private Date lastRootChangedDate = new Date(0);

    private Map<NodeRef, ContentData<T>> configDataByNode = new ConcurrentHashMap<>();
    private Map<Map<QName, Serializable>, List<ContentData<T>>> configDataByKeys = new ConcurrentHashMap<>();

    protected NodeService nodeService;
    protected ContentService contentService;
    protected SearchService searchService;
    protected DictionaryService dictionaryService;
    protected RecordsService recordsService;

    private ContentDAO<T> contentDAO;
    private NodeDataReader<T> nodeDataReader;

    @Override
    public Optional<ContentData<T>> getContentData(NodeRef nodeRef) {
        ContentData<T> config = getContentDataImpl(nodeRef);
        if (config.updateData()) {
            return Optional.of(config);
        } else {
            configDataByNode.remove(nodeRef);
            return Optional.empty();
        }
    }

    @Override
    public List<ContentData<T>> getContentData(Map<QName, Serializable> keys, boolean ignoreWithoutData) {

        checkChangeDate();

        Map<QName, Serializable> localKeys = new HashMap<>(keys);

        List<ContentData<T>> result = configDataByKeys.computeIfAbsent(localKeys, this::searchData);
        result.forEach(ContentData::updateData);

        if (configDataByKeys.size() > MEMORY_LEAK_THRESHOLD) {
            logger.warn("Cache size increased to " + MEMORY_LEAK_THRESHOLD + " elements. Seems it is memory leak");
        }

        if (ignoreWithoutData) {
            return result.stream()
                         .filter(d -> d.getData().isPresent())
                         .collect(Collectors.toList());
        } else {
            return result;
        }
    }

    @Override
    public NodeRef createNode(Map<QName, Serializable> properties) {

        String name = (String) properties.get(ContentModel.PROP_NAME);

        if (name == null) {
            name = "contentData";
        }

        NodeRef node = nodeService.getChildByName(rootRef.getNodeRef(), childAssocType, name);

        if (node != null) {

            String baseName = FilenameUtils.getBaseName(name);
            String extension = FilenameUtils.getExtension(name);
            if (StringUtils.isNotBlank(extension)) {
                extension = "." + extension;
            }

            int idx = 1;

            do {
                name = String.format("%s(%d)%s", baseName, idx++, extension);
                node = nodeService.getChildByName(rootRef.getNodeRef(), childAssocType, name);
            } while (node != null);

            properties.put(ContentModel.PROP_NAME, name);
        }

        QName assocName = QName.createQName(childAssocType.getNamespaceURI(), name);
        return nodeService.createNode(rootRef.getNodeRef(),
                                      childAssocType,
                                      assocName,
                                      configNodeType,
                                      properties).getChildRef();
    }

    /**
     * Clear cache
     */
    @Override
    public void clearCache() {
        configDataByNode.clear();
        configDataByKeys.clear();
    }

    @Override
    public void forEach(Consumer<ContentData<T>> consumer) {

        RecordsQuery query = new RecordsQuery();
        query.setMaxItems(0);
        query.setLanguage(SearchService.LANGUAGE_FTS_ALFRESCO);
        query.setQuery("PARENT:\"" + rootRef.getNodeRef() + "\"");
        query.setSourceId(AlfNodesRecordsDAO.ID);

        Iterable<EntityRef> records = new IterableRecords(recordsService, query);
        for (EntityRef ref : records) {
            NodeRef nodeRef = new NodeRef(ref.getLocalId());
            ContentData<T> data = getContentDataImpl(nodeRef);
            data.updateData();
            consumer.accept(data);
        }
    }

    private List<ContentData<T>> searchData(Map<QName, Serializable> keys) {

        Map<QName, Serializable> notNullProps = new HashMap<>();
        Set<QName> nullProps = new HashSet<>();

        for (Map.Entry<QName, Serializable> entry : keys.entrySet()) {
            QName key = entry.getKey();
            Serializable value = entry.getValue();
            if (value != null) {
                notNullProps.put(key, value);
            } else {
                nullProps.add(key);
            }
        }

        OperatorExpected query = FTSQuery.create()
                                         .parent(rootRef.getNodeRef()).and()
                                         .type(configNodeType)
                                         .transactional();

        if (!notNullProps.isEmpty()) {
            query.and().values(notNullProps, BinOperator.AND, true);
        }

        return query.query(searchService)
                    .stream()
                    .filter(ref -> {
                        for (QName propName : nullProps) {
                            Serializable value = nodeService.getProperty(ref, propName);
                            if (value != null && (!(value instanceof String) ||
                                    StringUtils.isNotBlank((String) value))) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .map(this::getContentDataImpl)
                    .collect(Collectors.toList());
    }

    private ContentData<T> getContentDataImpl(NodeRef nodeRef) {
        return configDataByNode.computeIfAbsent(nodeRef, r -> new ContentData<>(r, this));
    }

    private void checkChangeDate() {
        Date lastChanged = (Date) nodeService.getProperty(rootRef.getNodeRef(), ContentModel.PROP_MODIFIED);
        if (lastChanged.getTime() > lastRootChangedDate.getTime()) {
            configDataByKeys.clear();
            lastRootChangedDate = lastChanged;
        }
    }

    public void setRootNode(LazyNodeRef rootRef) {
        this.rootRef = rootRef;
    }

    public NodeRef getRootRef() {
        return rootRef.getNodeRef();
    }

    public void setConfigNodeType(QName configNodeType) {
        this.configNodeType = configNodeType;
    }

    public QName getConfigNodeType() {
        return configNodeType;
    }

    public void setContentDAO(ContentDAO<T> contentDAO) {
        this.contentDAO = contentDAO;
    }

    public ContentDAO<T> getContentDAO() {
        return contentDAO;
    }

    public void setContentFieldName(QName contentFieldName) {
        this.contentFieldName = contentFieldName;
    }

    public QName getContentFieldName() {
        return contentFieldName;
    }

    public NodeService getNodeService() {
        return nodeService;
    }

    public ContentService getContentService() {
        return contentService;
    }

    public void setChildAssocType(QName childAssocType) {
        this.childAssocType = childAssocType;
    }

    public QName getChildAssocType() {
        return childAssocType;
    }

    public NodeDataReader<T> getNodeDataReader() {
        return nodeDataReader;
    }

    public void setNodeDataReader(NodeDataReader<T> nodeDataReader) {
        this.nodeDataReader = nodeDataReader;
    }

    @Autowired
    public void setRecordsService(RecordsService recordsService) {
        this.recordsService = recordsService;
    }

    @Autowired
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.nodeService = serviceRegistry.getNodeService();
        this.searchService = serviceRegistry.getSearchService();
        this.contentService = serviceRegistry.getContentService();
        this.dictionaryService = serviceRegistry.getDictionaryService();
    }
}
