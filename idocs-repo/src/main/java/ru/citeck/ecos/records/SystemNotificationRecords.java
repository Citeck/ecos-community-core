package ru.citeck.ecos.records;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.dto.SystemNotificationDto;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.request.error.RecordsError;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;
import ru.citeck.ecos.service.SystemNotificationService;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * @author Pavel Tkachenko
 */
@Component
public class SystemNotificationRecords extends LocalRecordsDao
    implements LocalRecordsQueryWithMetaDao<SystemNotificationDto> {

    private static final String ID = "system-notification";

    private ApplicationContext context;
    private SystemNotificationService systemNotificationService;

    public SystemNotificationRecords() {
        setId(ID);
    }

    @PostConstruct
    public void init() {
        if (context.containsBean("systemNotificationService")) {
            this.systemNotificationService = context.getBean(SystemNotificationService.class);
        }
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

    @Autowired
    public void setApplicationContext(ApplicationContext context) {
        this.context = context;
    }
}
