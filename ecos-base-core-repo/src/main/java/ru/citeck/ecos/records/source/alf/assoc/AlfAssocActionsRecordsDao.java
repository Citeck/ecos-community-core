package ru.citeck.ecos.records.source.alf.assoc;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.records.source.alf.assoc.dao.AlfAnyAssocDao;
import ru.citeck.ecos.records.source.alf.assoc.dao.AlfAssocDao;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.record.atts.dto.LocalRecordAtts;
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao;
import ru.citeck.ecos.records3.record.dao.mutate.RecordMutateDao;
import ru.citeck.ecos.utils.NodeUtils;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.*;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class AlfAssocActionsRecordsDao extends AbstractRecordsDao implements RecordMutateDao {

    private final DictionaryService dictionaryService;
    private final NamespaceService namespaceService;
    private final NodeUtils nodeUtils;

    private Map<QName, AlfAssocDao> assocsDao = Collections.emptyMap();

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

        AlfAssocDao alfAssocDao = needAlfAssocDao(assocQName);
        switch (actionDto.action) {
            case CREATE:
                alfAssocDao.create(sourceRef, targetRef, association);
                break;
            case REMOVE:
                alfAssocDao.remove(sourceRef, targetRef, association);
                break;
            default:
                throw new IllegalArgumentException("Unknown action type: " + actionDto.action);
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
        if (EntityRef.isEmpty(action.sourceRef)) {
            throw new RuntimeException("sourceRef is empty");
        }
        if (EntityRef.isEmpty(action.targetRef)) {
            throw new RuntimeException("targetRef is empty");
        }
        if (StringUtils.isBlank(action.association)) {
            throw new RuntimeException("association is empty");
        }
        return action;
    }

    private AlfAssocDao needAlfAssocDao(QName assocQName) {
        AlfAssocDao alfAssocDao = assocsDao.get(assocQName);
        if (alfAssocDao == null) {
            alfAssocDao = assocsDao.get(AlfAnyAssocDao.QNAME);
            if (alfAssocDao == null) {
                throw new IllegalStateException("Associations DAO can't be found" +
                    " for " + assocQName + ". Registered types: " + assocsDao.keySet());
            }
        }
        return alfAssocDao;
    }

    @NotNull
    @Override
    public String getId() {
        return "assoc-actions";
    }

    @Autowired
    public void setAssocsDao(List<AlfAssocDao> assocsDaoList) {

        List<AlfAssocDao> mutableList = new ArrayList<>(assocsDaoList);
        mutableList.sort((dao0, dao1) -> {
            // reversed order
            if (dao0.getOrder() > dao1.getOrder()) {
                return -1;
            } else if (dao0.getOrder() < dao1.getOrder()) {
                return 1;
            }
            return 0;
        });
        Map<QName, AlfAssocDao> daoMap = new HashMap<>();
        mutableList.forEach(dao ->
            dao.getQNames().forEach(qname -> daoMap.put(qname, dao))
        );
        this.assocsDao = daoMap;
    }

    @Data
    public static class ActionDto {
        private AlfAssocActionType action;
        private RecordRef sourceRef;
        private RecordRef targetRef;
        private String association;
    }
}
