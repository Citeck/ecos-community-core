package ru.citeck.ecos.records.type;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.model.lib.ModelServiceFactory;
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao;
import ru.citeck.ecos.model.lib.type.api.records.TypesMixin;

@Component
public class TypesMixinsInitializer {

    @Autowired
    public TypesMixinsInitializer(@Qualifier("remoteTypesSyncRecordsDao") AbstractRecordsDao typesRecordsDao,
                                  ModelServiceFactory modelServices) {
        typesRecordsDao.addAttributesMixin(new TypesMixin(modelServices));
    }

}
