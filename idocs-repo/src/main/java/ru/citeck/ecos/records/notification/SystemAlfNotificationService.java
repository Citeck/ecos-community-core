package ru.citeck.ecos.records.notification;

import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.notifications.lib.Notification;
import ru.citeck.ecos.notifications.lib.service.NotificationService;

@Service("systemAlfNotificationService")
public class SystemAlfNotificationService implements NotificationService {

    private final NotificationService notificationService;

    @Autowired
    public SystemAlfNotificationService(@Qualifier("ecosNotificationService") NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void send(Notification notification) {
        AuthenticationUtil.runAsSystem(() -> {
            notificationService.send(notification);
            return null;
        });
    }

}
