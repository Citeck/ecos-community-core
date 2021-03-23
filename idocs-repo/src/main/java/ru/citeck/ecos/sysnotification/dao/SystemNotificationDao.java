package ru.citeck.ecos.sysnotification.dao;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.sysnotification.dto.SystemNotificationDto;

/**
 * @author Pavel Tkachenko
 */
public interface SystemNotificationDao {
    @NotNull
    RecordsQueryResult<SystemNotificationDto> get(@NotNull RecordsQuery recordsQuery);

    @Nullable
    SystemNotificationDto get(@NotNull String id);

    @NotNull
    SystemNotificationDto save(@NotNull SystemNotificationDto dto);

    void delete(@NotNull String id);
}
