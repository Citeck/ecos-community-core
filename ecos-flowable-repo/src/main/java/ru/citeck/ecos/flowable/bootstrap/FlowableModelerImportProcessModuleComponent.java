package ru.citeck.ecos.flowable.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.module.AbstractModuleComponent;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import ru.citeck.ecos.flowable.services.FlowableModelerService;

/**
 * @author Roman Makarskiy
 */
@Slf4j
public class FlowableModelerImportProcessModuleComponent extends AbstractModuleComponent {

    /**
     * Services
     */
    private RetryingTransactionHelper retryingTransactionHelper;
    private FlowableModelerService flowableModelerService;

    /**
     * Execute internal
     */
    @Override
    protected void executeInternal() {
        AuthenticationUtil.runAsSystem(() -> retryingTransactionHelper.doInTransaction(() -> {
            if (importRequired()) {
                flowableModelerService.importProcessModel();
            }
            return null;
        }));
    }

    /**
     * Check - is import required
     * @return Check result
     */
    private boolean importRequired() {
        if (flowableModelerService == null || !flowableModelerService.importIsPossible()) {
            log.info("Cannot import process model, because flowable integration is not initialized.");
            return false;
        }
        return true;
    }

    /** Setters */

    public void setRetryingTransactionHelper(RetryingTransactionHelper retryingTransactionHelper) {
        this.retryingTransactionHelper = retryingTransactionHelper;
    }

    public void setFlowableModelerService(FlowableModelerService flowableModelerService) {
        this.flowableModelerService = flowableModelerService;
    }
}
