package ru.citeck.ecos.records;

import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.LoadingCache;
import org.alfresco.service.cmr.module.ModuleDetails;
import org.alfresco.service.cmr.module.ModuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.source.dao.local.meta.MetaAttributesSupplier;
import ru.citeck.ecos.records2.source.dao.local.meta.MetaRecordsDaoAttsProvider;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class AlfMetaRecordsDaoAttsProvider implements MetaAttributesSupplier {

    private static final String ATT_EDITION = "edition";
    private static final String ATT_MODULES = "alfModules";

    private final ModuleService moduleService;

    private final LoadingCache<String, Optional<Object>> attsCache;

    @Autowired
    public AlfMetaRecordsDaoAttsProvider(ModuleService moduleService,
                                         MetaRecordsDaoAttsProvider provider) {
        this.moduleService = moduleService;

        attsCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .maximumSize(100)
            .build(CacheLoader.from(this::getAttributeImpl));

        provider.register(this);
    }

    @Override
    public List<String> getAttributesList() {
        return Arrays.asList(ATT_EDITION, ATT_MODULES);
    }

    @Override
    public Object getAttribute(String name) {
        return attsCache.getUnchecked(name).orElse(null);
    }

    private Optional<Object> getAttributeImpl(String name) {

        Object result = null;

        switch (name) {
            case ATT_EDITION:
                result = getEdition();
                break;
            case ATT_MODULES:
                result = new AlfModules();
                break;
        }

        return Optional.ofNullable(result);
    }

    private String getEdition() {
        ModuleDetails module = moduleService.getModule("ecos-enterprise-repo");
        if (module == null) {
            return "community";
        } else {
            return "enterprise";
        }
    }

    public class AlfModules implements MetaValue {

        @Override
        public boolean has(String name) {
            return moduleService.getModule(name) != null;
        }
    }
}
