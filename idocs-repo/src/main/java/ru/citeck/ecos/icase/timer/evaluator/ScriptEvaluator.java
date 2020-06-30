package ru.citeck.ecos.icase.timer.evaluator;

import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.ScriptService;
import org.alfresco.util.ISO8601DateFormat;
import org.mozilla.javascript.Undefined;
import ru.citeck.ecos.action.ActionConditionUtils;
import ru.citeck.ecos.icase.activity.dto.ActivityInstance;
import ru.citeck.ecos.icase.activity.service.eproc.EProcUtils;
import ru.citeck.ecos.icase.activity.service.eproc.importer.parser.CmmnDefinitionConstants;
import ru.citeck.ecos.model.CaseTimerModel;
import ru.citeck.ecos.records.RecordsUtils;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.service.AlfrescoServices;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Pavel Simonov
 */
public class ScriptEvaluator extends Evaluator {

    private static final String MODEL_TIMER = "timer";
    private static final String MODEL_DOCUMENT = "document";
    private static final String MODEL_REPEAT_COUNTER = "repeatCounter";

    private ScriptService scriptService;

    @Override
    public CaseTimerModel.ExpressionType getType() {
        return CaseTimerModel.ExpressionType.SCRIPT;
    }

    @Override
    public String evaluate(NodeRef timerRef, Date fromDate, int repeatCounter) {
        String script = (String) nodeService.getProperty(timerRef, CaseTimerModel.PROP_TIMER_EXPRESSION);
        Map<String, Object> model = buildAlfContextModel(timerRef, repeatCounter);
        return executeScript(script, model);
    }

    @Override
    public String evaluate(RecordRef caseRef, ActivityInstance activityInstance, Date fromDate, int repeatCounter) {
        String script = EProcUtils.getAnyAttribute(activityInstance, CmmnDefinitionConstants.TIMER_EXPRESSION);
        Map<String, Object> model = buildContextModel(caseRef, repeatCounter);
        return executeScript(script, model);
    }

    private String executeScript(String expression, Map<String, Object> model) {
        Object result = scriptService.executeScriptString(expression, model);
        if (result == null) {
            return null;
        }
        if (result instanceof Date) {
            return ISO8601DateFormat.format((Date) result);
        } else if (result instanceof String) {
            return (String) result;
        } else if (result instanceof Undefined) {
            throw new IllegalStateException("Timer script return return nothing. Script: " + expression);
        }
        throw new IllegalStateException("Timer script return incorrect result with type "
                + result.getClass() + " but expected Date or String");
    }

    private Map<String, Object> buildAlfContextModel(NodeRef timerRef, int repeatCounter) {
        RecordRef caseRef = alfActivityUtils.getDocumentId(timerRef);
        Map<String, Object> model = buildContextModel(caseRef, repeatCounter);
        model.put(MODEL_TIMER, timerRef);
        return model;
    }

    private Map<String, Object> buildContextModel(RecordRef caseRef, int repeatCounter) {
        Map<String, Object> model = new HashMap<>();

        Map<String, Object> variables = ActionConditionUtils.getTransactionVariables();
        for (Map.Entry<String, Object> variable : variables.entrySet()) {
            model.put(variable.getKey(), variable.getValue());
        }

        model.put(MODEL_DOCUMENT, RecordsUtils.toNodeRef(caseRef));
        model.put(MODEL_REPEAT_COUNTER, repeatCounter);

        return model;
    }

    @Override
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        super.setServiceRegistry(serviceRegistry);
        scriptService = (ScriptService) serviceRegistry.getService(AlfrescoServices.SCRIPT_SERVICE);
    }
}
