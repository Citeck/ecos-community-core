package ru.citeck.ecos.records.source.common;

import lombok.NonNull;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.lock.LockService;
import org.alfresco.service.cmr.lock.LockStatus;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records.source.alf.AlfNodesRecordsDAO;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.source.common.AttributesMixin;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;

@Component
public class IsLockedMixin implements AttributesMixin<Class<RecordRef>, RecordRef> {

    private final AlfNodesRecordsDAO alfNodesRecordsDao;
    private final NodeService nodeService;
    private final LockService lockService;

    @Autowired
    public IsLockedMixin(AlfNodesRecordsDAO alfNodesRecordsDao, NodeService nodeService, LockService lockService) {
        this.alfNodesRecordsDao = alfNodesRecordsDao;
        this.nodeService = nodeService;
        this.lockService = lockService;
    }

    @PostConstruct
    public void setup() {
        alfNodesRecordsDao.addAttributesMixin(this);
    }

    @Override
    public List<String> getAttributesList() {
        return Collections.singletonList("isLocked");
    }

    @Override
    public Object getAttribute(String attribute, RecordRef meta, MetaField field) {
        NodeRef documentNodeRef = new NodeRef(meta.getId());
        return documentIsLocked(documentNodeRef);
    }

    @Override
    public Class<RecordRef> getMetaToRequest() {
        return RecordRef.class;
    }

    private boolean documentIsLocked(@NonNull NodeRef document) {
        if (nodeService.hasAspect(document, ContentModel.ASPECT_LOCKABLE)) {
            LockStatus status = lockService.getLockStatus(document);
            return status == LockStatus.LOCKED || status == LockStatus.LOCK_OWNER;
        }
        return false;
    }
}
