package ru.citeck.ecos.role;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.activiti.engine.delegate.VariableScope;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.node.NodeInfo;
import ru.citeck.ecos.utils.NodeUtils;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CaseRoleAssocsDao {

    private final NodeService nodeService;
    private final NamespaceService namespaceService;
    private final CaseRoleService caseRoleService;
    private final NodeUtils nodeUtils;

    @Autowired
    public CaseRoleAssocsDao(NodeUtils nodeUtils,
                             NodeService nodeService,
                             NamespaceService namespaceService,
                             CaseRoleService caseRoleService) {

        this.nodeUtils = nodeUtils;
        this.nodeService = nodeService;
        this.namespaceService = namespaceService;
        this.caseRoleService = caseRoleService;
    }

    public void createRoleAssocs(NodeInfo nodeInfo, QName assoc, Object value) {

        ArrayList<NodeRef> nodeRefs = new ArrayList<>();
        nodeUtils.fillNodeRefsList(value, nodeRefs);

        AlfAndTypeRoles alfAndTypeRoles = splitAlfAndTypeRoles(nodeRefs);
        if (!alfAndTypeRoles.alfRoles.isEmpty()) {
            nodeInfo.createTargetAssociations(alfAndTypeRoles.alfRoles, assoc);
        }
        if (!alfAndTypeRoles.typeRoles.isEmpty()) {
            nodeInfo.setProperty(assocToProp(assoc), alfAndTypeRoles.typeRoles);
        }
    }

    private QName assocToProp(QName assoc) {
        return QName.createQName(assoc.getNamespaceURI(), assoc.getLocalName() + "-prop");
    }

    public void createRoleAssoc(NodeRef nodeRef, QName assoc, NodeRef roleRef) {
        if (caseRoleService.isAlfRole(roleRef)) {
            nodeService.createAssociation(nodeRef, roleRef, assoc);
        } else {
            nodeService.setProperty(nodeRef, assocToProp(assoc), roleRef.toString());
        }
    }

    @NotNull
    public List<NodeRef> getRolesByAssoc(NodeRef nodeRef, QName assocName) {

        QName propName = QName.createQName(assocName.getNamespaceURI(), assocName.getLocalName() + "-prop");
        List<NodeRef> result = new ArrayList<>();

        nodeUtils.fillNodeRefsList(nodeService.getProperty(nodeRef, propName), result);
        nodeUtils.fillNodeRefsList(nodeService.getTargetAssocs(nodeRef, assocName), result);

        Set<NodeRef> assocsSet = new HashSet<>();
        return result.stream().filter(assocsSet::add).collect(Collectors.toList());
    }

    @NotNull
    public List<NodeRef> getRolesByAssoc(Map<QName, Serializable> props, QName assocName) {

        List<NodeRef> result = new ArrayList<>();

        QName propName = QName.createQName(assocName.getNamespaceURI(), assocName.getLocalName() + "-prop");
        nodeUtils.fillNodeRefsList(props.get(propName), result);
        nodeUtils.fillNodeRefsList(props.get(assocName), result);

        Set<NodeRef> assocsSet = new HashSet<>();
        return result.stream().filter(assocsSet::add).collect(Collectors.toList());
    }

    @NotNull
    public List<NodeRef> getRolesByAssoc(VariableScope scope, QName assocName) {

        List<NodeRef> result = new ArrayList<>();
        String key = assocName.toPrefixString(namespaceService)
            .replaceAll(":", "_");

        nodeUtils.fillNodeRefsList(scope.getVariable(key + "-prop"), result);
        nodeUtils.fillNodeRefsList(scope.getVariable(key), result);

        Set<NodeRef> assocsSet = new HashSet<>();
        return result.stream().filter(assocsSet::add).collect(Collectors.toList());
    }

    private AlfAndTypeRoles splitAlfAndTypeRoles(List<NodeRef> roleRefs) {

        ArrayList<NodeRef> alfRoles = new ArrayList<>();
        ArrayList<NodeRef> typeRoles = new ArrayList<>();

        for (NodeRef roleRef : roleRefs) {
            if (caseRoleService.isAlfRole(roleRef)) {
                alfRoles.add(roleRef);
            } else {
                typeRoles.add(roleRef);
            }
        }

        return new AlfAndTypeRoles(alfRoles, typeRoles);
    }

    @Data
    @AllArgsConstructor
    private static class AlfAndTypeRoles {
        ArrayList<NodeRef> alfRoles;
        ArrayList<NodeRef> typeRoles;
    }
}
