package ru.citeck.ecos.icase.activity.service.eproc;

import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.LoadingCache;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.service.cmr.dictionary.ClassDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.alfresco.util.transaction.TransactionSupportUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.cmmn.model.Definitions;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.commands.dto.CommandResult;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.commons.utils.MandatoryParam;
import ru.citeck.ecos.icase.activity.dto.*;
import ru.citeck.ecos.icase.activity.service.eproc.commands.dto.request.*;
import ru.citeck.ecos.icase.activity.service.eproc.commands.dto.response.*;
import ru.citeck.ecos.icase.activity.service.eproc.importer.parser.CmmnSchemaParser;
import ru.citeck.ecos.icase.activity.service.eproc.importer.pojo.OptimizedProcessDefinition;
import ru.citeck.ecos.icase.activity.service.eproc.importer.pojo.SentrySearchKey;
import ru.citeck.ecos.model.EcosProcessModel;
import ru.citeck.ecos.records.RecordsUtils;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts;
import ru.citeck.ecos.records3.record.atts.schema.ScalarType;
import ru.citeck.ecos.utils.TransactionUtils;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service("eprocActivityService")
public class EProcActivityServiceImpl implements EProcActivityService {

    private static final String CMMN_PROCESS_TYPE = "cmmn";
    private static final String EPROC_TARGET_APP_NAME = "eproc";
    private static final String EPROC_SAVE_STATE_TRANSACTION_KEY = EProcActivityServiceImpl.class.getName() + ".save-state";
    private static final String EPROC_CASE_STATE_BY_ID_KEY_PREFIX = "eproc-case-state-by-id";

    private static final String ATT_PROC_STATE = "_proc";
    private static final String ATT_PROC_STATE_JSON = "_proc?json";
    private static final String ATT_PROC_STATE_STATE_ID = ATT_PROC_STATE + "." + EcosProcessModel.PROP_STATE_ID.getLocalName();

    private final CmmnSchemaParser cmmnSchemaParser;
    private final CommandsService commandsService;
    private final DictionaryService dictionaryService;
    private final NodeService nodeService;
    private final BehaviourFilter behaviourFilter;
    private final RecordsService recordsService;

    private final LoadingCache<EcosAlfTypesKey, Optional<String>> typesToRevisionIdCache;
    private final LoadingCache<String, OptimizedProcessDefinition> revisionIdToProcessDefinitionCache;

    @Autowired
    public EProcActivityServiceImpl(CmmnSchemaParser cmmnSchemaParser,
                                    CommandsService commandsService,
                                    DictionaryService dictionaryService,
                                    NodeService nodeService,
                                    RecordsService recordsService,
                                    @Qualifier("policyBehaviourFilter") BehaviourFilter behaviourFilter) {

        this.commandsService = commandsService;
        this.cmmnSchemaParser = cmmnSchemaParser;
        this.dictionaryService = dictionaryService;
        this.nodeService = nodeService;
        this.recordsService = recordsService;
        this.behaviourFilter = behaviourFilter;

        this.typesToRevisionIdCache = CacheBuilder.newBuilder()
                .maximumSize(250)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build(CacheLoader.from(this::findProcDefRevIdFromMicroservice));

        this.revisionIdToProcessDefinitionCache = CacheBuilder.newBuilder()
                .maximumSize(150)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .build(CacheLoader.from(this::getProcessDefByRevIdFromMicroservice));
    }

    public void resetCache() {
        typesToRevisionIdCache.invalidateAll();
        revisionIdToProcessDefinitionCache.invalidateAll();
    }

    @Override
    public Optional<Pair<String, OptimizedProcessDefinition>> getOptimizedDefinitionWithRevisionId(RecordRef caseRef) {

        return getRevisionIdForNode(caseRef).map(procRevId -> {
            OptimizedProcessDefinition result = revisionIdToProcessDefinitionCache.getUnchecked(procRevId);
            if (result == null) {
                return null;
            }
            return new Pair<>(procRevId, result);
        });
    }

    @Override
    public Optional<ProcessDefinition> getFullDefinition(RecordRef caseRef) {
        return getFullDefinitionImpl(caseRef).map(OptimizedProcessDefinition::getProcessDefinition);
    }

    @Override
    public Optional<Definitions> getXmlProcDefinition(RecordRef caseRef) {
        return getFullDefinitionImpl(caseRef).map(OptimizedProcessDefinition::getXmlProcessDefinition);
    }

    private Optional<OptimizedProcessDefinition> getFullDefinitionImpl(RecordRef caseRef) {

        NodeRef caseNodeRef = RecordsUtils.toNodeRef(caseRef);

        String defRevId;
        String stateId;
        if (caseNodeRef != null) {
            Map<QName, Serializable> props = nodeService.getProperties(caseNodeRef);
            defRevId = (String) props.get(EcosProcessModel.PROP_DEFINITION_REVISION_ID);
            stateId = (String) props.get(EcosProcessModel.PROP_STATE_ID);
        } else {
            DataValue procData = recordsService.getAtt(caseRef, ATT_PROC_STATE_JSON);
            defRevId = procData.get(EcosProcessModel.PROP_DEFINITION_REVISION_ID.getLocalName()).asText();
            stateId = procData.get(EcosProcessModel.PROP_STATE_ID.getLocalName()).asText();
        }

        if (StringUtils.isNotBlank(defRevId)) {
            return Optional.of(getFullDefinitionByRevisionId(defRevId));
        }
        if (StringUtils.isNotBlank(stateId)) {
            return Optional.of(getFullDefinitionForExistingByStateId(stateId));
        }

        return getFullDefinitionForNewCase(caseRef);
    }

    private OptimizedProcessDefinition getFullDefinitionForExistingByStateId(String stateId) {
        GetProcStateResp processState = getProcessStateFromMicroservice(stateId);
        if (processState == null) {
            throw new IllegalArgumentException("Can not find state for stateId=" + stateId);
        }

        String procDefRevId = processState.getProcDefRevId();
        return getFullDefinitionByRevisionId(procDefRevId);
    }

    private Optional<OptimizedProcessDefinition> getFullDefinitionForNewCase(RecordRef caseRef) {
        return getRevisionIdForNode(caseRef)
            .map(this::getFullDefinitionByRevisionId);
    }

    private OptimizedProcessDefinition getFullDefinitionByRevisionId(String processRevisionId) {
        OptimizedProcessDefinition result = revisionIdToProcessDefinitionCache.getUnchecked(processRevisionId);
        if (result == null || result.getProcessDefinition() == null) {
            throw new IllegalArgumentException("Can not find processDef by procDefRevId=" + processRevisionId);
        }
        return result;
    }

    private Optional<String> getRevisionIdForNode(RecordRef caseRef) {
        EcosAlfTypesKey ecosAlfTypesKey = composeEcosAlfTypesKey(caseRef);
        return typesToRevisionIdCache.getUnchecked(ecosAlfTypesKey);
    }

    private EcosAlfTypesKey composeEcosAlfTypesKey(RecordRef caseNodeRef) {

        String typeAtt = RecordConstants.ATT_TYPE + ScalarType.ID.getSchema();
        RecordRef ecosType = RecordRef.valueOf(recordsService.getAtt(caseNodeRef, typeAtt).asText());

        NodeRef nodeRef = RecordsUtils.toNodeRef(caseNodeRef);
        List<String> alfTypes = Collections.emptyList();
        if (nodeRef != null) {
            List<QName> alfQNameTypes = getTypeInheritanceListForCase(nodeRef);
            alfTypes = toString(alfQNameTypes);
        }

        return new EcosAlfTypesKey(ecosType, alfTypes);
    }

    private List<QName> getTypeInheritanceListForCase(NodeRef caseNodeRef) {
        QName type = nodeService.getType(caseNodeRef);

        List<QName> result = new ArrayList<>();

        ClassDefinition typeDef = dictionaryService.getClass(type);
        while (typeDef != null) {
            result.add(typeDef.getName());
            typeDef = typeDef.getParentClassDefinition();
        }

        return result;
    }

    private List<String> toString(List<QName> alfQNames) {
        if (CollectionUtils.isEmpty(alfQNames)) {
            return Collections.emptyList();
        }
        return alfQNames.stream()
                .map(QName::toString)
                .collect(Collectors.toList());
    }

    private Optional<String> findProcDefRevIdFromMicroservice(EcosAlfTypesKey key) {

        FindProcDef findProcDefCommand = new FindProcDef();
        findProcDefCommand.setProcType(CMMN_PROCESS_TYPE);
        findProcDefCommand.setEcosTypeRef(key.getEcosType());
        findProcDefCommand.setAlfTypes(key.getAlfTypes());

        CommandResult commandResult = commandsService.executeSync(findProcDefCommand, EPROC_TARGET_APP_NAME);
        commandResult.throwPrimaryErrorIfNotNull();
        if (CollectionUtils.isNotEmpty(commandResult.getErrors())) {
            throw new RuntimeException("Exception while find of process definition. " +
                    "For detailed information see logs");
        }

        FindProcDefResp response = commandResult.getResultAs(FindProcDefResp.class);
        if (response == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(response.getProcDefRevId());
    }

    private OptimizedProcessDefinition getProcessDefByRevIdFromMicroservice(String definitionRevisionId) {
        GetProcDefRevResp response = getProcessDefByRevIdFromMicroserviceImpl(definitionRevisionId);
        if (response == null) {
            return null;
        }
        return cmmnSchemaParser.parse(response.getData());
    }

    private GetProcDefRevResp getProcessDefByRevIdFromMicroserviceImpl(String definitionRevisionId) {

        GetProcDefRev getProcDefRevCommand = new GetProcDefRev();
        getProcDefRevCommand.setProcType(CMMN_PROCESS_TYPE);
        getProcDefRevCommand.setProcDefRevId(definitionRevisionId);

        CommandResult commandResult = commandsService.executeSync(getProcDefRevCommand, EPROC_TARGET_APP_NAME);
        commandResult.throwPrimaryErrorIfNotNull();
        if (CollectionUtils.isNotEmpty(commandResult.getErrors())) {
            throw new RuntimeException("Exception while receiving of process definition revision. " +
                    "For detailed information see logs");
        }

        return commandResult.getResultAs(GetProcDefRevResp.class);
    }

    @Override
    public ProcessInstance createDefaultState(RecordRef caseRef) {

        EcosAlfTypesKey ecosAlfTypesKey = composeEcosAlfTypesKey(caseRef);
        String definitionRevisionId = typesToRevisionIdCache.getUnchecked(ecosAlfTypesKey)
                .orElseThrow(() -> new IllegalStateException("Def revision ID is null. CaseRef: " + caseRef));

        CreateProcResp createProcResp = createProcessInstanceInMicroservice(definitionRevisionId, caseRef);
        setState(caseRef, createProcResp, definitionRevisionId);

        OptimizedProcessDefinition optimizedProcessDefinition = getFullDefinitionForNewCase(caseRef)
                .orElseThrow(() -> new IllegalStateException("Proc definition is null. CaseRef: " + caseRef));
        ProcessInstance processInstance = createProcessInstanceFromDefinition(createProcResp.getProcId(),
                caseRef, optimizedProcessDefinition.getProcessDefinition());

        putInstanceToTransactionScopeByStateId(caseRef, processInstance);

        return processInstance;
    }

    @Override
    public ProcessInstance createDefaultState(RecordRef caseRef, String revisionId,
                                              OptimizedProcessDefinition optimizedProcessDefinition) {

        CreateProcResp createProcResp = createProcessInstanceInMicroservice(revisionId, caseRef);
        setState(caseRef, createProcResp, revisionId);

        ProcessInstance processInstance = createProcessInstanceFromDefinition(
            createProcResp.getProcId(),
            caseRef,
            optimizedProcessDefinition.getProcessDefinition()
        );

        putInstanceToTransactionScopeByStateId(caseRef, processInstance);

        return processInstance;
    }

    private void setState(RecordRef caseRef, CreateProcResp data, String defRevId) {

        NodeRef caseNodeRef = RecordsUtils.toNodeRef(caseRef);

        if (caseNodeRef != null) {
            nodeService.setProperty(caseNodeRef, EcosProcessModel.PROP_PROCESS_ID, data.getProcId());
            nodeService.setProperty(caseNodeRef, EcosProcessModel.PROP_STATE_ID, data.getProcStateId());
            nodeService.setProperty(caseNodeRef, EcosProcessModel.PROP_DEFINITION_REVISION_ID, defRevId);
        } else {
            Map<String, Object> stateProps = new HashMap<>();
            stateProps.put(EcosProcessModel.PROP_PROCESS_ID.getLocalName(), data.getProcId());
            stateProps.put(EcosProcessModel.PROP_STATE_ID.getLocalName(), data.getProcStateId());
            stateProps.put(EcosProcessModel.PROP_DEFINITION_REVISION_ID.getLocalName(), defRevId);
            recordsService.mutateAtt(caseRef, ATT_PROC_STATE, stateProps);
        }
    }

    private CreateProcResp createProcessInstanceInMicroservice(String definitionRevisionId, RecordRef caseRef) {
        CreateProc createProc = new CreateProc();
        createProc.setProcDefRevId(definitionRevisionId);
        createProc.setRecordRef(caseRef);

        CommandResult commandResult = commandsService.executeSync(createProc, EPROC_TARGET_APP_NAME);
        commandResult.throwPrimaryErrorIfNotNull();
        if (CollectionUtils.isNotEmpty(commandResult.getErrors())) {
            throw new RuntimeException("Exception while creation of process state. For detailed information see logs");
        }
        return commandResult.getResultAs(CreateProcResp.class);
    }

    private ProcessInstance createProcessInstanceFromDefinition(String procId,
                                                                RecordRef caseRef,
                                                                ProcessDefinition definition) {

        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setId(procId);
        processInstance.setCaseRef(caseRef);
        processInstance.setDefinition(definition);
        processInstance.setRootActivity(createActivityInstanceFromDefinition(null, definition.getActivityDefinition()));
        return processInstance;
    }

    private ActivityInstance createActivityInstanceFromDefinition(ActivityInstance parentActivity,
                                                                  ActivityDefinition activityDefinition) {
        ActivityInstance currentInstance = new ActivityInstance();
        currentInstance.setId(activityDefinition.getId());
        currentInstance.setDefinition(activityDefinition);
        currentInstance.setState(ActivityState.NOT_STARTED);
        currentInstance.setActivated(null);
        currentInstance.setTerminated(null);

        if (CollectionUtils.isEmpty(activityDefinition.getActivities())) {
            currentInstance.setActivities(Collections.emptyList());
        } else {
            List<ActivityInstance> childActivities = new ArrayList<>(activityDefinition.getActivities().size());
            for (ActivityDefinition childDefinition : activityDefinition.getActivities()) {
                ActivityInstance childActivity = createActivityInstanceFromDefinition(currentInstance, childDefinition);
                childActivities.add(childActivity);
            }
            currentInstance.setActivities(childActivities);
        }

        currentInstance.setVariables(null);
        currentInstance.setParentInstance(parentActivity);
        return currentInstance;
    }

    @Override
    public Optional<ProcessInstance> getFullState(RecordRef caseRef) {

        NodeRef caseNodeRef = RecordsUtils.toNodeRef(caseRef);
        String stateId;
        String procId;
        if (caseNodeRef != null) {
            stateId = (String) nodeService.getProperty(caseNodeRef, EcosProcessModel.PROP_STATE_ID);
            procId = (String) nodeService.getProperty(caseNodeRef, EcosProcessModel.PROP_PROCESS_ID);
        } else {
            DataValue procData = recordsService.getAtt(caseRef, ATT_PROC_STATE_JSON);
            stateId = procData.get(EcosProcessModel.PROP_STATE_ID.getLocalName()).asText();
            procId = procData.get(EcosProcessModel.PROP_PROCESS_ID.getLocalName()).asText();
        }

        if (StringUtils.isBlank(stateId) || StringUtils.isBlank(procId)) {
            return Optional.empty();
        }

        ProcessInstance transactionProcessInstance = getProcessStateFromTransactionByStateId(caseRef);
        if (transactionProcessInstance != null) {
            return Optional.of(transactionProcessInstance);
        }

        Optional<OptimizedProcessDefinition> optOptimizedProcessDefinition = getFullDefinitionImpl(caseRef);
        if (!optOptimizedProcessDefinition.isPresent()) {
            return Optional.empty();
        }
        OptimizedProcessDefinition optimizedProcessDefinition = optOptimizedProcessDefinition.get();

        GetProcStateResp processState = getProcessStateFromMicroservice(stateId);
        byte[] stateData = processState.getStateData();

        ProcessInstance instance;
        if (stateData == null || stateData.length == 0) {
            ProcessDefinition processDefinition = optimizedProcessDefinition.getProcessDefinition();
            instance = createProcessInstanceFromDefinition(procId, caseRef, processDefinition);
        } else {
            instance = Json.getMapper().read(stateData, ProcessInstance.class);

            if (instance == null) {
                throw new RuntimeException("Can not parse state from microservice for caseRef=" + caseRef);
            }

            setUnSerializableObjectsInProcessInstance(instance, optimizedProcessDefinition);
        }

        putInstanceToTransactionScopeByStateId(caseRef, instance);

        return Optional.of(instance);
    }

    private GetProcStateResp getProcessStateFromMicroservice(String stateId) {

        GetProcState getProcStateCommand = new GetProcState();
        getProcStateCommand.setProcType(CMMN_PROCESS_TYPE);
        getProcStateCommand.setProcStateId(stateId);

        CommandResult commandResult = commandsService.executeSync(getProcStateCommand, EPROC_TARGET_APP_NAME);
        commandResult.throwPrimaryErrorIfNotNull();
        if (CollectionUtils.isNotEmpty(commandResult.getErrors())) {
            throw new RuntimeException("Exception while receiving of process state. For detailed information see logs");
        }
        return commandResult.getResultAs(GetProcStateResp.class);
    }

    private void setUnSerializableObjectsInProcessInstance(ProcessInstance processInstance,
                                                           OptimizedProcessDefinition optimizedProcessDefinition) {

        processInstance.setDefinition(optimizedProcessDefinition.getProcessDefinition());

        setUnSerializableObjectsInActivityInstance(null, processInstance.getRootActivity(), optimizedProcessDefinition);
    }

    private void setUnSerializableObjectsInActivityInstance(ActivityInstance parentInstance,
                                                            ActivityInstance activityInstance,
                                                            OptimizedProcessDefinition optimizedProcessDefinition) {

        activityInstance.setParentInstance(parentInstance);

        ActivityDefinition activityDefinition = findDefinitionById(optimizedProcessDefinition, activityInstance.getId());
        if (activityDefinition == null) {
            throw new RuntimeException("Can not find definition by id=" + activityInstance.getId());
        }
        activityInstance.setDefinition(activityDefinition);

        if (activityInstance.getActivities() != null) {
            for (ActivityInstance childInstance : activityInstance.getActivities()) {
                setUnSerializableObjectsInActivityInstance(activityInstance, childInstance, optimizedProcessDefinition);
            }
        }
    }

    private ActivityDefinition findDefinitionById(OptimizedProcessDefinition optimizedProcessDefinition, String id) {
        return optimizedProcessDefinition.getIdToActivityCache().get(id);
    }

    private ProcessInstance getProcessStateFromTransactionByStateId(RecordRef caseRef) {
        String key = getProcessStateTransactionKey(caseRef);
        return TransactionSupportUtil.getResource(key);
    }

    private void putInstanceToTransactionScopeByStateId(RecordRef caseRef, ProcessInstance instance) {
        String key = getProcessStateTransactionKey(caseRef);
        ProcessInstance current = TransactionSupportUtil.getResource(key);
        if (current != null) {
            if (!Objects.equals(instance, current)) {
                throw new RuntimeException("For case='" + caseRef + "' already saved another process instance.\n" +
                        "Existing instance: " + current + ".\n" +
                        "New instance: " + instance + "." +
                        "Perhaps out of sync!");
            }
            return;
        }

        TransactionSupportUtil.bindResource(key, instance);
    }

    private String getProcessStateTransactionKey(RecordRef caseRef) {
        return this.getClass().getName()
                + "."
                + EPROC_CASE_STATE_BY_ID_KEY_PREFIX
                + "."
                + caseRef;
    }

    @Override
    public void saveState(RecordRef caseRef) {
        TransactionUtils.processAfterBehaviours(
                EPROC_SAVE_STATE_TRANSACTION_KEY,
                caseRef,
                this::saveStateImpl);
    }

    private void saveStateImpl(RecordRef caseRef) {

        MandatoryParam.check("caseRef", caseRef);

        NodeRef caseNodeRef = RecordsUtils.toNodeRef(caseRef);

        String prevStateId;
        if (caseNodeRef != null) {
            prevStateId = (String) nodeService.getProperty(caseNodeRef, EcosProcessModel.PROP_STATE_ID);
        } else {
            prevStateId = recordsService.getAtt(caseRef, ATT_PROC_STATE_STATE_ID).asText();
        }

        ProcessInstance processInstance = getFullState(caseRef)
                .orElseThrow(() -> new IllegalStateException("State is not found for case: " + caseRef));

        UpdateProcStateResp result = updateStateInMicroservice(prevStateId, processInstance);
        if (result == null || StringUtils.isBlank(result.getProcStateId())) {
            throw new RuntimeException("Error while state saving");
        }

        if (caseNodeRef != null) {
            behaviourFilter.disableBehaviour(caseNodeRef);
            try {
                nodeService.setProperty(caseNodeRef, EcosProcessModel.PROP_STATE_ID, result.getProcStateId());
            } finally {
                behaviourFilter.enableBehaviour(caseNodeRef);
            }
        } else {
            DataValue state = recordsService.getAtt(caseRef, ATT_PROC_STATE_JSON);
            state.set(EcosProcessModel.PROP_STATE_ID.getLocalName(), result.getProcStateId());
            recordsService.mutateAtt(caseRef, ATT_PROC_STATE, state);
        }
    }

    private UpdateProcStateResp updateStateInMicroservice(String prevStateId, ProcessInstance processInstance) {
        UpdateProcState updateProcState = new UpdateProcState();
        updateProcState.setPrevProcStateId(prevStateId);
        byte[] stateData = Json.getMapper().toBytes(processInstance);
        if (stateData == null) {
            throw new IllegalArgumentException("Can not parse processInstance to bytes. " +
                    "For detailed information see logs");
        }
        updateProcState.setStateData(stateData);

        CommandResult commandResult = commandsService.executeSync(updateProcState, EPROC_TARGET_APP_NAME);
        commandResult.throwPrimaryErrorIfNotNull();
        if (CollectionUtils.isNotEmpty(commandResult.getErrors())) {
            throw new RuntimeException("Exception while state updating. For detailed information see logs");
        }
        return commandResult.getResultAs(UpdateProcStateResp.class);
    }

    @Override
    public ActivityInstance getStateInstance(ActivityRef activityRef) {
        ProcessInstance instance = getFullState(activityRef.getProcessId())
                .orElseThrow(() -> new IllegalStateException("Process instance is not found for activity: " + activityRef));
        if (activityRef.isRoot()) {
            return instance.getRootActivity();
        }
        return getStateInstanceRecursively(instance.getRootActivity(), activityRef.getId());
    }

    private ActivityInstance getStateInstanceRecursively(ActivityInstance instance, String id) {
        if (StringUtils.equals(instance.getId(), id)) {
            return instance;
        }

        if (CollectionUtils.isEmpty(instance.getActivities())) {
            return null;
        }

        for (ActivityInstance childInstance : instance.getActivities()) {
            ActivityInstance stateInstance = getStateInstanceRecursively(childInstance, id);
            if (stateInstance != null) {
                return stateInstance;
            }
        }
        return null;
    }

    @Override
    public ActivityDefinition getActivityDefinition(ActivityRef activityRef) {
        return getFullDefinitionImpl(activityRef.getProcessId())
                .map(OptimizedProcessDefinition::getIdToActivityCache)
                .map(cache -> cache.get(activityRef.getId()))
                .orElseThrow(() -> new IllegalStateException("Activity def is not found: " + activityRef));
    }

    @Override
    @NotNull
    public SentryDefinition getSentryDefinition(EventRef eventRef) {
        return getFullDefinitionImpl(eventRef.getProcessId())
                .map(OptimizedProcessDefinition::getIdToSentryCache)
                .map(cache -> cache.get(eventRef.getId()))
                .orElseThrow(() -> new IllegalStateException("Sentry is not found: " + eventRef));
    }

    @Override
    public List<SentryDefinition> findSentriesBySourceRefAndEventType(RecordRef caseRef,
                                                                      String sourceRef,
                                                                      String eventType) {

        OptimizedProcessDefinition optimizedProcessDefinition = getFullDefinitionImpl(caseRef)
                .orElseThrow(() -> new IllegalStateException("Process definition is not found!. "
                        + "CaseRef: " + caseRef
                        + " sourceRef: " + sourceRef
                        + " eventType: " + eventType));

        SourceRef sourceRefObj = new SourceRef();
        sourceRefObj.setRef(sourceRef);
        SentrySearchKey sentrySearchKey = new SentrySearchKey(sourceRefObj, eventType);
        return optimizedProcessDefinition.getSentrySearchCache().get(sentrySearchKey);
    }

    @Data
    private static class EcosAlfTypesKey {
        private final RecordRef ecosType;
        private final List<String> alfTypes;
    }
}
