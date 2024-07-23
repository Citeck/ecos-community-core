package ru.citeck.ecos.icase.timer.evaluator;

import org.alfresco.service.cmr.repository.NodeRef;
import ru.citeck.ecos.icase.activity.dto.ActivityInstance;
import ru.citeck.ecos.icase.activity.service.eproc.EProcUtils;
import ru.citeck.ecos.icase.activity.service.eproc.importer.parser.CmmnDefinitionConstants;
import ru.citeck.ecos.model.CaseTimerModel;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Date;

/**
 * @author Pavel Simonov
 */
public class ExpressionEvaluator extends Evaluator {

    @Override
    public String evaluate(NodeRef timerRef, Date fromDate, int repeatCounter) {
        return (String) nodeService.getProperty(timerRef, CaseTimerModel.PROP_TIMER_EXPRESSION);
    }

    @Override
    public String evaluate(RecordRef caseRef, ActivityInstance activityInstance, Date fromDate, int repeatCounter) {
        return EProcUtils.getAnyAttribute(activityInstance, CmmnDefinitionConstants.TIMER_EXPRESSION);
    }

    @Override
    public CaseTimerModel.ExpressionType getType() {
        return CaseTimerModel.ExpressionType.EXPRESSION;
    }
}
