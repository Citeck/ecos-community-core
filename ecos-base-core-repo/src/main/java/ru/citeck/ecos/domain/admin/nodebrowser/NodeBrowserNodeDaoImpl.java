package ru.citeck.ecos.domain.admin.nodebrowser;

import org.alfresco.repo.domain.node.ChildAssocEntity;
import org.alfresco.repo.domain.node.NodeAssocEntity;
import org.alfresco.repo.domain.node.NodeDAO;
import org.alfresco.repo.domain.node.NodeEntity;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.util.Pair;
import org.apache.ibatis.session.RowBounds;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * NodeBrowser component to add pagination support for associations.
 * This bean solves problem with loading a huge number of associations when node is opened in NodeBrowser.
 *
 * @see org.alfresco.repo.web.scripts.admin.NodeBrowserPost
 */
@Component
public class NodeBrowserNodeDaoImpl implements NodeBrowserNodeDao {

    private static final String SELECT_CHILD_ASSOCS_OF_PARENT_LIMITED = "alfresco.node.select.children.select_ChildAssocsOfParent_Limited";
    private static final String SELECT_NODE_ASSOCS_BY_TARGET = "alfresco.node.select_NodeAssocsByTarget";

    private NodeDAO nodeDao;
    private QNameDAO qnameDao;
    private SqlSessionTemplate template;

    @NotNull
    @Override
    public NodeElementsResult<ChildAssociationRef> getChildAssocs(@NotNull NodeRef nodeRef, int skipCount, int maxItems) {

        // Get the node
        Pair<Long, NodeRef> parentNodePair = getNodePairNotNull(nodeRef);

        ChildAssocEntity assocEntity = new ChildAssocEntity();

        NodeEntity parentNode = new NodeEntity();
        parentNode.setId(parentNodePair.getFirst());
        assocEntity.setParentNode(parentNode);
        assocEntity.setOrdered(true);

        List<ChildAssociationRef> results = new ArrayList<>(10);

        RowBounds rowBounds = new RowBounds(skipCount, maxItems + 1);
        List<?> entities = template.selectList(SELECT_CHILD_ASSOCS_OF_PARENT_LIMITED, assocEntity, rowBounds);

        for (Object entity : entities) {

            ChildAssocEntity assoc = (ChildAssocEntity) entity;
            Pair<Long, ChildAssociationRef> childAssocPair = assoc.getPair(qnameDao);

            if (results.size() < maxItems) {
                results.add(childAssocPair.getSecond());
            }
        }

        return new NodeElementsResult<>(results, results.size() > maxItems);
    }

    @NotNull
    @Override
    public NodeElementsResult<AssociationRef> getSourceAssocs(@NotNull NodeRef nodeRef, int skipCount, int maxItems) {

        Pair<Long, NodeRef> targetNodePair = getNodePairNotNull(nodeRef);

        NodeAssocEntity assocEntity = new NodeAssocEntity();
        NodeEntity targetNode = new NodeEntity();
        targetNode.setId(targetNodePair.getFirst());
        assocEntity.setTargetNode(targetNode);

        RowBounds rowBounds = new RowBounds(skipCount, maxItems + 1);
        List<?> entities = template.selectList(SELECT_NODE_ASSOCS_BY_TARGET, assocEntity, rowBounds);
        List<AssociationRef> results = new ArrayList<>(10);

        for (Object entity : entities) {
            NodeAssocEntity assoc = (NodeAssocEntity) entity;
            if (results.size() < maxItems) {
                results.add(assoc.getAssociationRef(qnameDao));
            }
        }

        return new NodeElementsResult<>(results, results.size() > maxItems);
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

    @Autowired
    @Qualifier("nodeDAO")
    public void setNodeDao(NodeDAO nodeDao) {
        this.nodeDao = nodeDao;
    }
}
