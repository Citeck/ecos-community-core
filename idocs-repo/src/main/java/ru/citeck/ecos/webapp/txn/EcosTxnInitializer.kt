package ru.citeck.ecos.webapp.txn

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.citeck.ecos.txn.lib.manager.TransactionManager
import ru.citeck.ecos.txn.lib.manager.TransactionManagerImpl
import ru.citeck.ecos.txn.lib.manager.api.TxnManagerWebExecutor
import ru.citeck.ecos.webapp.api.EcosWebAppApi
import ru.citeck.ecos.webapp.api.web.executor.EcosWebExecutorsApi
import javax.annotation.PostConstruct

@Component
class EcosTxnInitializer @Autowired constructor(
    private val webExecutors: EcosWebExecutorsApi,
    private val txnManager: TransactionManager,
    private val webAppApi: EcosWebAppApi
) {

    @PostConstruct
    fun init() {
        webExecutors.register(TxnManagerWebExecutor(txnManager))
        if (txnManager is TransactionManagerImpl) {
            txnManager.init(webAppApi)
        }
    }
}
