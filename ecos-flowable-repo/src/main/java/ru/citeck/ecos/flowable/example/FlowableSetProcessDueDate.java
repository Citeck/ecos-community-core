package ru.citeck.ecos.flowable.example;

import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.common.engine.impl.calendar.BusinessCalendar;
import org.flowable.common.engine.impl.calendar.DurationBusinessCalendar;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.flowable.engine.impl.context.Context;

import java.util.Date;

/**
 * Set process due date execution listener
 */
public class FlowableSetProcessDueDate implements ExecutionListener {

    private Expression calendar;
    private Expression variable;
    private Expression dueDate;

    @Override
    public void notify(DelegateExecution execution) {
        String calendarName = this.calendar != null ? this.calendar.getValue(execution).toString() : DurationBusinessCalendar.NAME;
        String dueDateExpr = (String) dueDate.getValue(execution);
        BusinessCalendar calendar = Context.getProcessEngineConfiguration().getBusinessCalendarManager().getBusinessCalendar(calendarName);
        Date dueDateValue = calendar.resolveDuedate(dueDateExpr);
        execution.setVariable(variable.getValue(execution).toString(), dueDateValue);
    }

}
