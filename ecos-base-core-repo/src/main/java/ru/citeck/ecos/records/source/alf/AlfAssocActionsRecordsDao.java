package ru.citeck.ecos.records.source.alf;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts;
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao;
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDao;
import ru.citeck.ecos.utils.NodeUtils;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class AlfAssocActionsRecordsDao extends AbstractRecordsDao implements RecordMutateDao {

    public enum ActionType { CREATE, REMOVE }

    private final NodeUtils nodeUtils;
    private final DictionaryService dictionaryService;
    private final NamespaceService namespaceService;
    private final NodeService nodeService;

    @NotNull
    @Override
    public String mutate(@NotNull LocalRecordAtts localRecordAtts) {

        ActionDto actionDto = getAction(localRecordAtts.getAttributes());
        QName assocQName = QName.resolveToQName(namespaceService, actionDto.association);
        AssociationDefinition association = dictionaryService.getAssociation(assocQName);
        if (association == null) {
            throw new RuntimeException("Association is not found in dictionary");
        }
        NodeRef sourceRef = nodeUtils.getNodeRef(actionDto.getSourceRef());
        NodeRef targetRef = nodeUtils.getNodeRef(actionDto.getTargetRef());

        if (association.isChild()) {

            // We should get child assocs using getParentAssocs because parent node
            // may contain a lot of children and nodeService.getChildAssocs will be very slow
            List<ChildAssociationRef> currentAssocs =
                nodeService.getParentAssocs(targetRef, assocQName, RegexQNamePattern.MATCH_ALL)
                    .stream()
                    .filter(assoc -> sourceRef.equals(assoc.getParentRef()))
                    .collect(Collectors.toList());

            if (currentAssocs.isEmpty() && ActionType.CREATE.equals(actionDto.action)) {
                QName qName = QName.createQName(assocQName.getNamespaceURI(), UUID.randomUUID().toString());
                nodeService.addChild(sourceRef, targetRef, assocQName, qName);
            }
            if (!currentAssocs.isEmpty() && ActionType.REMOVE.equals(actionDto.action)) {
                nodeService.removeSecondaryChildAssociation(currentAssocs.get(0));
            }
        } else {
            if (ActionType.CREATE.equals(actionDto.action)) {
                nodeUtils.createAssoc(sourceRef, targetRef, assocQName);
            } else if (ActionType.REMOVE.equals(actionDto.action)) {
                nodeUtils.removeAssoc(sourceRef, targetRef, assocQName);
            }
        }
        return localRecordAtts.getId();
    }

    @NotNull
    private ActionDto getAction(ObjectData actionData) {
        ActionDto action = actionData.getAs(ActionDto.class);
        if (action == null) {
            throw new RuntimeException("ActionDto is null");
        }
        if (action.action == null) {
            throw new RuntimeException("Action is null");
        }
        if (RecordRef.isEmpty(action.sourceRef)) {
            throw new RuntimeException("sourceRef is empty");
        }
        if (RecordRef.isEmpty(action.targetRef)) {
            throw new RuntimeException("targetRef is empty");
        }
        if (StringUtils.isBlank(action.association)) {
            throw new RuntimeException("association is empty");
        }
        return action;
    }

    @NotNull
    @Override
    public String getId() {
        return "assoc-actions";
    }

    @Data
    public static class ActionDto {
        private ActionType action;
        private RecordRef sourceRef;
        private RecordRef targetRef;
        private String association;
    }
}
