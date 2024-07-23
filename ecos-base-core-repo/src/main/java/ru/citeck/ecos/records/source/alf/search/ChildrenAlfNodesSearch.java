package ru.citeck.ecos.records.source.alf.search;

import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ChildrenAlfNodesSearch implements AlfNodesSearch {

    public static final String LANGUAGE = "children";

    private NodeService nodeService;
    private NamespaceService namespaceService;

    @Autowired
    public ChildrenAlfNodesSearch(AlfNodesRecordsDAO recordsDao) {
        recordsDao.register(this);
    }

    @Override
    public RecordsQueryResult<EntityRef> queryRecords(RecordsQuery query, Long afterDbId, Date afterCreated) {

        Query assocsQuery = query.getQuery(Query.class);

        QName assocQName = QName.resolveToQName(namespaceService, assocsQuery.assocName);
        NodeRef parentRef = new NodeRef(assocsQuery.parent);

        List<NodeRef> children = fillChildren(parentRef, assocQName, new ArrayList<>(), assocsQuery.recursive);

        RecordsQueryResult<EntityRef> results = new RecordsQueryResult<>();
        results.setRecords(children.stream()
                                   .map(r -> EntityRef.valueOf(r.toString()))
                                   .collect(Collectors.toList()));
        results.setHasMore(false);
        results.setTotalCount(children.size());

        return results;
    }

    private List<NodeRef> fillChildren(NodeRef parent, QName assocName, List<NodeRef> result, boolean recursive) {
        List<ChildAssociationRef> assocs = nodeService.getChildAssocs(parent, assocName, RegexQNamePattern.MATCH_ALL);
        assocs.forEach(a -> {
            result.add(a.getChildRef());
            if (recursive) {
                fillChildren(a.getChildRef(), assocName, result, recursive);
            }
        });
        return result;
    }

    @Override
    public AfterIdType getAfterIdType() {
        return null;
    }

    @Override
    public String getLanguage() {
        return LANGUAGE;
    }

    @Autowired
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.nodeService = serviceRegistry.getNodeService();
        this.namespaceService = serviceRegistry.getNamespaceService();
    }

    public static class Query {
        public String parent;
        public String assocName;
        public boolean recursive = false;
    }
}
