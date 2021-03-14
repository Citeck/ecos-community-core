package ru.citeck.ecos.sysnotification.service.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.sysnotification.dao.SystemNotificationDao;
import ru.citeck.ecos.sysnotification.dto.SystemNotificationDto;
import ru.citeck.ecos.sysnotification.service.NoDaoException;
import ru.citeck.ecos.sysnotification.service.SystemNotificationService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Pavel Tkachenko
 */
@Service
public class SystemNotificationServiceImpl implements SystemNotificationService {
    private SystemNotificationDao systemNotificationDao;

    @NotNull
    @Override
    public List<SystemNotificationDto> get(int maxItems, int skipCount, boolean onlyActive) {
        return systemNotificationDao != null
            ? systemNotificationDao.get(maxItems, skipCount, onlyActive)
            : new ArrayList<>();
    }

    @Nullable
    @Override
    public SystemNotificationDto get(@NotNull String id) {
        return systemNotificationDao != null ? systemNotificationDao.get(id) : null;
    }

    @NotNull
    @Override
    public SystemNotificationDto save(@NotNull SystemNotificationDto dto) throws NoDaoException {
        if (systemNotificationDao == null) {
            throw new NoDaoException("You should implement SystemNotificationDao before using this method");
        }

        if (dto.getId() == null) {
            dto.setCreated(Instant.now());
        }

        dto.setModified(Instant.now());

        return systemNotificationDao.save(dto);
    }

    @Override
    public void delete(@NotNull String id) {
        if (systemNotificationDao != null) {
            systemNotificationDao.delete(id);
        }
    }

    @Override
    public long getTotalCount() {
        return systemNotificationDao != null ? systemNotificationDao.getTotalCount() : 0;
    }

    @Autowired(required = false)
    public void setSystemNotificationDao(SystemNotificationDao systemNotificationDao) {
        this.systemNotificationDao = systemNotificationDao;
    }
}
