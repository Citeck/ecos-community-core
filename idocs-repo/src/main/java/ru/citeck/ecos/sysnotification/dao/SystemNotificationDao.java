package ru.citeck.ecos.sysnotification.dao;

import ru.citeck.ecos.sysnotification.dto.SystemNotificationDto;

import java.util.List;

/**
 * @author Pavel Tkachenko
 */
public interface SystemNotificationDao {
    default List<SystemNotificationDto> get(int maxItems) {
        return get(maxItems, 0, true);
    }

    default List<SystemNotificationDto> get(int maxItems, int skipCount) {
        return get(maxItems, skipCount, true);
    }

    List<SystemNotificationDto> get(int maxItems, int skipCount, boolean onlyActive);
    SystemNotificationDto save(SystemNotificationDto systemNotificationDto);
    SystemNotificationDto delete(String id);
    long getTotalCount();
}
