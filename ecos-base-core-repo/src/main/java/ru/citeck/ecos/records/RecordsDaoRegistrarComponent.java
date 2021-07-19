package ru.citeck.ecos.records;

import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records3.RecordsDaoRegistrar;
import ru.citeck.ecos.records3.record.dao.RecordsDao;

import javax.annotation.PostConstruct;
import java.util.List;

@Component
public class RecordsDaoRegistrarComponent extends RecordsDaoRegistrar {

    @Autowired
    public RecordsDaoRegistrarComponent(RecordsService recordsService,
                                        ru.citeck.ecos.records3.RecordsService recordsServiceV1) {
        super(recordsServiceV1, recordsService);
    }

    @PostConstruct
    public void init() {
        super.register();
    }

    @Override
    @Autowired(required = false)
    public void setSources(@Nullable List<? extends RecordsDao> sources) {
        super.setSources(sources);
    }

    @Override
    @Autowired(required = false)
    public void setSourcesV0(@Nullable List<? extends ru.citeck.ecos.records2.source.dao.RecordsDao> sourcesV0) {
        super.setSourcesV0(sourcesV0);
    }
}
