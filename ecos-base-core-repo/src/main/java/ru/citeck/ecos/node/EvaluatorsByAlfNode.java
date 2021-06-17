package ru.citeck.ecos.node;

import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.ClassDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class EvaluatorsByAlfNode<T> {

    private final Map<QName, Function<AlfNodeInfo, T>> evaluators = new ConcurrentHashMap<>();
    private final Map<QName, List<Function<AlfNodeInfo, T>>> typeEvaluators = new ConcurrentHashMap<>();
    private final Function<AlfNodeInfo, T> defaultEvaluator;

    private final ServiceRegistry serviceRegistry;
    private final DictionaryService dictionaryService;

    public EvaluatorsByAlfNode(ServiceRegistry serviceRegistry, Function<AlfNodeInfo, T> defaultEvaluator) {
        this.serviceRegistry = serviceRegistry;
        this.dictionaryService = serviceRegistry.getDictionaryService();
        if (defaultEvaluator != null) {
            this.defaultEvaluator = defaultEvaluator;
        } else {
            this.defaultEvaluator = node -> null;
        }
    }

    public T eval(NodeRef nodeRef) {
        return eval(new AlfNodeInfoImpl(nodeRef, serviceRegistry));
    }

    public T eval(AlfNodeInfo nodeInfo) {

        QName type = nodeInfo.getType();

        List<Function<AlfNodeInfo, T>> evaluators = getEvaluatorsForType(type);
        T result = null;
        int i = 0;
        while (result == null && i < evaluators.size()) {
            result = evaluators.get(i).apply(nodeInfo);
            i++;
        }

        if (result == null) {
            result = defaultEvaluator.apply(nodeInfo);
        }

        return result;
    }

    private List<Function<AlfNodeInfo, T>> getEvaluatorsForType(QName type) {

        return typeEvaluators.computeIfAbsent(type, t -> {

            List<Function<AlfNodeInfo, T>> result = new ArrayList<>();

            ClassDefinition typeDef = dictionaryService.getClass(type);
            while (typeDef != null) {
                Function<AlfNodeInfo, T> evaluator = evaluators.get(typeDef.getName());
                if (evaluator != null) {
                    result.add(evaluator);
                }
                typeDef = typeDef.getParentClassDefinition();
            }

            return result;
        });
    }

    public void register(QName nodeType, Function<AlfNodeInfo, T> evaluator) {
        evaluators.put(nodeType, evaluator);
        typeEvaluators.clear();
    }
}
