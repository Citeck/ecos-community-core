package ru.citeck.ecos.domain.node;

import org.alfresco.repo.domain.node.NodeAssocEntity;
import org.alfresco.repo.domain.node.NodeDAO;
import org.alfresco.repo.domain.node.NodeEntity;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.InvalidNodeRefException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.QNamePattern;
import org.alfresco.util.Pair;
import org.alfresco.util.ParameterCheck;
import org.apache.ibatis.session.RowBounds;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EcosNodeService {

    private static final String SELECT_NODE_ASSOCS_BY_TARGET = "alfresco.node.select_NodeAssocsByTarget";

    private NodeDAO nodeDao;
    private EcosNodeDao ecosNodeDao;
    private QNameDAO qnameDao;
    private SqlSessionTemplate template;

    public List<ChildAssociationRef> getChildAssocsLimited(NodeRef nodeRef,
                                                           final QNamePattern typeQNamePattern,
                                                           final QNamePattern qnamePattern,
                                                           String childName,
                                                           final int maxResults,
                                                           final boolean preload) {
        // Get the node
        Pair<Long, NodeRef> nodePair = getNodePairNotNull(nodeRef);

        // We have a callback handler to filter results
        final List<ChildAssociationRef> results = new ArrayList<ChildAssociationRef>(10);
        NodeDAO.ChildAssocRefQueryCallback callback = new NodeDAO.ChildAssocRefQueryCallback() {
            public boolean preLoadNodes() {
                return preload;
            }

            @Override
            public boolean orderResults() {
                return true;
            }

            public boolean handle(
                Pair<Long, ChildAssociationRef> childAssocPair,
                Pair<Long, NodeRef> parentNodePair,
                Pair<Long, NodeRef> childNodePair) {
                if (typeQNamePattern != null && !typeQNamePattern.isMatch(childAssocPair.getSecond().getTypeQName())) {
                    return true;
                }
                if (qnamePattern != null && !qnamePattern.isMatch(childAssocPair.getSecond().getQName())) {
                    return true;
                }
                results.add(childAssocPair.getSecond());
                return true;
            }

            public void done() {}
        };

        // Get the assocs pointing to it
        QName typeQName = (typeQNamePattern instanceof QName) ? (QName) typeQNamePattern : null;
        QName qname = (qnamePattern instanceof QName) ? (QName) qnamePattern : null;

        ecosNodeDao.getChildAssocs(nodePair.getFirst(), typeQName, qname, childName, null, maxResults, callback);
        // Done
        return results;
    }

    public List<ChildAssociationRef> getChildAssocsLimited(NodeRef nodeRef,
                                                           final QNamePattern typeQNamePattern,
                                                           final QNamePattern qnamePattern,
                                                           final int maxResults,
                                                           final boolean preload) {
        return getChildAssocsLimited(nodeRef, typeQNamePattern, qnamePattern, null, maxResults, preload);
    }


    public List<AssociationRef> getSourceAssocsByType(@NotNull NodeRef nodeRef, QName typeQName, int skipCount, int maxItems) {

        List<AssociationRef> results = new ArrayList<>();

        Pair<Long, QName> typeQNamePair = qnameDao.getQName(typeQName);
        if (typeQNamePair == null) {
            return results;
        }

        Pair<Long, NodeRef> targetNodePair = getNodePairNotNull(nodeRef);

        NodeAssocEntity assocEntity = new NodeAssocEntity();
        NodeEntity targetNode = new NodeEntity();
        targetNode.setId(targetNodePair.getFirst());
        assocEntity.setTargetNode(targetNode);

        Long typeQNameId = typeQNamePair.getFirst();
        assocEntity.setTypeQNameId(typeQNameId);

        RowBounds rowBounds = new RowBounds(skipCount, maxItems + 1);
        List<?> entities = template.selectList(SELECT_NODE_ASSOCS_BY_TARGET, assocEntity, rowBounds);

        for (Object entity : entities) {
            NodeAssocEntity assoc = (NodeAssocEntity) entity;
            if (results.size() < maxItems) {
                results.add(assoc.getAssociationRef(qnameDao));
            }
        }

        return results;
    }

    private Pair<Long, NodeRef> getNodePairNotNull(NodeRef nodeRef) throws InvalidNodeRefException {
        ParameterCheck.mandatory("nodeRef", nodeRef);
        Pair<Long, NodeRef> unchecked = nodeDao.getNodePair(nodeRef);
        if (unchecked == null) {
            NodeRef.Status nodeStatus = nodeDao.getNodeRefStatus(nodeRef);
            throw new InvalidNodeRefException("Node does not exist: " + nodeRef + " (status:" + nodeStatus + ")", nodeRef);
        }
        return unchecked;
    }

    @Autowired
    @Qualifier("nodeDAO")
    public void setNodeDao(NodeDAO nodeDao) {
        this.nodeDao = nodeDao;
    }

    @Autowired
    public void setEcosNodeDao(EcosNodeDao ecosNodeDao) {
        this.ecosNodeDao = ecosNodeDao;
    }

    @Autowired
    @Qualifier("repoSqlSessionTemplate")
    public void setTemplate(SqlSessionTemplate template) {
        this.template = template;
    }

    @Autowired
    @Qualifier("qnameDAO")
    public void setQnameDao(QNameDAO qnameDao) {
        this.qnameDao = qnameDao;
    }
}
