package ru.citeck.ecos.node;

import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.utils.StringUtils;

import java.util.function.Function;

@Service
public class DisplayNameService {

    public static final QName QNAME = QName.createQName("", "displayNameService");

    private static final String NO_NAME_MSG_KEY = "ecos.service.display-name.no-name";

    private final EvaluatorsByAlfNode<String> evaluators;

    @Autowired
    public DisplayNameService(ServiceRegistry serviceRegistry) {
        evaluators = new EvaluatorsByAlfNode<>(serviceRegistry, node -> getNoNameMsg());
    }

    @NotNull
    public String getDisplayName(NodeRef nodeRef) {
        return evaluators.eval(nodeRef);
    }

    @NotNull
    public String getDisplayName(AlfNodeInfo nodeInfo) {
        return evaluators.eval(nodeInfo);
    }

    private String getNoNameMsg() {
        String message = I18NUtil.getMessage(NO_NAME_MSG_KEY);
        return StringUtils.isBlank(message) ? "No name" : message;
    }

    public void register(QName nodeType, Function<AlfNodeInfo, String> evaluator) {
        evaluators.register(nodeType, evaluator);
    }
}
