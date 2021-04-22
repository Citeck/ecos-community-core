package ru.citeck.ecos.history.impl;

import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import ru.citeck.ecos.constants.DocumentHistoryConstants;
import ru.citeck.ecos.model.HistoryModel;

import java.io.Serializable;
import java.util.*;

/**
 * History get service
 */
public class HistoryGetServiceImpl implements HistoryGetService {

    private NodeService nodeService;

    /**
     * Get history events by document refs
     *
     * @param documentNodeRef Document node reference
     * @return List of transformed events (for web script)
     */
    @SuppressWarnings("unchecked")
    public List<Map> getHistoryEventsByDocumentRef(NodeRef documentNodeRef) {
        /* Loaf associations */
        List<AssociationRef> associations = nodeService.getSourceAssocs(documentNodeRef, HistoryModel.ASSOC_DOCUMENT);
        List<Map> result = new ArrayList<>(associations.size());
        /* Create entries */
        for (AssociationRef associationRef : associations) {

            Map<String, Object> entryMap = new HashMap<>();
            NodeRef eventRef = associationRef.getSourceRef();
            Map<QName, Serializable> eventProps = nodeService.getProperties(eventRef);

            entryMap.put(DocumentHistoryConstants.NODE_REF.getValue(), eventRef.getId());
            entryMap.put(DocumentHistoryConstants.EVENT_TYPE.getValue(), eventProps.get(HistoryModel.PROP_NAME));

            entryMap.put(DocumentHistoryConstants.DOCUMENT_VERSION.getValue(),
                    eventProps.get(HistoryModel.PROP_DOCUMENT_VERSION));
            entryMap.put(DocumentHistoryConstants.COMMENTS.getValue(),
                    eventProps.get(HistoryModel.PROP_TASK_COMMENT));
            entryMap.put(DocumentHistoryConstants.DOCUMENT_DATE.getValue(),
                    ((Date) eventProps.get(HistoryModel.PROP_DATE)).getTime());
            entryMap.put(DocumentHistoryConstants.TASK_ROLE.getValue(),
                    eventProps.get(HistoryModel.PROP_TASK_ROLE));
            entryMap.put(DocumentHistoryConstants.TASK_OUTCOME.getValue(),
                eventProps.get(HistoryModel.PROP_TASK_OUTCOME));
            entryMap.put(DocumentHistoryConstants.TASK_DEFINITION_KEY.getValue(),
                eventProps.get(HistoryModel.PROP_TASK_DEFINITION_KEY));
            entryMap.put(DocumentHistoryConstants.TASK_INSTANCE_ID.getValue(),
                    eventProps.get(HistoryModel.PROP_TASK_INSTANCE_ID));
            entryMap.put(DocumentHistoryConstants.TASK_TITLE.getValue(),
                    eventProps.get(HistoryModel.PROP_TASK_TITLE));

            entryMap.put(DocumentHistoryConstants.EVENT_INITIATOR.getValue(), getInitiator(eventRef, eventProps));

            QName taskTypeValue = (QName) eventProps.get(HistoryModel.PROP_TASK_TYPE);
            String taskType = taskTypeValue != null ? taskTypeValue.toString() : "";

            entryMap.put(DocumentHistoryConstants.TASK_TYPE.getValue(), taskType);

            ArrayList<NodeRef> attachments = (ArrayList<NodeRef>) eventProps.get(HistoryModel.PROP_TASK_ATTACHMENTS);
            entryMap.put(DocumentHistoryConstants.TASK_ATTACHMENTS.getValue(),
                    Optional.ofNullable(attachments).orElse(new ArrayList<>()));

            ArrayList<NodeRef> pooledActors = (ArrayList<NodeRef>) eventProps.get(HistoryModel.PROP_TASK_POOLED_ACTORS);
            entryMap.put(DocumentHistoryConstants.TASK_POOLED_ACTORS.getValue(),
                    Optional.ofNullable(pooledActors).orElse(new ArrayList<>()));

            result.add(entryMap);
        }
        result.sort((firstMap, secondMap) -> {
            Long firstDate = (Long) firstMap.get(DocumentHistoryConstants.DOCUMENT_DATE.getValue());
            Long secondDate = (Long) secondMap.get(DocumentHistoryConstants.DOCUMENT_DATE.getValue());
            return firstDate.compareTo(secondDate);
        });
        return result;
    }

    private String getInitiator(NodeRef nodeRef, Map<QName, Serializable> props) {
        List<AssociationRef> initiatorAssoc = nodeService.getTargetAssocs(nodeRef, HistoryModel.ASSOC_INITIATOR);
        if (initiatorAssoc != null && !initiatorAssoc.isEmpty()) {
            return initiatorAssoc.get(0).getTargetRef().toString();
        }
        return (String) props.get(HistoryModel.MODIFIER_PROPERTY);
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }
}
