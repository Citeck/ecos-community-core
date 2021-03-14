package ru.citeck.ecos.sysnotification.dao;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.sysnotification.dto.SystemNotificationDto;

import java.util.List;

/**
 * @author Pavel Tkachenko
 */
public interface SystemNotificationDao {
    @NotNull
    default List<SystemNotificationDto> get(int maxItems) {
        return get(maxItems, 0, true);
    }

    @NotNull
    default List<SystemNotificationDto> get(int maxItems, int skipCount) {
        return get(maxItems, skipCount, true);
    }

    @NotNull
    List<SystemNotificationDto> get(int maxItems, int skipCount, boolean onlyActive);

    @Nullable
    SystemNotificationDto get(@NotNull String id);

    @NotNull
    SystemNotificationDto save(@NotNull SystemNotificationDto dto);

    void delete(@NotNull String id);

    long getTotalCount();
}
