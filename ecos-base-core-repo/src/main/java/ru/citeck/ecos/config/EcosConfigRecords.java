package ru.citeck.ecos.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.ArrayList;
import java.util.List;

@Component
public class EcosConfigRecords extends LocalRecordsDao implements LocalRecordsMetaDao<String> {

    public static final String ID = "ecos-config";

    private EcosConfigService ecosConfigService;

    public EcosConfigRecords() {
        setId(ID);
    }

    @Override
    public List<String> getLocalRecordsMeta(List<EntityRef> records, MetaField metaField) {

        List<String> result = new ArrayList<>();
        for (EntityRef ref : records) {
            Object value = ecosConfigService.getParamValue(ref.getLocalId());
            result.add(value == null ? null : value.toString());
        }
        return result;
    }

    @Autowired
    @Qualifier("ecosConfigService")
    public void setEcosConfigService(EcosConfigService ecosConfigService) {
        this.ecosConfigService = ecosConfigService;
    }
}
