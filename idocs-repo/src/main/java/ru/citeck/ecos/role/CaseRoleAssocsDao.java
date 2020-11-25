package ru.citeck.ecos.role;

import org.activiti.engine.delegate.VariableScope;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNode;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class CaseRoleAssocsDao {

    private final NodeService nodeService;
    private final NamespaceService namespaceService;
    private final CaseRoleService caseRoleService;

    @Autowired
    public CaseRoleAssocsDao(NodeService nodeService,
                             NamespaceService namespaceService,
                             CaseRoleService caseRoleService) {
        this.nodeService = nodeService;
        this.namespaceService = namespaceService;
        this.caseRoleService = caseRoleService;
    }

    public void createRoleAssoc(NodeRef nodeRef, QName assoc, NodeRef roleRef) {
        if (caseRoleService.isAlfRole(roleRef)) {
            nodeService.createAssociation(nodeRef, roleRef, assoc);
        } else {
            nodeService.setProperty(nodeRef,
                QName.createQName(assoc.getNamespaceURI(), assoc.getLocalName() + "-prop"), roleRef.toString());
        }
    }

    @NotNull
    public List<NodeRef> getRolesByAssoc(NodeRef nodeRef, QName assocName) {

        QName propName = QName.createQName(assocName.getNamespaceURI(), assocName.getLocalName() + "-prop");
        List<NodeRef> result = new ArrayList<>();

        fillRolesList(nodeService.getProperty(nodeRef, propName), result);
        fillRolesList(nodeService.getTargetAssocs(nodeRef, assocName), result);

        return result;
    }

    @NotNull
    public List<NodeRef> getRolesByAssoc(Map<QName, Serializable> props, QName assocName) {

        List<NodeRef> result = new ArrayList<>();

        QName propName = QName.createQName(assocName.getNamespaceURI(), assocName.getLocalName() + "-prop");
        fillRolesList(props.get(propName), result);
        fillRolesList(props.get(assocName), result);

        return result;
    }

    @NotNull
    public List<NodeRef> getRolesByAssoc(VariableScope scope, QName assocName) {

        List<NodeRef> result = new ArrayList<>();
        String key = assocName.toPrefixString(namespaceService)
            .replaceAll(":", "_");

        fillRolesList(scope.getVariable(key + "-prop"), result);
        fillRolesList(scope.getVariable(key), result);

        return result;
    }

    private void fillRolesList(Object value, List<NodeRef> resultList) {
        if (value == null) {
            return;
        }
        if (value instanceof NodeRef) {
            resultList.add((NodeRef) value);
        } else if (value instanceof String) {
            String strValue = (String) value;
            if (StringUtils.isNotBlank(strValue)) {
                resultList.add(new NodeRef(strValue));
            }
        } else if (value instanceof AssociationRef) {
            resultList.add(((AssociationRef) value).getTargetRef());
        } else if (value instanceof Collection) {
            for (Object item : (Collection<?>) value) {
                fillRolesList(item, resultList);
            }
        } else if (value instanceof ActivitiScriptNode) {
            NodeRef nodeRef = ((ActivitiScriptNode) value).getNodeRef();
            if (nodeRef != null) {
                resultList.add(nodeRef);
            }
        } else if (value instanceof ScriptNode) {
            NodeRef nodeRef = ((ScriptNode) value).getNodeRef();
            if (nodeRef != null) {
                resultList.add(nodeRef);
            }
        }
    }
}
