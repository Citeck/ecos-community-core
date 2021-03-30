package ru.citeck.ecos.sysnotification.api.records;

import ecos.com.fasterxml.jackson210.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.records2.QueryContext;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
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

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Pavel Tkachenko
 */
@Component
public class SystemNotificationRecordsDao extends LocalRecordsDao
    implements LocalRecordsQueryWithMetaDao<SystemNotificationDto>,
    MutableRecordsLocalDao<SystemNotificationRecordsDao.SystemNotificationRecord>,
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
    public List<SystemNotificationRecord> getValuesToMutate(@NotNull List<RecordRef> recordRefs) {
        List<SystemNotificationRecord> result = new ArrayList<>();

        for (RecordRef recordRef: recordRefs) {
            String id = recordRef.getId();
            SystemNotificationRecord record = StringUtils.isBlank(id)
                ? new SystemNotificationRecord()
                : new SystemNotificationRecord(systemNotificationService.get(id));
            result.add(record);
        }

        return result;
    }

    @NotNull
    @Override
    public RecordsMutResult save(@NotNull List<SystemNotificationRecord> records) {
        RecordsMutResult result = new RecordsMutResult();

        for (SystemNotificationRecord record: records) {
            if (record.isUseCountdown()) {
                long ss = record.getTimeBeforeEventInSeconds();
                long mm = record.getTimeBeforeEventInMinutes();
                long hh = record.getTimeBeforeEventInHours();
                Instant time = Instant.now().plus(ss, ChronoUnit.SECONDS)
                    .plus(mm, ChronoUnit.MINUTES).plus(hh, ChronoUnit.HOURS);
                record.setTime(time);
            }

            SystemNotificationDto savedDto = systemNotificationService.save(record);
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

    @Getter
    @Setter
    public class SystemNotificationRecord extends SystemNotificationDto {
        private long timeBeforeEventInSeconds;
        private long timeBeforeEventInMinutes;
        private long timeBeforeEventInHours;
        private boolean useCountdown;

        public SystemNotificationRecord() {}

        public SystemNotificationRecord(SystemNotificationDto dto) {
            this.id = dto.getId();
            this.message = dto.getMessage();
            this.time = Instant.from(dto.getTime());
            this.created = Instant.from(dto.getCreated());
            this.modified = Instant.from(dto.getModified());
        }

        @MetaAtt(".disp")
        public String getDisplayName() {
            String name = MLText.getClosestValue(getMessage(), QueryContext.getCurrent().getLocale());
            return org.apache.commons.lang.StringUtils.isNotBlank(name) ? name : "System notification";
        }

        @JsonIgnore
        public String get_formKey() {
            return "system-notification";
        }
    }
}