package ru.citeck.ecos.sysnotification.service;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.sysnotification.dto.SystemNotificationDto;

/**
 * @author Pavel Tkachenko
 */
public interface SystemNotificationService {
    @NotNull
    RecordsQueryResult<SystemNotificationDto> get(@NotNull RecordsQuery recordsQuery);

    @Nullable
    SystemNotificationDto get(@NotNull String id);

    @NotNull
    SystemNotificationDto save(@NotNull SystemNotificationDto dto) throws NoDaoException;

    void delete(@NotNull String id);
}
