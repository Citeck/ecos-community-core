package ru.citeck.ecos.webapp.txn

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.txn.lib.manager.EcosTxnProps
import ru.citeck.ecos.txn.lib.manager.TransactionManager
import ru.citeck.ecos.txn.lib.manager.TransactionManagerImpl
import ru.citeck.ecos.webapp.lib.env.EcosWebAppEnvironment

@Configuration
class EcosTxnConfiguration {

    @Bean
    fun ecosTransactionProps(env: EcosWebAppEnvironment): EcosTxnProps {
        return env.getValue("ecos.webapp.txn", EcosTxnProps::class.java)
    }

    @Bean
    fun ecosTransactionManager(): TransactionManager {
        val transactionManager = TransactionManagerImpl()
        TxnContext.setManager(transactionManager)
        return transactionManager
    }
}
