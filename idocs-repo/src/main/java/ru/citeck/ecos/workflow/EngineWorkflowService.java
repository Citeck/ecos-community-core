package ru.citeck.ecos.workflow;

import java.util.List;

public interface EngineWorkflowService {

    void sendSignal(List<String> processes, String signalName);

    String getRootProcessInstanceId(String processId);
}
