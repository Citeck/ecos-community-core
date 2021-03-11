package ru.citeck.ecos.sysnotification.api.records;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.MutableRecordsLocalDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;
import ru.citeck.ecos.sysnotification.dto.SystemNotificationDto;
import ru.citeck.ecos.sysnotification.service.SystemNotificationService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Pavel Tkachenko
 */
@Component
public class SystemNotificationRecordsDao extends LocalRecordsDao
    implements LocalRecordsQueryWithMetaDao<SystemNotificationDto>,
    MutableRecordsLocalDao<SystemNotificationDto>,
    LocalRecordsMetaDao<SystemNotificationDto> {

    private static final String ID = "system-notification";

    private SystemNotificationService systemNotificationService;

    public SystemNotificationRecordsDao() {
        setId(ID);
    }

    @Override
    public RecordsQueryResult<SystemNotificationDto> queryLocalRecords(@NotNull RecordsQuery recordsQuery,
                                                                       @NotNull MetaField metaField) {

        RecordsQueryResult<SystemNotificationDto> result = new RecordsQueryResult<>();

        int maxItems = recordsQuery.getMaxItems();
        int skipCount = recordsQuery.getSkipCount();
        List<SystemNotificationDto> notifications = systemNotificationService.get(maxItems, skipCount);
        long totalCount = systemNotificationService.getTotalCount();

        result.setRecords(notifications);
        result.setTotalCount(totalCount);
        result.setHasMore((maxItems >= 0) && (totalCount > maxItems + skipCount));

        return result;
    }

    @NotNull
    @Override
    public List<SystemNotificationDto> getValuesToMutate(@NotNull List<RecordRef> recordRefs) {
        List<SystemNotificationDto> result = new ArrayList<>();

        for (RecordRef recordRef: recordRefs) {
            String id = recordRef.getId();
            result.add(StringUtils.isBlank(id) ? new SystemNotificationDto() : systemNotificationService.get(id));
        }

        return result;
    }

    @NotNull
    @Override
    public RecordsMutResult save(@NotNull List<SystemNotificationDto> dtos) {
        RecordsMutResult result = new RecordsMutResult();

        for (SystemNotificationDto dto: dtos) {
            SystemNotificationDto savedDto = systemNotificationService.save(dto);
            result.addRecord(new RecordMeta(savedDto.getId()));
        }

        return result;
    }

    @Override
    public RecordsDelResult delete(@NotNull RecordsDeletion recordsDeletion) {
        RecordsDelResult result = new RecordsDelResult();

        for (RecordRef recordRef: recordsDeletion.getRecords()) {
            systemNotificationService.delete(recordRef.getId());
            result.addRecord(new RecordMeta(recordRef));
        }

        return result;
    }

    @Override
    public List<SystemNotificationDto> getLocalRecordsMeta(@NotNull List<RecordRef> list,
                                                           @NotNull MetaField metaField) {
        return list.stream().map(r -> systemNotificationService.get(r.getId())).collect(Collectors.toList());
    }

    @Autowired
    public void setSystemNotificationService(SystemNotificationService systemNotificationService) {
        this.systemNotificationService = systemNotificationService;
    }
}
