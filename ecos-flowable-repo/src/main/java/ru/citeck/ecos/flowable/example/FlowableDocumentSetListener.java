package ru.citeck.ecos.flowable.example;

import org.flowable.engine.delegate.DelegateExecution;

/**
 * @author Roman Makarskiy
 * <p>
 * Document set listener
 * @deprecated Now document set by default^
 * {@link ru.citeck.ecos.flowable.listeners.global.impl.variables.FlowableBaseVariablesSetListener }
 */
@Deprecated
public class FlowableDocumentSetListener extends AbstractExecutionListener {

    @Override
    protected void notifyImpl(DelegateExecution execution) {
        // Do nothing. Document should be set in FlowableBaseVariablesSetListener
    }
}
