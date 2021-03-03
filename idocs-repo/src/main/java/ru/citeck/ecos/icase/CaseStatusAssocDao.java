package ru.citeck.ecos.icase;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.model.lib.type.service.TypeDefService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.utils.NodeUtils;
import ru.citeck.ecos.utils.RepoUtils;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CaseStatusAssocDao {

    private final NodeService nodeService;
    private final CaseStatusService caseStatusService;
    private final NodeUtils nodeUtils;
    private final TypeDefService typeDefService;

    @Autowired
    public CaseStatusAssocDao(NodeService nodeService, CaseStatusService caseStatusService,
                              NodeUtils nodeUtils, TypeDefService typeDefService) {
        this.nodeService = nodeService;
        this.caseStatusService = caseStatusService;
        this.nodeUtils = nodeUtils;
        this.typeDefService = typeDefService;
    }

    public void createAssocAndSetEcosStatusByAssoc(NodeRef nodeRef, QName assoc, NodeRef node) {
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

    @Nullable
    public NodeRef getStatusByAssoc(NodeRef nodeRef, QName assocName) {
        QName propName = assocToProp(assocName);

        RecordRef typeRef = getTypeRefByCaseNode(nodeRef);
        String ecosStatusName = (String) nodeService.getProperty(nodeRef, propName);
        NodeRef node = getVirtualStatus(typeRef.getId(), ecosStatusName);

        if (node == null) {
            node = nodeUtils.getNodeRefByObject(RepoUtils.getFirstTargetAssoc(nodeRef, assocName, nodeService));
        }
        return node;
    }

    @NotNull
    public List<NodeRef> getStatusesByAssoc(NodeRef nodeRef, QName assoc) {
        QName propName = assocToProp(assoc);
        List<NodeRef> result = new ArrayList<>();

        RecordRef typeRef = getTypeRefByCaseNode(nodeRef);
        String ecosStatusName = (String) nodeService.getProperty(nodeRef, propName);
        NodeRef virtualStatus = getVirtualStatus(typeRef.getId(), ecosStatusName);

        nodeUtils.fillNodeRefsList(virtualStatus, result);
        nodeUtils.fillNodeRefsList(nodeService.getTargetAssocs(nodeRef, assoc), result);

        Set<NodeRef> assocsSet = new HashSet<>();
        return result.stream().filter(assocsSet::add).collect(Collectors.toList());
    }

    @NotNull
    private RecordRef getTypeRefByCaseNode(NodeRef nodeRef) {
        RecordRef nodeRecord = RecordRef.create("alfresco", nodeRef.toString());
        return typeDefService.getTypeRef(nodeRecord);
    }

    public void setStatusesByAssoc(NodeRef nodeRef, QName assoc, List<NodeRef> nodeRefList) {
        nodeService.getTargetAssocs(nodeRef, assoc).forEach(assocRef -> {
            nodeService.removeAssociation(nodeRef, assocRef.getTargetRef(), assoc);
        });

        List<String> propList = new LinkedList<>();
        nodeRefList.forEach(nodeAssoc -> {
            if (caseStatusService.isAlfRef(nodeAssoc)) {
                nodeService.createAssociation(nodeRef, nodeAssoc, assoc);
            } else {
                propList.add(nodeAssoc.getId());
            }
        });

        QName propName = assocToProp(assoc);
        nodeService.setProperty(nodeRef, propName, (Serializable) propList);
    }

    public NodeRef getVirtualStatus(String etype, String statusId) {
        if (StringUtils.isBlank(statusId)) {
            return null;
        }
        return nodeUtils.getNodeRefByObject("et-status://" + etype + "/" + statusId);
    }

}
