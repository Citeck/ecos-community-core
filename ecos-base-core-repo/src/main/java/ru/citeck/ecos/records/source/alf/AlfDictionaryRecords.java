package ru.citeck.ecos.records.source.alf;

import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records.source.alf.meta.AlfNodeRecord;
import ru.citeck.ecos.records.source.alf.meta.DictRecord;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.mutation.RecordsMutation;
import ru.citeck.ecos.records2.source.dao.MutableRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AlfDictionaryRecords extends LocalRecordsDao
                                  implements LocalRecordsMetaDao<MetaValue>,
                                             MutableRecordsDao {

    public static final String ID = "dict";

    private final AlfNodesRecordsDAO alfNodesRecordsDao;
    private NamespaceService namespaceService;

    @Autowired
    public AlfDictionaryRecords(AlfNodesRecordsDAO alfNodesRecordsDao) {
        setId(ID);
        this.alfNodesRecordsDao = alfNodesRecordsDao;
    }

    @Override
    public List<MetaValue> getLocalRecordsMeta(List<EntityRef> records, MetaField metaField) {

        return records.stream().map(r -> {
            QName typeName = QName.resolveToQName(namespaceService, r.getLocalId());
            return new DictRecord(typeName, r.getLocalId(), "alf_" + r.getLocalId());

        }).collect(Collectors.toList());
    }

    @Override
    public RecordsMutResult mutateImpl(RecordsMutation mutation) {

        RecordsMutation alfNodesMut = new RecordsMutation(mutation, m -> {
            RecordMeta alfNodeMeta = new RecordMeta(m, id -> RecordRef.EMPTY);
            alfNodeMeta.setAttribute(AlfNodeRecord.ATTR_TYPE, m.getId().getLocalId());
            return alfNodeMeta;
        });
        return alfNodesRecordsDao.mutate(alfNodesMut);
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion deletion) {
        return new RecordsDelResult();
    }

    @Autowired
    public void setNamespaceService(NamespaceService namespaceService) {
        this.namespaceService = namespaceService;
    }
}
