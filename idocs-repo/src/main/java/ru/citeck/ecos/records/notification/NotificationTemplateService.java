package ru.citeck.ecos.records.notification;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.source.dao.local.RemoteSyncRecordsDao;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class NotificationTemplateService {

    private final RemoteSyncRecordsDao<TemplateModelDto> remoteSyncTemplateModelRecordsDao;

    @Autowired
    public NotificationTemplateService(@Qualifier("remoteSyncTemplateModelRecordsDao")
                                           RemoteSyncRecordsDao<TemplateModelDto> remoteSyncTemplateModelRecordsDao) {
        this.remoteSyncTemplateModelRecordsDao = remoteSyncTemplateModelRecordsDao;
    }

    public Map<String, String> getTemplateModel(RecordRef template) {
        Optional<TemplateModelDto> modelDto = remoteSyncTemplateModelRecordsDao.getRecord(template);
        if (!modelDto.isPresent() || modelDto.get().getModel() == null) {
            return Collections.emptyMap();
        }

        Map<String, String> model = new HashMap<>();
        modelDto.get().getModel().forEach((s, dataValue) -> model.put(s, dataValue.asText()));
        return model;
    }

}
