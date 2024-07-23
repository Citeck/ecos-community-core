package ru.citeck.ecos.utils;

import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.config.EcosConfigService;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class NewUIUtils {

    public static final QName QNAME = QName.createQName("", "newUIUtils");

    public static final String UI_TYPE_SHARE = "share";
    public static final String UI_TYPE_REACT = "react";

    private static final String UI_TYPE_ATT = "uiType";

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
    private final EcosTypeService ecosTypeService;

    private final LoadingCache<EntityRef, String> uiTypeByRecord;
    private final LoadingCache<String, Boolean> isNewUIEnabledForUserCache;

    @Autowired
    public NewUIUtils(@Qualifier("ecosConfigService") EcosConfigService ecosConfigService,
                      @Qualifier("authenticationService") AuthenticationService authenticationService,
                      EcosTypeService ecosTypeService,
                      AuthorityService authorityService,
                      RecordsService recordsService) {

        this.ecosConfigService = ecosConfigService;
        this.authenticationService = authenticationService;
        this.authorityService = authorityService;
        this.ecosTypeService = ecosTypeService;
        this.recordsService = recordsService;

        isNewUIEnabledForUserCache = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(100)
            .build(CacheLoader.from(this::isNewUIEnabledForUserImpl));

        uiTypeByRecord = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .maximumSize(100)
            .build(CacheLoader.from(this::getUITypeByRecord));
    }

    public void invalidateCache() {
        uiTypeByRecord.invalidateAll();
        isNewUIEnabledForUserCache.invalidateAll();
    }

    public void invalidateCacheForUser(String username) {
        isNewUIEnabledForUserCache.invalidate(username);
    }

    public boolean isNewUIEnabled() {
        return isNewUIEnabledForUser(authenticationService.getCurrentUserName());
    }

    public boolean isNewUIEnabledForUser(String username) {
        return isNewUIEnabledForUserCache.getUnchecked(username);
    }

    public boolean isOldCardDetailsRequired(RecordRef recordRef) {
        return getUITypeForRecordAndUser(
            recordRef,
            authenticationService.getCurrentUserName()
        ).equals(UI_TYPE_SHARE);
    }

    public String getNewUIRedirectUrl() {
        Object objValue = ecosConfigService.getParamValue(NEW_UI_REDIRECT_URL);
        String newUIRedirectUrl = String.valueOf(objValue);
        if (StringUtils.isBlank(newUIRedirectUrl) || newUIRedirectUrl.equals("null")) {
            newUIRedirectUrl = V2_DASHBOARD_URL_DEFAULT;
        }
        return newUIRedirectUrl;
    }

    public String getUITypeForRecord(EntityRef recordRef) {
        try {
            return uiTypeByRecord.getUnchecked(recordRef);
        } catch (Exception e) {
            log.error("Exception. RecordRef: " + recordRef, e);
            return "";
        }
    }

    public String getUITypeForRecordAndUser(EntityRef recordRef) {
        return getUITypeForRecordAndUser(recordRef, authenticationService.getCurrentUserName());
    }

    public String getUITypeForRecordAndUser(EntityRef recordRef, String userName) {
        String uiType = getUITypeForRecord(recordRef);

        String resStr = uiType;
        if (StringUtils.isBlank(uiType)) {
            resStr = isNewUIEnabledForUser(userName) ? UI_TYPE_REACT : UI_TYPE_SHARE;
        }

        return resStr;
    }

    private boolean isNewUIEnabledForUserImpl(String username) {
        Object objValue = ecosConfigService.getParamValue(NEW_UI_REDIRECT_ENABLED);
        boolean isNewUIRedirectEnabled = String.valueOf(objValue).equals(Boolean.TRUE.toString());
        return isNewUIRedirectEnabled || isNewJournalsGroupMember(username) || isNewJournalsEnabledForUser(username);
    }

    private String getUITypeByRecord(EntityRef recordRef) {

        String att;
        if (recordRef.getSourceId().equals("site")) {
            att = UI_TYPE_FROM_SECTION_ATT;
            recordRef = EntityRef.create("emodel", "section", recordRef.getLocalId());
        } else if (recordRef.getSourceId().equals("type")) {
            att = UI_TYPE_FROM_SECTION_ATT;
        } else {
            att = UI_TYPE_FROM_ETYPE_ATT;
        }

        DataValue res = recordsService.getAttribute(recordRef, att);

        if (res == null || !res.isTextual() || res.asText().equals("null") || StringUtils.isBlank(res.asText())) {
            if (UI_TYPE_FROM_ETYPE_ATT.equals(att) && NodeRef.isNodeRef(recordRef.getLocalId())) {
                res = getUiTypeFromParent(recordRef);
            } else {
                res = DataValue.NULL;
            }
        }

        String resStr;
        if (res.isNull() || StringUtils.isBlank(res.asText())) {
            resStr = "";
        } else {
            resStr = res.asText();

            if (!UI_TYPE_SHARE.equals(resStr) && !UI_TYPE_REACT.equals(resStr)) {
                resStr = "";
            }
        }

        return resStr;
    }

    private DataValue getUiTypeFromParent(EntityRef recordRef) {
        EntityRef ecosTypeRef = ecosTypeService.getEcosType(new NodeRef(recordRef.getLocalId()));
        if (EntityRef.isEmpty(ecosTypeRef)) {
            return DataValue.NULL;
        }
        AtomicReference<String> uiType = new AtomicReference<>();
        ecosTypeService.forEachAsc(ecosTypeRef, type -> {
            if (type.getProperties() != null) {
                String parentUiType = type.getProperties().get(UI_TYPE_ATT).asText();
                if (StringUtils.isNotBlank(parentUiType)) {
                    uiType.set(parentUiType);
                    return true;
                }
            }
            return false;
        });
        return DataValue.createStr(uiType.get());
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

    private boolean isNewJournalsEnabledForUser(String username) {
        RecordRef recordRef = RecordRef.create("people", username);
        DataValue att = recordsService.getAtt(recordRef, "ecos:newJournalsEnabled");
        return att.asBoolean();
    }

    @Data
    @AllArgsConstructor
    private static class RecordRefUserKey {
        private RecordRef recordRef;
        private String userName;
    }
}
