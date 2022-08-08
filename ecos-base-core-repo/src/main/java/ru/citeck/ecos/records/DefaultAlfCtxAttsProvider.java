package ru.citeck.ecos.records;

import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.record.request.ctxatts.CtxAttsProvider;
import ru.citeck.ecos.utils.UrlUtils;

import javax.annotation.PostConstruct;
import java.util.Map;

@Component
public class DefaultAlfCtxAttsProvider implements CtxAttsProvider {

    @Autowired
    private RecordsConfiguration recordsConfiguration;

    @Autowired
    private UrlUtils urlUtils;

    @PostConstruct
    public void init() {
        recordsConfiguration.getCtxAttsService().register(this);
    }

    @Override
    public void fillContextAtts(@NotNull Map<String, Object> contextAtts) {
        contextAtts.put("user", RecordRef.valueOf("people@" + AuthenticationUtil.getFullyAuthenticatedUser()));
        contextAtts.put("webUrl", urlUtils.getWebUrl());
        contextAtts.put("shareUrl", urlUtils.getShareUrl());
        contextAtts.put("alfMeta", RecordRef.create("alfresco", "meta", ""));
    }

    @Override
    public float getOrder() {
        return 100;
    }
}
