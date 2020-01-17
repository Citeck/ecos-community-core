/*
 * Copyright (C) 2008-2015 Citeck LLC.
 *
 * This file is part of Citeck EcoS
 *
 * Citeck EcoS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Citeck EcoS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Citeck EcoS. If not, see <http://www.gnu.org/licenses/>.
 */
package ru.citeck.ecos.webscripts.invariants;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespacePrefixResolver;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.springframework.extensions.webscripts.Cache;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;
import ru.citeck.ecos.attr.NodeAttributeService;
import ru.citeck.ecos.invariants.view.NodeView;
import ru.citeck.ecos.invariants.view.NodeViewService;
import ru.citeck.ecos.webscripts.utils.WebScriptUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NodeViewGet extends DeclarativeWebScript {

    private static final String PARAM_TYPE = "type";
    private static final String PARAM_VIEW_ID = "viewId";
    private static final String PARAM_MODE = "mode";
    private static final String PARAM_NODEREF = "nodeRef";
    private static final String PARAM_NODEREF_ATTR = "nodeRefAttr";

    private static final String MODEL_VIEW = "view";
    private static final String MODEL_CAN_BE_DRAFT = "canBeDraft";
    private static final String MODEL_NODEREF = "nodeRef";

    private static final String TEMPLATE_PARAM_PREFIX = "param_";

    private NodeService nodeService;
    private NodeViewService nodeViewService;
    private NamespacePrefixResolver prefixResolver;
    private NodeAttributeService nodeAttributeService;
    private NamespaceService namespaceService;

    @Override
    protected Map<String, Object> executeImpl(WebScriptRequest req, Status status, Cache cache) {

        String typeParam = req.getParameter(PARAM_TYPE);
        String viewId = req.getParameter(PARAM_VIEW_ID);
        String mode = req.getParameter(PARAM_MODE);
        String nodeRefParam = req.getParameter(PARAM_NODEREF);
        String nodeRefAttrParam = req.getParameter(PARAM_NODEREF_ATTR);

        boolean canBeDraft;
        NodeRef nodeRef = null;

        NodeView.Builder builder = new NodeView.Builder(prefixResolver);

        if (typeParam != null && !typeParam.isEmpty()) {
            builder.className(typeParam);
            canBeDraft = nodeViewService.canBeDraft(QName.resolveToQName(prefixResolver, typeParam));
        } else if (nodeRefParam != null && !nodeRefParam.isEmpty()) {
            if (!NodeRef.isNodeRef(nodeRefParam)) {
                status.setCode(Status.STATUS_BAD_REQUEST, "Parameter '" + PARAM_NODEREF + "' should contain nodeRef");
                return null;
            }
            nodeRef = new NodeRef(nodeRefParam);
            if (!nodeService.exists(nodeRef)) {
                status.setCode(Status.STATUS_NOT_FOUND, "Node " + nodeRefParam + " does not exist");
                return null;
            }
            if (StringUtils.isNotBlank(nodeRefAttrParam)) {
                nodeRef = getNodeRefByAttribute(nodeRef, nodeRefAttrParam);
                if (nodeRef == null) {
                    status.setCode(Status.STATUS_NOT_FOUND, "Attribute " + nodeRefAttrParam + " of node " + nodeRefParam + " does not exist");
                    return null;
                }
            }
            builder.className(nodeService.getType(nodeRef));
            canBeDraft = nodeViewService.canBeDraft(nodeRef);
        } else {
            status.setCode(Status.STATUS_BAD_REQUEST, "Either type, or nodeRef parameters should be set");
            return null;
        }

        if (viewId != null) builder.id(viewId);
        if (mode != null) builder.mode(mode);

        builder.templateParams(getTemplateParams(req));

        NodeView query = builder.build();

        if (!nodeViewService.hasNodeView(query)) {
            status.setCode(Status.STATUS_NOT_FOUND, "This view is not registered");
            return null;
        }

        NodeView view = nodeViewService.getNodeView(query);

        Map<String, Object> model = new HashMap<>();
        model.put(MODEL_VIEW, view);
        model.put(MODEL_CAN_BE_DRAFT, canBeDraft);
        model.put(MODEL_NODEREF, nodeRef != null ? nodeRef.toString() : null);
        return model;
    }

    private Map<String, Object> getTemplateParams(WebScriptRequest req) {
        Map<String, String> requestParams = WebScriptUtils.getParameterMap(req);
        Map<String, Object> templateParams = new HashMap<>(requestParams.size());
        for (Map.Entry<String, String> entry : requestParams.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(TEMPLATE_PARAM_PREFIX)) {
                templateParams.put(key.replaceFirst(TEMPLATE_PARAM_PREFIX, ""), entry.getValue());
            }
        }
        return templateParams;
    }

    private NodeRef getNodeRefByAttribute(NodeRef nodeRef, String attribute) {

        QName attrQName = QName.resolveToQName(namespaceService, attribute);
        Object value = nodeAttributeService.getAttribute(nodeRef, attrQName);

        if (value instanceof List) {
            List<?> attributeList = (List<?>) value;
            if (!attributeList.isEmpty()) {
                value = attributeList.get(0);
            }
        }

        return value instanceof NodeRef ? (NodeRef) value : null;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setNodeViewService(NodeViewService nodeViewService) {
        this.nodeViewService = nodeViewService;
    }

    public void setPrefixResolver(NamespacePrefixResolver prefixResolver) {
        this.prefixResolver = prefixResolver;
    }

    public void setNodeAttributeService(NodeAttributeService nodeAttributeService) {
        this.nodeAttributeService = nodeAttributeService;
    }

    public void setNamespaceService(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }
}
