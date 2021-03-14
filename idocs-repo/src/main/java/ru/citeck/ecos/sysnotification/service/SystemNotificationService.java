package ru.citeck.ecos.sysnotification.service;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.sysnotification.dto.SystemNotificationDto;

import java.util.List;

/**
 * @author Pavel Tkachenko
 */
public interface SystemNotificationService {
    @NotNull
    default List<SystemNotificationDto> get(int maxItems) {
        return get(maxItems, 0, false);
    }

    @NotNull
    default List<SystemNotificationDto> get(int maxItems, int skipCount) {
        return get(maxItems, skipCount, false);
    }

    @NotNull
    List<SystemNotificationDto> get(int maxItems, int skipCount, boolean onlyActive);

    @Nullable
    SystemNotificationDto get(@NotNull String id);

    @NotNull
    SystemNotificationDto save(@NotNull SystemNotificationDto dto) throws NoDaoException;

    void delete(@NotNull String id);

    long getTotalCount();
}
