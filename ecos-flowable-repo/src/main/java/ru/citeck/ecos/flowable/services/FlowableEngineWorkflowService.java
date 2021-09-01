package ru.citeck.ecos.flowable.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.flowable.common.engine.impl.interceptor.CommandExecutor;
import org.flowable.common.engine.impl.service.CommonEngineServiceImpl;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.flowable.cmd.SendWorkflowSignalCmd;
import ru.citeck.ecos.flowable.constants.FlowableConstants;
import ru.citeck.ecos.workflow.EcosWorkflowService;
import ru.citeck.ecos.workflow.EngineWorkflowService;

import java.util.List;

import static ru.citeck.ecos.flowable.constants.FlowableConstants.ENGINE_PREFIX;

@Slf4j
@Service
public class FlowableEngineWorkflowService implements EngineWorkflowService {

    private CommandExecutor commandExecutor;
    private final FlowableProcessInstanceService flowableProcessInstanceService;

    @Autowired
    public FlowableEngineWorkflowService(RuntimeService runtimeService,
                                         EcosWorkflowService ecosWorkflowService,
                                         FlowableProcessInstanceService flowableProcessInstanceService) {
        if (runtimeService instanceof CommonEngineServiceImpl) {
            commandExecutor = ((CommonEngineServiceImpl) runtimeService).getCommandExecutor();
        }
        ecosWorkflowService.register(FlowableConstants.ENGINE_ID, this);
        this.flowableProcessInstanceService = flowableProcessInstanceService;
    }

    @Override
    public void sendSignal(List<String> processes, String signalName) {
        if (commandExecutor == null) {
            log.error("Command executor is null!");
            return;
        }
        commandExecutor.execute(new SendWorkflowSignalCmd(processes, signalName, false));
    }

    @Override
    public String getRootProcessInstanceId(String processId) {
        if (StringUtils.isBlank(processId)) {
          return processId;
        }
        ProcessInstance procInstanceId = flowableProcessInstanceService.getProcessInstanceById(processId);
        if (procInstanceId != null) {
            String rootProcInstId = procInstanceId.getRootProcessInstanceId();
            if (StringUtils.isNotBlank(rootProcInstId)) {
                return ENGINE_PREFIX + rootProcInstId;
            }
        }
        return ENGINE_PREFIX + processId;
    }

}
