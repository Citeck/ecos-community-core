package ru.citeck.ecos.service;

import org.alfresco.repo.domain.node.ChildAssocEntity;
import org.alfresco.repo.domain.node.NodeDAO;
import org.alfresco.repo.domain.node.NodeEntity;
import org.alfresco.repo.domain.qname.QNameDAO;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.ibatis.executor.result.DefaultResultContext;
import org.apache.ibatis.session.ResultContext;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.domain.node.ChildAssocEntityLimit;

import java.util.List;

@Component
public class EcosNodeDao {

    private static final String SELECT_CHILD_ASSOCS_OF_PARENT_LIMITED = "custom.alfresco.node.select.children.select_ChildAssocsOfParent_Limited";
    private static final String SELECT_NODE_ASSOCS_BY_TARGET = "alfresco.node.select_NodeAssocsByTarget";

    private QNameDAO qnameDao;
    private SqlSessionTemplate customTemplate;

    public void getChildAssocs(Long parentNodeId,
                               QName assocTypeQName,
                               QName assocQName,
                               String childName,
                               Boolean isPrimary,
                               int maxResults,
                               NodeDAO.ChildAssocRefQueryCallback resultsCallback) {
        selectChildAssocsLimited(
            parentNodeId,
            assocTypeQName,
            assocQName,
            childName,
            isPrimary,
            maxResults,
            resultsCallback);
    }

    public void getChildAssocs(Long parentNodeId,
                               QName assocTypeQName,
                               QName assocQName,
                               Boolean isPrimary,
                               int maxResults,
                               NodeDAO.ChildAssocRefQueryCallback resultsCallback) {
        selectChildAssocsLimited(
            parentNodeId,
            assocTypeQName,
            assocQName,
            null,
            isPrimary,
            maxResults,
            resultsCallback);
    }


    public void selectChildAssocsLimited(Long parentNodeId,
                                         QName assocTypeQName,
                                         QName assocQName,
                                         String childName,
                                         Boolean isPrimary,
                                         final int maxResults,
                                         NodeDAO.ChildAssocRefQueryCallback resultsCallback) {

        if (!AuthenticationUtil.isRunAsUserTheSystemUser()) {
            throw new IllegalStateException("Method call is available only to the system user");
        }

        ChildAssocEntityLimit assoc = new ChildAssocEntityLimit();
        // Parent
        NodeEntity parentNode = new NodeEntity();
        parentNode.setId(parentNodeId);
        assoc.setParentNode(parentNode);
        assoc.setLimit(maxResults);
        assoc.setPrimary(isPrimary);

        // Type QName
        if (assocTypeQName != null) {
            if (!assoc.setTypeQNameAll(qnameDao, assocTypeQName, false)) {
                resultsCallback.done();
                return;                 // Shortcut
            }
        }
        // QName
        if (assocQName != null) {
            if (!assoc.setQNameAll(qnameDao, assocQName, false)) {
                resultsCallback.done();
                return;                 // Shortcut
            }
        }
        if (childName != null) {
            assoc.setChildNodeNameAll(null, assocTypeQName, childName);
        }
        // Order
        assoc.setOrdered(resultsCallback.orderResults());

        ChildAssocResultHandler resultHandler = new ChildAssocResultHandler(resultsCallback);

        RowBounds rowBounds = new RowBounds(0, maxResults);
        List<?> entities = customTemplate.selectList(SELECT_CHILD_ASSOCS_OF_PARENT_LIMITED, assoc, rowBounds);
        final DefaultResultContext resultContext = new DefaultResultContext();
        for (Object entity : entities) {
            resultContext.nextResultObject(entity);
            resultHandler.handleResult(resultContext);
        }

        resultsCallback.done();
    }


    private class ChildAssocResultHandler implements ResultHandler {
        private final ChildAssocResultHandlerFilter filter;
        private final NodeDAO.ChildAssocRefQueryCallback resultsCallback;
        private boolean more = true;

        private ChildAssocResultHandler(NodeDAO.ChildAssocRefQueryCallback resultsCallback) {
            this(null, resultsCallback);
        }

        private ChildAssocResultHandler(ChildAssocResultHandlerFilter filter,
                                        NodeDAO.ChildAssocRefQueryCallback resultsCallback) {
            this.filter = filter;
            this.resultsCallback = resultsCallback;
        }

        public void handleResult(ResultContext context) {
            // Do nothing if no further results are required
            // TODO: Use iBatis' new feature (when we upgrade) to kill the resultset walking
            if (!more) {
                return;
            }
            ChildAssocEntity assoc = (ChildAssocEntity) context.getResultObject();
            if (filter != null && !filter.isResult(assoc)) {
                // Filtered out
                return;
            }
            Pair<Long, ChildAssociationRef> childAssocPair = assoc.getPair(qnameDao);
            Pair<Long, NodeRef> parentNodePair = assoc.getParentNode().getNodePair();
            Pair<Long, NodeRef> childNodePair = assoc.getChildNode().getNodePair();
            // Call back
            boolean more = resultsCallback.handle(childAssocPair, parentNodePair, childNodePair);
            if (!more) {
                this.more = false;
            }
        }
    }

    private interface ChildAssocResultHandlerFilter {
        boolean isResult(ChildAssocEntity assoc);
    }

    @Autowired
    @Qualifier("customSqlSessionTemplate")
    public void setCustomTemplate(SqlSessionTemplate customTemplate) {
        this.customTemplate = customTemplate;
    }

    @Autowired
    @Qualifier("qnameDAO")
    public void setQnameDao(QNameDAO qnameDao) {
        this.qnameDao = qnameDao;
    }

}
