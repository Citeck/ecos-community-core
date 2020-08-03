package ru.citeck.ecos.flowable.example;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.flowable.engine.delegate.DelegateExecution;
import ru.citeck.ecos.flowable.utils.FlowableListenerUtils;
import ru.citeck.ecos.flowable.variable.FlowableScriptNode;

/**
 * @author Roman Makarskiy
 * <p>
 * Document set listener
 * @deprecated Now document set by default^
 * {@link ru.citeck.ecos.flowable.listeners.global.impl.variables.FlowableBaseVariablesSetListener }
 */
@Deprecated
public class FlowableDocumentSetListener extends AbstractExecutionListener {

    private static final String VAR_DOCUMENT = "document";

    private NodeService nodeService;

    @Override
    protected void initImpl() {
        this.nodeService = serviceRegistry.getNodeService();
    }

    @Override
    protected void notifyImpl(DelegateExecution execution) {
        NodeRef document = FlowableListenerUtils.getDocument(execution, nodeService);
        if (document != null) {
            FlowableScriptNode node = new FlowableScriptNode(document, serviceRegistry);
            execution.setVariable(VAR_DOCUMENT, node);
        } else {
            execution.setVariable(VAR_DOCUMENT, null);
        }
    }
}
