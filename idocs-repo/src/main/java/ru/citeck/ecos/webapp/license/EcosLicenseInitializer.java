package ru.citeck.ecos.webapp.license;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.webapp.lib.license.EcosZkLicenseProvider;
import ru.citeck.ecos.webapp.lib.license.LicenseCtxAttsProvider;
import ru.citeck.ecos.zookeeper.EcosZooKeeper;

import javax.annotation.PostConstruct;

@Component
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class EcosLicenseInitializer {

    private final EcosZooKeeper zooKeeper;
    private final RecordsServiceFactory recordsServiceFactory;

    @PostConstruct
    public void init() {
        EcosZkLicenseProvider.Companion.setZookeeper(zooKeeper);
        recordsServiceFactory.getCtxAttsService().register(new LicenseCtxAttsProvider());
    }
}
