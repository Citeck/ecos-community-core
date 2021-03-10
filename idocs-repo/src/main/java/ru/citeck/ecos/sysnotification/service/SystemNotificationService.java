package ru.citeck.ecos.sysnotification.service;

import ru.citeck.ecos.sysnotification.dto.SystemNotificationDto;

import java.util.List;

/**
 * @author Pavel Tkachenko
 */
public interface SystemNotificationService {
    default List<SystemNotificationDto> get(int maxItems) throws NoDaoException {
        return get(maxItems, 0, true);
    }

    default List<SystemNotificationDto> get(int maxItems, int skipCount) throws NoDaoException {
        return get(maxItems, skipCount, true);
    }

    List<SystemNotificationDto> get(int maxItems, int skipCount, boolean onlyActive) throws NoDaoException;
    SystemNotificationDto get(String id) throws NoDaoException;
    SystemNotificationDto save(SystemNotificationDto systemNotificationDto) throws NoDaoException;
    SystemNotificationDto delete(String id) throws NoDaoException;
    long getTotalCount() throws NoDaoException;
}
