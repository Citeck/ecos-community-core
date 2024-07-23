package ru.citeck.ecos.webapp.txn

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.citeck.ecos.micrometer.EcosMicrometerContext
import ru.citeck.ecos.txn.lib.manager.EcosTxnProps
import ru.citeck.ecos.txn.lib.manager.TransactionManager
import ru.citeck.ecos.txn.lib.manager.TransactionManagerImpl
import ru.citeck.ecos.txn.lib.manager.api.server.TxnManagerRemoteActions
import ru.citeck.ecos.txn.lib.manager.api.server.TxnManagerWebExecutor
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorsApi
import javax.annotation.PostConstruct

@Component
class EcosTxnInitializer @Autowired constructor(
    private val webExecutors: EcosWebExecutorsApi,
    private val txnManager: TransactionManager,
    private val webAppApi: EcosWebAppApi,
    private val micrometerContext: EcosMicrometerContext,
    private val props: EcosTxnProps
) {

    @PostConstruct
    fun init() {
        webExecutors.register(
            TxnManagerWebExecutor(
                TxnManagerRemoteActions(txnManager, micrometerContext)
            )
        )
        if (txnManager is TransactionManagerImpl) {
            txnManager.init(webAppApi, props, micrometerContext)
        }
    }
}
