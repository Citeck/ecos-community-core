package ru.citeck.ecos.action.group.output;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.notifications.lib.Notification;
import ru.citeck.ecos.notifications.lib.NotificationType;
import ru.citeck.ecos.records.notification.SystemAlfNotificationService;
import ru.citeck.ecos.records.source.PeopleRecordsDao;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.utils.RepoUtils;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.Collections;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class ExportOutputEmailAction implements ExportOutputAction<ExportOutputEmailAction.Config> {

    public static final String TYPE = "email";
    private static final RecordRef DEFAULT_TEMPLATE_REF = RecordRef.create(
        "notifications", "template", "default-report-email-notification");

    private final SystemAlfNotificationService systemAlfNotificationService;
    private final RecordsService recordsService;

    @Override
    public void execute(NodeRef outputFile, Config config) {

        RecordRef templateRef = config.templateRef;
        if (EntityRef.isEmpty(templateRef)) {
            templateRef = DEFAULT_TEMPLATE_REF;
        }

        String downloadURL = RepoUtils.getDownloadURL(outputFile);

        String email = getEmail();
        Notification notification = new Notification.Builder()
            .templateRef(templateRef)
            .notificationType(NotificationType.EMAIL_NOTIFICATION)
            .recipients(Collections.singleton(email))
            .addToAdditionalMeta("url", downloadURL)
            .build();
        systemAlfNotificationService.send(notification);
    }

    @Override
    public void validate(Config config) {
        String email = getEmail();
        if (StringUtils.isBlank(email)) {
            throw new RuntimeException("Email not found by " + email);
        }
    }

    private String getEmail() {
        String userName = AuthenticationUtil.getFullyAuthenticatedUser();
        return recordsService.getAtt(RecordRef.create(PeopleRecordsDao.ID, userName), "cm:email").asText();
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Data
    public static class Config {
        private RecordRef templateRef;
    }
}
