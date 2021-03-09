package ru.citeck.ecos.sysnotification.service;

import ru.citeck.ecos.sysnotification.dto.SystemNotificationDto;

import java.util.List;

/**
 * @author Pavel Tkachenko
 */
public interface SystemNotificationService {
    default List<SystemNotificationDto> get(int maxItems) {
        return get(maxItems, 0, true);
    }

    default List<SystemNotificationDto> get(int maxItems, int skipCount) {
        return get(maxItems, skipCount, true);
    }

    List<SystemNotificationDto> get(int maxItems, int skipCount, boolean onlyActive);
    long getTotalCount();
}
