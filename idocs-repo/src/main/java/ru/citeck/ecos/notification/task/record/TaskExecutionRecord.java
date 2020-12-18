package ru.citeck.ecos.notification.task.record;

import lombok.Data;
import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.records3.record.op.atts.service.value.AttValue;

import java.util.HashMap;
import java.util.Map;

@Data
public class TaskExecutionRecord implements AttValue {

    private String taskId = null;
    private String taskName = null;
    private String taskTitle = null;
    private String taskDescription = null;
    private Map<String, Object> properties = new HashMap<>();
    private Map<String, Object> workflow = new HashMap<>();

    @Nullable
    @Override
    public Object getId() {
        return this.taskId;
    }

    @Nullable
    @Override
    public Object asJson() throws Exception {
        HashMap<String, Object> json = new HashMap<>();
        json.put("taskId", taskId);
        json.put("taskName", taskName);
        json.put("taskTitle", taskTitle);
        json.put("taskDescription", taskDescription);
        json.put("properties", properties);
        json.put("workflow", workflow);
        return json;
    }

    @Nullable
    @Override
    public Object getAtt(String name) {
        switch (name) {
            case "name":
                return this.taskName;
            case "title":
                return this.taskTitle;
            case "description":
                return this.taskDescription;
            case "workflow":
                return this.workflow;
            default:
                return properties.get(name);
        }
    }

}
