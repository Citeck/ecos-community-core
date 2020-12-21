package ru.citeck.ecos.icase;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.utils.NodeUtils;
import ru.citeck.ecos.utils.RepoUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CaseStatusAssocDao {

    private final NodeService nodeService;
    private final CaseStatusService caseStatusService;
    private final NodeUtils nodeUtils;

    @Autowired
    public CaseStatusAssocDao(NodeService nodeService, CaseStatusService caseStatusService, NodeUtils nodeUtils) {
        this.nodeService = nodeService;
        this.caseStatusService = caseStatusService;
        this.nodeUtils = nodeUtils;
    }

    public void createStatusAssoc(NodeRef nodeRef, QName assoc, NodeRef node) {
        QName assocProp = assocToProp(assoc);
        if (caseStatusService.isAlfRef(node)) {
            nodeService.createAssociation(nodeRef, node, assoc);
            nodeService.setProperty(nodeRef, assocProp, null);
        } else {
            nodeService.setProperty(nodeRef, assocProp, node.getId());
        }
    }

    private QName assocToProp(QName assoc) {
        return QName.createQName(assoc.getNamespaceURI(), assoc.getLocalName() + "-prop");
    }

    @NotNull
    public List<NodeRef> getStatusByAssoc(NodeRef nodeRef, QName assocName) {

        QName propName = assocToProp(assocName);
        List<NodeRef> result = new ArrayList<>();

        nodeUtils.fillNodeRefsList(statusToNode((String) nodeService.getProperty(nodeRef, propName)), result);
        nodeUtils.fillNodeRefsList(nodeService.getTargetAssocs(nodeRef, assocName), result);

        Set<NodeRef> assocsSet = new HashSet<>();
        return result.stream().filter(assocsSet::add).collect(Collectors.toList());
    }

    @Nullable
    public NodeRef getFirstStatusByAssoc(NodeRef nodeRef, QName assocName) {
        QName propName = assocToProp(assocName);
        NodeRef node = statusToNode((String) nodeService.getProperty(nodeRef, propName));
        if (node == null) {
            node = nodeUtils.getNodeRefByObject(RepoUtils.getFirstTargetAssoc(nodeRef, assocName, nodeService));
        }
        return node;
    }

    public NodeRef statusToNode(String statusId) {
        if (StringUtils.isBlank(statusId)) {
            return null;
        }
        return nodeUtils.getNodeRefByObject("et-status://virtual/" + statusId);
    }

}
