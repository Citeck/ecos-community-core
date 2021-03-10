package ru.citeck.ecos.sysnotification.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.sysnotification.dao.SystemNotificationDao;
import ru.citeck.ecos.sysnotification.dto.SystemNotificationDto;
import ru.citeck.ecos.sysnotification.service.NoDaoException;
import ru.citeck.ecos.sysnotification.service.SystemNotificationService;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Pavel Tkachenko
 */
@Service
public class SystemNotificationServiceImpl implements SystemNotificationService {
    private SystemNotificationDao systemNotificationDao;

    @Override
    public List<SystemNotificationDto> get(int maxItems, int skipCount, boolean onlyActive) throws NoDaoException {
        return systemNotificationDao != null
            ? systemNotificationDao.get(maxItems, skipCount, onlyActive)
            : new ArrayList<>();
    }

    @Override
    public SystemNotificationDto get(String id) throws NoDaoException {
        return systemNotificationDao != null ? systemNotificationDao.get(id) : null;
    }

    @Override
    public SystemNotificationDto save(SystemNotificationDto systemNotificationDto) throws NoDaoException {
        if (systemNotificationDao == null) {
            throw new NoDaoException("You should implement SystemNotificationDao before using this method");
        }

        return systemNotificationDao.save(systemNotificationDto);
    }

    @Override
    public void delete(String id) throws NoDaoException {
        if (systemNotificationDao != null) {
            systemNotificationDao.delete(id);
        }
    }

    @Override
    public long getTotalCount() throws NoDaoException {
        return systemNotificationDao != null ? systemNotificationDao.getTotalCount() : 0;
    }

    @Autowired(required = false)
    public void setSystemNotificationDao(SystemNotificationDao systemNotificationDao) {
        this.systemNotificationDao = systemNotificationDao;
    }
}
