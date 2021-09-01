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
package ru.citeck.ecos.webscripts.history;

import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespacePrefixResolver;
import org.alfresco.service.namespace.QName;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;
import ru.citeck.ecos.webscripts.common.BaseAbstractWebscript;
import ru.citeck.ecos.history.HistoryService;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Anton Fateev <anton.fateev@citeck.ru>
 */
public class HistoryGet extends BaseAbstractWebscript {

    private static final String USER = "user";

    private static final String LIMIT_DATE = "limitDate";

    private static final String DOCUMENT = "document";

    private static final String WORKFLOW = "workflow";

    private static final String TASK = "task";

    // TODO refactor with AssociationIndexPropertyRegistry
    private static final String LINKED_POSTFIX = "_added";

    private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = ThreadLocal.withInitial(() ->
        new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"));

    private HistoryService historyService;

    private NodeService nodeService;

    private NamespacePrefixResolver namespaceService;

    public void setHistoryService(HistoryService historyService) {
        this.historyService = historyService;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setNamespaceService(NamespacePrefixResolver namespaceService) {
        this.namespaceService = namespaceService;
    }

    @Override
    protected void executeInternal(WebScriptRequest request, WebScriptResponse response) throws Exception {
        List<NodeRef> history = getHistoryEvents(request);
        try {
            response.setContentType(MimetypeMap.MIMETYPE_JSON);
            response.setContentEncoding("UTF-8");
            response.getWriter().write(serialize(history).toString());
        } catch (JSONException ex) {
            throw new WebScriptException("Unable to serialize JSON: " + ex.getMessage(), ex);
        }
    }

    private List<NodeRef> getHistoryEvents(WebScriptRequest request) {
        List<String> parameterNames = Arrays.asList(request.getParameterNames());
        if (parameterNames.isEmpty()) {
            throw new WebScriptException("No parameters specified");
        }
        if (parameterNames.contains(USER)) {
            String user = request.getParameter(USER);
            String limitDate = request.getParameter(LIMIT_DATE);
            if (limitDate == null) {
                return historyService.getEventsByInitiator(new NodeRef(user));
            } else {
                try {
                    return historyService.getEventsByInitiator(new NodeRef(user), DATE_FORMAT.get().parse(limitDate));
                } catch (ParseException ex) {
                    throw new WebScriptException("Unable to parse date: " + ex.getMessage(), ex);
                }
            }
        } else if (parameterNames.contains(DOCUMENT)) {
            String document = request.getParameter(DOCUMENT);
            return historyService.getEventsByDocument(new NodeRef(document));
        } else if (parameterNames.contains(WORKFLOW)) {
            String workflowInstance = request.getParameter(WORKFLOW);
            return historyService.getEventsByWorkflow(workflowInstance);
        } else if (parameterNames.contains(TASK)) {
            String taskInstance = request.getParameter(TASK);
            return historyService.getEventsByTask(taskInstance);
        } else {
            throw new WebScriptException("Unknown parameter name");
        }
    }

    private JSONObject serialize(List<NodeRef> history) throws JSONException {
        JSONObject json = new JSONObject();
        ArrayList<JSONObject> historyEvents = new ArrayList<>();
        for (NodeRef event : history) {
            historyEvents.add(serializeNode(event, true));
        }
        json.put("history", historyEvents);
        return json;
    }

    private JSONObject serializeNode(NodeRef nodeRef, boolean embedLinked) throws JSONException {
        JSONObject json = new JSONObject();
        if (nodeService.exists(nodeRef)) {
            Map<QName, Serializable> properties = nodeService.getProperties(nodeRef);
            for (Map.Entry<QName, Serializable> entry : properties.entrySet()) {
                String propertyName = entry.getKey().toPrefixString(namespaceService);
                Serializable propertyValue = entry.getValue();
                if (embedLinked && propertyName.contains(LINKED_POSTFIX)) {
                    ArrayList<?> propertyValueList = (ArrayList<?>) propertyValue;
                    NodeRef linkedNodeRef = (propertyValueList.get(0) instanceof NodeRef) ? (NodeRef) propertyValueList.get(0) : null;
                    JSONObject linkedNode = linkedNodeRef != null ? serializeNode(linkedNodeRef, false) : null;
                    json.put(propertyName.replace(":", "_"), linkedNode);
                } else {
                    json.put(propertyName.replace(":", "_"), propertyValue);
                }
            }
        }
        return json;
    }
}
