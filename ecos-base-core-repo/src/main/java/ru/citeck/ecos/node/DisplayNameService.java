package ru.citeck.ecos.node;

import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Function;

@Service
public class DisplayNameService {

    public static final QName QNAME = QName.createQName("", "displayNameService");

    private EvaluatorsByAlfNode<String> evaluators;

    @Autowired
    public DisplayNameService(ServiceRegistry serviceRegistry) {
        evaluators = new EvaluatorsByAlfNode<>(serviceRegistry, node -> String.valueOf(node.getNodeRef()));
    }

    public String getDisplayName(NodeRef nodeRef) {
        return evaluators.eval(nodeRef);
    }

    public String getDisplayName(AlfNodeInfo nodeInfo) {
        return evaluators.eval(nodeInfo);
    }

    public void register(QName nodeType, Function<AlfNodeInfo, String> evaluator) {
        evaluators.register(nodeType, evaluator);
    }
}
