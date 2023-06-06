package ru.citeck.ecos.webapp.txn

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.citeck.ecos.txn.lib.TxnContext
import ru.citeck.ecos.txn.lib.manager.TransactionManager
import ru.citeck.ecos.txn.lib.manager.TransactionManagerImpl

@Configuration
class EcosTxnConfiguration {

    @Bean
    fun ecosTransactionManager(): TransactionManager {
        val transactionManager = TransactionManagerImpl()
        TxnContext.setManager(transactionManager)
        return transactionManager
    }
}
