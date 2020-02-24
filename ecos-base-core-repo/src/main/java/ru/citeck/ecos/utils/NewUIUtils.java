package ru.citeck.ecos.utils;

import com.fasterxml.jackson.databind.JsonNode;
import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.config.EcosConfigService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class NewUIUtils {

    public static final QName QNAME = QName.createQName("", "newUIUtils");

    public static final String UI_TYPE_SHARE = "share";
    public static final String UI_TYPE_REACT = "react";

    private static final String NEW_UI_REDIRECT_ENABLED = "new-ui-redirect-enabled";
    private static final String NEW_UI_REDIRECT_URL = "new-ui-redirect-url";
    private static final String V2_DASHBOARD_URL_DEFAULT = "/v2/dashboard";
    private static final String DEFAULT_UI_NEW_JOURNALS_ACCESS_GROUPS = "default-ui-new-journals-access-groups";

    private static final String UI_TYPE_FROM_ETYPE_ATT = "_etype.attributes.uiType?str";
    private static final String UI_TYPE_FROM_SECTION_ATT = "attributes.uiType?str";

    private final EcosConfigService ecosConfigService;
    private final AuthenticationService authenticationService;
    private final AuthorityService authorityService;
    private final RecordsService recordsService;

    private LoadingCache<RecordRef, String> uiTypeByRecord;
    private LoadingCache<String, Boolean> isNewUIEnabledCache;

    @Autowired
    public NewUIUtils(@Qualifier("ecosConfigService") EcosConfigService ecosConfigService,
                      @Qualifier("authenticationService") AuthenticationService authenticationService,
                      AuthorityService authorityService,
                      RecordsService recordsService) {

        this.ecosConfigService = ecosConfigService;
        this.authenticationService = authenticationService;
        this.authorityService = authorityService;
        this.recordsService = recordsService;

        isNewUIEnabledCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(100)
            .build(CacheLoader.from(this::isNewUIEnabledForUserImpl));

        uiTypeByRecord = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(100)
            .build(CacheLoader.from(this::getUITypeByRecord));
    }

    public boolean isNewUIEnabled() {
        return isNewUIEnabledForUser(authenticationService.getCurrentUserName());
    }

    public boolean isNewUIEnabledForUser(String username) {
        return isNewUIEnabledCache.getUnchecked(username);
    }

    public boolean isOldCardDetailsRequired(RecordRef recordRef) {
        return getUITypeByRecord(recordRef).equals(UI_TYPE_SHARE);
    }

    public String getNewUIRedirectUrl() {
        Object objValue = ecosConfigService.getParamValue(NEW_UI_REDIRECT_URL);
        String newUIRedirectUrl = String.valueOf(objValue);
        if (StringUtils.isBlank(newUIRedirectUrl) || newUIRedirectUrl.equals("null")) {
            newUIRedirectUrl = V2_DASHBOARD_URL_DEFAULT;
        }
        return newUIRedirectUrl;
    }

    public String getUITypeForRecord(RecordRef recordRef) {
        try {
            return uiTypeByRecord.getUnchecked(recordRef);
        } catch (Exception e) {
            log.error("Exception. RecordRef: " + recordRef, e);
            return "";
        }
    }

    private boolean isNewUIEnabledForUserImpl(String username) {
        Object objValue = ecosConfigService.getParamValue(NEW_UI_REDIRECT_ENABLED);
        boolean isNewUIRedirectEnabled = String.valueOf(objValue).equals(Boolean.TRUE.toString());
        return isNewUIRedirectEnabled || isNewJournalsGroupMember(username);
    }

    private String getUITypeByRecord(RecordRef recordRef) {
        if (!isNewUIEnabled()) {
            return UI_TYPE_SHARE;
        }
        String att;
        if (recordRef.getSourceId().equals("site")) {
            att = UI_TYPE_FROM_SECTION_ATT;
            recordRef = RecordRef.create("emodel", "section", recordRef.getId());
        } else if (recordRef.getSourceId().equals("type")) {
            att = UI_TYPE_FROM_SECTION_ATT;
        } else {
            att = UI_TYPE_FROM_ETYPE_ATT;
        }
        DataValue res = recordsService.getAttribute(recordRef, att);
        return res != null && res.isTextual() ? res.asText() : "";
    }

    private boolean isNewJournalsGroupMember(String username) {
        String groupsInString = (String) ecosConfigService.getParamValue(DEFAULT_UI_NEW_JOURNALS_ACCESS_GROUPS);
        if (StringUtils.isEmpty(groupsInString)) {
            return false;
        }
        Set<String> avalibleGroups = new HashSet<>(Arrays.asList(groupsInString.split(",")));
        Set<String> userGroups = authorityService.getAuthoritiesForUser(username);
        return !Collections.disjoint(avalibleGroups, userGroups);
    }
}
