package ru.citeck.ecos.sysnotification.api.records;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.sysnotification.dto.SystemNotificationDto;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.request.error.RecordsError;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;
import ru.citeck.ecos.sysnotification.service.SystemNotificationService;

import java.util.List;

/**
 * @author Pavel Tkachenko
 */
@Component
public class SystemNotificationRecords extends LocalRecordsDao
    implements LocalRecordsQueryWithMetaDao<SystemNotificationDto> {

    private static final String ID = "system-notification";

    private SystemNotificationService systemNotificationService;

    public SystemNotificationRecords() {
        setId(ID);
    }

    @Override
    public RecordsQueryResult<SystemNotificationDto> queryLocalRecords(@NotNull RecordsQuery recordsQuery,
                                                                       @NotNull MetaField metaField) {

        RecordsQueryResult<SystemNotificationDto> result = new RecordsQueryResult<>();

        if (systemNotificationService != null) {
            int maxItems = recordsQuery.getMaxItems();
            int skipCount = recordsQuery.getSkipCount();
            List<SystemNotificationDto> notifications = systemNotificationService.get(maxItems, skipCount);
            long totalCount = systemNotificationService.getTotalCount();

            result.setRecords(notifications);
            result.setTotalCount(totalCount);
            result.setHasMore(totalCount > maxItems + skipCount);
        } else {
            result.addError(new RecordsError("SystemNotificationService is not implemented"));
        }

        return result;
    }

    @Autowired(required = false)
    public void setSystemNotificationService(SystemNotificationService systemNotificationService) {
        this.systemNotificationService = systemNotificationService;
    }
}
