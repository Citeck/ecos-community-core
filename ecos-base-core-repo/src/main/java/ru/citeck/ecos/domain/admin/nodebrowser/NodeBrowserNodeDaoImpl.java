package ru.citeck.ecos.domain.admin.nodebrowser;

import org.alfresco.repo.domain.node.*;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.util.Pair;
import org.apache.ibatis.session.RowBounds;
import org.jetbrains.annotations.NotNull;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.service.EcosNodeService;

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

    private static final String SELECT_NODE_ASSOCS_BY_TARGET = "alfresco.node.select_NodeAssocsByTarget";

    private NodeDAO nodeDao;
    private QNameDAO qnameDao;
    private SqlSessionTemplate template;
    private EcosNodeService ecosNodeService;

    @NotNull
    @Override
    public NodeElementsResult<ChildAssociationRef> getChildAssocs(@NotNull NodeRef nodeRef, int skipCount, int maxItems) {

        List<ChildAssociationRef> results = new ArrayList<>(10);

        List<ChildAssociationRef> entities = AuthenticationUtil.runAsSystem(() -> ecosNodeService.getChildAssocsLimited(nodeRef, null,
            null, maxItems, false));
        for (ChildAssociationRef entity : entities) {
            if (results.size() < maxItems) {
                results.add(entity);
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
    public void setEcosNodeService(EcosNodeService ecosNodeService) {
        this.ecosNodeService = ecosNodeService;
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
