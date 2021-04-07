package ru.citeck.ecos.flowable.email;

import lombok.SneakyThrows;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.mail.*;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableIllegalArgumentException;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.cfg.MailServerInfo;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.impl.bpmn.behavior.email.SendEmailDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import javax.activation.DataSource;
import javax.naming.NamingException;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Component
public class FlowableStdEmailSender {

    private static final String NEWLINE_REGEX = "\\r?\\n";
    private static final String FLOWABLE_MAIL_FROM_FORCE = "flowable.mail.from.fixed";

    private final ProcessEngineConfiguration processEngineConfiguration;
    private final Properties properties;

    @Autowired
    public FlowableStdEmailSender(ProcessEngineConfiguration processEngineConfiguration,
                                  FlowableEmailSenderImpl flowableEmailSender,
                                  @Qualifier("global-properties") Properties properties) {

        this.processEngineConfiguration = processEngineConfiguration;
        this.properties = properties;

        flowableEmailSender.setFlowableStdEmailSender(this);
    }

    @SneakyThrows
    public void sendEmail(SendEmailDto emailDto, DelegateExecution execution){

        String fixedFrom = properties.getProperty(FLOWABLE_MAIL_FROM_FORCE, "");
        if (StringUtils.isNotBlank(fixedFrom)) {
            emailDto.setFrom(fixedFrom);
        }

        Email email = createEmail(
            emailDto.getText(),
            emailDto.getHtml(),
            attachmentsExist(emailDto.getFiles(), emailDto.getDataSources())
        );

        addHeader(email, emailDto.getHeaders());
        addTo(email, emailDto.getTo(), execution.getTenantId());
        setFrom(email, emailDto.getFrom(), execution.getTenantId());
        addCc(email, emailDto.getCc(), execution.getTenantId());
        addBcc(email, emailDto.getBcc(), execution.getTenantId());
        setSubject(email, emailDto.getSubject());
        setMailServerProperties(email, execution.getTenantId());
        setCharset(email, emailDto.getCharset());
        attach(email, emailDto.getFiles(), emailDto.getDataSources());

        email.send();
    }

    protected void setCharset(Email email, String charSetStr) {
        if (StringUtils.isNotBlank(charSetStr)) {
            email.setCharset(charSetStr);
        }
    }

    protected void setMailServerProperties(Email email, String tenantId) {

        boolean isMailServerSet = false;
        if (tenantId != null && tenantId.length() > 0) {
            if (processEngineConfiguration.getMailSessionJndi(tenantId) != null) {
                setEmailSession(email, processEngineConfiguration.getMailSessionJndi(tenantId));
                isMailServerSet = true;

            } else if (processEngineConfiguration.getMailServer(tenantId) != null) {
                MailServerInfo mailServerInfo = processEngineConfiguration.getMailServer(tenantId);
                String host = mailServerInfo.getMailServerHost();
                if (host == null) {
                    throw new FlowableException(
                        "Could not send email: no SMTP host is configured for tenantId " + tenantId);
                }
                email.setHostName(host);

                email.setSmtpPort(mailServerInfo.getMailServerPort());

                email.setSSLOnConnect(mailServerInfo.isMailServerUseSSL());
                email.setStartTLSEnabled(mailServerInfo.isMailServerUseTLS());

                String user = mailServerInfo.getMailServerUsername();
                String password = mailServerInfo.getMailServerPassword();
                if (user != null && password != null) {
                    email.setAuthentication(user, password);
                }

                isMailServerSet = true;
            }
        }

        if (!isMailServerSet) {
            String mailSessionJndi = processEngineConfiguration.getMailSessionJndi();
            if (mailSessionJndi != null) {
                setEmailSession(email, mailSessionJndi);

            } else {
                String host = processEngineConfiguration.getMailServerHost();
                if (host == null) {
                    throw new FlowableException("Could not send email: no SMTP host is configured");
                }
                email.setHostName(host);

                int port = processEngineConfiguration.getMailServerPort();
                email.setSmtpPort(port);

                email.setSSLOnConnect(processEngineConfiguration.getMailServerUseSSL());
                email.setStartTLSEnabled(processEngineConfiguration.getMailServerUseTLS());

                String user = processEngineConfiguration.getMailServerUsername();
                String password = processEngineConfiguration.getMailServerPassword();
                if (user != null && password != null) {
                    email.setAuthentication(user, password);
                }
            }
        }
    }

    protected void setEmailSession(Email email, String mailSessionJndi) {
        try {
            email.setMailSessionFromJNDI(mailSessionJndi);
        } catch (NamingException e) {
            throw new FlowableException("Could not send email: Incorrect JNDI configuration", e);
        }
    }

    protected void setSubject(Email email, String subject) {
        email.setSubject(subject != null ? subject : "");
    }

    protected void addCc(Email email, String cc, String tenantId) {
        if (cc == null) {
            return;
        }

        String newCc = getForceTo(tenantId);
        if (newCc == null) {
            newCc = cc;
        }
        String[] ccs = FlowableEmailSenderUtils.splitAndTrim(newCc);
        if (ccs != null) {
            for (String c : ccs) {
                try {
                    email.addCc(c);
                } catch (EmailException e) {
                    throw new FlowableException("Could not add " + c + " as cc recipient", e);
                }
            }
        }
    }

    protected void addBcc(Email email, String bcc, String tenantId) {
        if (bcc == null) {
            return;
        }
        String newBcc = getForceTo(tenantId);
        if (newBcc == null) {
            newBcc = bcc;
        }
        String[] bccs = FlowableEmailSenderUtils.splitAndTrim(newBcc);
        if (bccs != null) {
            for (String b : bccs) {
                try {
                    email.addBcc(b);
                } catch (EmailException e) {
                    throw new FlowableException("Could not add " + b + " as bcc recipient", e);
                }
            }
        }
    }

    protected void attach(Email email, List<File> files, List<DataSource> dataSources) throws EmailException {
        if (!(email instanceof MultiPartEmail && attachmentsExist(files, dataSources))) {
            return;
        }
        MultiPartEmail mpEmail = (MultiPartEmail) email;
        for (File file : files) {
            mpEmail.attach(file);
        }
        for (DataSource ds : dataSources) {
            if (ds != null) {
                mpEmail.attach(ds, ds.getName(), null);
            }
        }
    }

    protected void setFrom(Email email, String from, String tenantId) {
        String fromAddress = null;

        if (from != null) {
            fromAddress = from;
        } else { // use default configured from address in process engine config
            if (tenantId != null && tenantId.length() > 0) {
                Map<String, MailServerInfo> mailServers = processEngineConfiguration.getMailServers();
                if (mailServers != null && mailServers.containsKey(tenantId)) {
                    MailServerInfo mailServerInfo = mailServers.get(tenantId);
                    fromAddress = mailServerInfo.getMailServerDefaultFrom();
                }
            }

            if (fromAddress == null) {
                fromAddress = processEngineConfiguration.getMailServerDefaultFrom();
            }
        }

        try {
            email.setFrom(fromAddress);
        } catch (EmailException e) {
            throw new FlowableException("Could not set " + from + " as from address in email", e);
        }
    }

    protected void addHeader(Email email, String headersStr) {
        if (headersStr == null) {
            return;
        }
        for (String headerEntry : headersStr.split(NEWLINE_REGEX)) {
            String[] split = headerEntry.split(":");
            if (split.length != 2) {
                throw new FlowableIllegalArgumentException(
                    "When using email headers name and value must be defined colon separated. " +
                        "(e.g. X-Attribute: value");
            }
            String name = split[0].trim();
            String value = split[1].trim();
            email.addHeader(name, value);
        }
    }

    protected void addTo(Email email, String to, String tenantId) {
        if (to == null) {
            // To has to be set, otherwise it can fallback to the forced To and then it won't be noticed early on
            throw new FlowableException("No recipient could be found for sending email");
        }
        String newTo = getForceTo(tenantId);
        if (newTo == null) {
            newTo = to;
        }
        String[] tos = FlowableEmailSenderUtils.splitAndTrim(newTo);
        if (tos != null) {
            for (String t : tos) {
                try {
                    email.addTo(t);
                } catch (EmailException e) {
                    throw new FlowableException("Could not add " + t + " as recipient", e);
                }
            }
        } else {
            throw new FlowableException("No recipient could be found for sending email");
        }
    }

    protected String getForceTo(String tenantId) {
        String forceTo = null;
        if (tenantId != null && tenantId.length() > 0) {
            Map<String, MailServerInfo> mailServers = processEngineConfiguration.getMailServers();
            if (mailServers != null && mailServers.containsKey(tenantId)) {
                MailServerInfo mailServerInfo = mailServers.get(tenantId);
                forceTo = mailServerInfo.getMailServerForceTo();
            }
        }

        if (forceTo == null) {
            forceTo = processEngineConfiguration.getMailServerForceTo();
        }

        return forceTo;
    }

    private boolean attachmentsExist(List<File> files, List<DataSource> dataSources) {
        return !((files == null || files.isEmpty()) && (dataSources == null || dataSources.isEmpty()));
    }

    private Email createEmail(String text, String html, boolean attachmentsExist) {
        if (html != null) {
            return createHtmlEmail(text, html);
        } else if (text != null) {
            if (!attachmentsExist) {
                return createTextOnlyEmail(text);
            } else {
                return createMultiPartEmail(text);
            }
        } else {
            throw new FlowableIllegalArgumentException(
                "'html' or 'text' is required to be defined when using the mail activity");
        }
    }

    private HtmlEmail createHtmlEmail(String text, String html) {
        HtmlEmail email = new HtmlEmail();
        try {
            email.setHtmlMsg(html);
            if (text != null) { // for email clients that don't support html
                email.setTextMsg(text);
            }
            return email;
        } catch (EmailException e) {
            throw new FlowableException("Could not create HTML email", e);
        }
    }

    private SimpleEmail createTextOnlyEmail(String text) {
        SimpleEmail email = new SimpleEmail();
        try {
            email.setMsg(text);
            return email;
        } catch (EmailException e) {
            throw new FlowableException("Could not create text-only email", e);
        }
    }

    private MultiPartEmail createMultiPartEmail(String text) {
        MultiPartEmail email = new MultiPartEmail();
        try {
            email.setMsg(text);
            return email;
        } catch (EmailException e) {
            throw new FlowableException("Could not create text-only email", e);
        }
    }
}
