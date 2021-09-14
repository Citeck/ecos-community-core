package ru.citeck.ecos.flowable.email;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.apache.commons.lang3.StringUtils;
import org.flowable.bpmn.model.FieldExtension;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.TaskWithFieldExtensions;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.bpmn.behavior.email.FlowableEmailSender;
import org.flowable.engine.impl.bpmn.behavior.email.SendEmailDto;
import org.flowable.engine.impl.util.lib.FlowableLibUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.notifications.lib.Notification;
import ru.citeck.ecos.notifications.lib.NotificationType;
import ru.citeck.ecos.notifications.lib.service.NotificationService;
import ru.citeck.ecos.records2.RecordRef;

import java.util.*;

@Slf4j
@Component
public class FlowableEmailSenderImpl implements FlowableEmailSender {

    private static final String PROCESS_VARIABLE = "process";
    private static final String EVENT_INITIATOR = "eventInitiator";
    private static final String FORCE_STR_PREFIX = "!str_";
    private static final String APP_ALFRESCO = "alfresco";

    private final NotificationService notificationService;
    private FlowableStdEmailSender flowableStdEmailSender;

    @Autowired
    public FlowableEmailSenderImpl(@Qualifier("ecosNotificationService") NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void sendEmail(SendEmailDto emailDto, DelegateExecution execution) {

        if (StringUtils.isBlank(emailDto.getTo())
                && StringUtils.isBlank(emailDto.getCc())
                && StringUtils.isBlank(emailDto.getBcc())) {

            throw new RuntimeException("Email recipients is missing. " + getMailExecutionInfo(execution));
        }
        try {
            sendEmailImpl(emailDto, execution);
        } catch (Exception e) {
            log.error("Exception while email sending. " + getMailExecutionInfo(execution));
            throw e;
        }
    }

    private String getMailExecutionInfo(DelegateExecution execution) {
        if (execution == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder();
        FlowElement flowElement = execution.getCurrentFlowElement();
        if (flowElement != null) {
            sb.append("Name: ").append(flowElement.getName()).append(" ");
            if (flowElement instanceof TaskWithFieldExtensions) {
                TaskWithFieldExtensions taskWithFieldExtensions = (TaskWithFieldExtensions) flowElement;
                List<FieldExtension> fields = taskWithFieldExtensions.getFieldExtensions();
                if (fields != null) {
                    for (FieldExtension field : fields) {
                        if ("to".equals(field.getFieldName())) {
                            if (StringUtils.isNotBlank(field.getStringValue())) {
                                sb.append("to: ").append(field.getStringValue()).append(" ");
                            }
                            if (StringUtils.isNotBlank(field.getExpression())) {
                                sb.append("to: ").append(field.getExpression()).append(" ");
                            }
                            break;
                        }
                    }
                }
            }
            sb.append("id: ").append(flowElement.getId());
        }
        return sb.toString();
    }

    private void sendEmailImpl(SendEmailDto emailDto, DelegateExecution execution) {

        if (StringUtils.isBlank(emailDto.getTemplate())) {
            flowableStdEmailSender.sendEmail(emailDto, execution);
            return;
        }

        String notificationTypeStr = emailDto.getNotificationType();
        NotificationType notificationType = StringUtils.isNotBlank(notificationTypeStr)
            ? NotificationType.valueOf(notificationTypeStr.toUpperCase())
            : NotificationType.EMAIL_NOTIFICATION;

        String toStr = emailDto.getTo();
        List<String> to = StringUtils.isNotBlank(toStr)
            ? Arrays.asList(FlowableEmailSenderUtils.splitAndTrim(toStr))
            : Collections.emptyList();

        String ccStr = emailDto.getCc();
        List<String> cc = StringUtils.isNotBlank(ccStr)
            ? Arrays.asList(FlowableEmailSenderUtils.splitAndTrim(ccStr))
            : Collections.emptyList();

        String bccStr = emailDto.getBcc();
        List<String> bcc = StringUtils.isNotBlank(bccStr)
            ? Arrays.asList(FlowableEmailSenderUtils.splitAndTrim(bccStr))
            : Collections.emptyList();

        String fromStr = emailDto.getFrom();

        Map<String, Object> stringObjectMap = Json.getMapper().readMap(
            emailDto.getAdditionalMeta(),
            String.class,
            Object.class
        );

        Map<String, Object> processVariables = execution.getVariables();
        processVariables.put(EVENT_INITIATOR, getRunAsUser());

        Map<String, Object> mp = new HashMap<>();
        mp.put(PROCESS_VARIABLE, processVariables);

        stringObjectMap.forEach((k, v) -> {
            if (v instanceof String) {
                String str = (String) v;
                if (StringUtils.startsWith(str, FORCE_STR_PREFIX)) {
                    mp.put(k, StringUtils.replace(str, FORCE_STR_PREFIX, ""));
                } else {
                    mp.put(k, RecordRef.valueOf(str));
                }
            } else {
                mp.put(k, v);
            }
        });

        String langStr = emailDto.getLang();
        RecordRef record = resolveCompletedDocumentRecord(emailDto);

        Notification notification = new Notification.Builder()
            .record(record)
            .templateRef(RecordRef.valueOf(emailDto.getTemplate()))
            .notificationType(notificationType)
            .recipients(to)
            .cc(cc)
            .bcc(bcc)
            .lang(langStr)
            .from(fromStr)
            .additionalMeta(mp)
            .build();

        notificationService.send(notification);
    }

    protected RecordRef getRunAsUser() {
        if (!FlowableLibUtils.isAuthenticationUtilPresent()) {
            return RecordRef.EMPTY;
        }

        String runAsUserName = AuthenticationUtil.getRunAsUser();
        return RecordRef.valueOf("people@" + runAsUserName);
    }

    private RecordRef resolveCompletedDocumentRecord(SendEmailDto emailDto) {
        String record = emailDto.getRecord();
        if (StringUtils.isBlank(record)) {
            return RecordRef.EMPTY;
        }

        RecordRef rawRef = RecordRef.valueOf(record);
        if (StringUtils.isNotBlank(rawRef.getAppName())) {
            return rawRef;
        }

        return RecordRef.create(APP_ALFRESCO, rawRef.getSourceId(), rawRef.getId());
    }

    public void setFlowableStdEmailSender(FlowableStdEmailSender flowableStdEmailSender) {
        this.flowableStdEmailSender = flowableStdEmailSender;
    }

    public int getVersion() {
        return 0;
    }
}
