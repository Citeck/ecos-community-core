package ru.citeck.ecos.utils;

import org.alfresco.repo.admin.SysAdminParams;
import org.alfresco.util.UrlUtil;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.RecordRef;

@Component
public class UrlUtils {

    private static final String DASHBOARD_REF_URL = "%s/v2/dashboard?recordRef=%s";

    private final SysAdminParams sysAdminParams;

    @Autowired
    public UrlUtils(SysAdminParams sysAdminParams) {
        this.sysAdminParams = sysAdminParams;
    }

    /**
     * Builds up the dashboard Url for RecordRef, based on {@link #getWebUrl} and {@link #DASHBOARD_REF_URL}
     *
     * @return Url such as https://col.ab.or.ate/v2/dashboard?recordRef=some-record-ref
     */
    public String generateDashboardRefUrl(@NotNull String recordRef) {
        return String.format(DASHBOARD_REF_URL, getWebUrl(), recordRef);
    }

    /**
     * Builds up the dashboard Url for RecordRef, based on {@link #getWebUrl} and {@link #DASHBOARD_REF_URL}
     *
     * @return Url such as https://col.ab.or.ate/v2/dashboard?recordRef=some-record-ref
     */
    public String generateDashboardRefUrl(@NotNull RecordRef recordRef) {
        return String.format(DASHBOARD_REF_URL, getWebUrl(), recordRef.toString());
    }

    /**
     * Builds up the web Url based on the settings in the
     * {@link SysAdminParams}.
     *
     * @return Url such as https://col.ab.or.ate/
     * or http://localhost:8081/
     */
    public String getWebUrl() {
        return buildUrl(
            sysAdminParams.getShareProtocol(),
            sysAdminParams.getShareHost(),
            sysAdminParams.getSharePort()
        );
    }

    /**
     * Builds up the Url to Share based on the settings in the
     * {@link SysAdminParams}.
     *
     * @return Alfresco Url such as https://col.ab.or.ate/share/
     * or http://localhost:8081/share/
     */
    public String getShareUrl() {
        return UrlUtil.getShareUrl(sysAdminParams);
    }

    private String buildUrl(String protocol, String host, int port) {
        StringBuilder url = new StringBuilder();
        url.append(protocol);
        url.append("://");
        url.append(host);
        if ("http".equals(protocol) && port == 80) {
            // Not needed
        } else if ("https".equals(protocol) && port == 443) {
            // Not needed
        } else {
            url.append(':');
            url.append(port);
        }
        return url.toString();
    }

}
