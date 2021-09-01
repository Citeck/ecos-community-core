package ru.citeck.ecos.flowable.services;

import org.apache.commons.lang.StringUtils;
import org.flowable.common.engine.impl.interceptor.CommandExecutor;
import org.flowable.common.engine.impl.service.CommonEngineServiceImpl;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.flowable.cmd.SendWorkflowSignalCmd;
import ru.citeck.ecos.flowable.constants.FlowableConstants;
import ru.citeck.ecos.workflow.EcosWorkflowService;
import ru.citeck.ecos.workflow.EngineWorkflowService;

import java.util.List;

import static ru.citeck.ecos.flowable.constants.FlowableConstants.ENGINE_PREFIX;

@Service
public class FlowableEngineWorkflowService implements EngineWorkflowService {

    public static final Logger logger = LoggerFactory.getLogger(FlowableEngineWorkflowService.class);

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
            logger.error("Command executor is null!");
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
