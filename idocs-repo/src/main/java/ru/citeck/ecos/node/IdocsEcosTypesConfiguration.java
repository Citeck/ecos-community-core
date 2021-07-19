package ru.citeck.ecos.node;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.model.IdocsModel;
import ru.citeck.ecos.records.source.alf.meta.AlfNodeRecord;
import ru.citeck.ecos.records2.RecordRef;

import javax.annotation.PostConstruct;

@Configuration
public class IdocsEcosTypesConfiguration {

    @Autowired
    private EcosTypeService ecosTypeService;

    @PostConstruct
    public void init() {
        ecosTypeService.register(IdocsModel.TYPE_CONTRACTOR, (info) -> getType( "idocs-contractor"));

        AlfNodeRecord.addAttAsRecord("dms:ecosType");
        AlfNodeRecord.addAttAsRecord("dms:ecosNotificationTemplate");
    }

    private RecordRef getType(String typeId) {
        return RecordRef.create("emodel", "type", typeId);
    }
}
