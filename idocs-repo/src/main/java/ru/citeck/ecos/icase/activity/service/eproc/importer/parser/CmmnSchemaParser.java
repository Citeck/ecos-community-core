package ru.citeck.ecos.icase.activity.service.eproc.importer.parser;

import com.google.common.collect.Sets;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.cmmn.CMMNUtils;
import ru.citeck.ecos.cmmn.condition.Condition;
import ru.citeck.ecos.cmmn.condition.ConditionProperty;
import ru.citeck.ecos.cmmn.condition.ConditionsList;
import ru.citeck.ecos.cmmn.model.*;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.content.dao.xml.XmlContentDAO;
import ru.citeck.ecos.icase.activity.dto.*;
import ru.citeck.ecos.icase.activity.service.eproc.importer.pojo.OptimizedProcessDefinition;
import ru.citeck.ecos.icase.activity.service.eproc.importer.pojo.SentrySearchKey;
import ru.citeck.ecos.model.ActionModel;
import ru.citeck.ecos.model.ActivityModel;
import ru.citeck.ecos.model.ICaseEventModel;
import ru.citeck.ecos.model.ICaseTaskModel;
import ru.citeck.ecos.role.CaseRoleService;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CmmnSchemaParser {

    private static final String USER_ACTION_EVENT_NAME = "user-action";

    private static final Pattern qnamePattern = Pattern.compile("^\\{http.*\\}.*$");

    private XmlContentDAO<Definitions> xmlContentDAO;
    private DictionaryService dictionaryService;
    private NamespaceService namespaceService;
    private CMMNUtils utils;

    // Parsing execution thread cache
    private ThreadLocal<Map<String, ActivityDefinition>> idToActivityCache = new ThreadLocal<>();
    private ThreadLocal<Map<String, SentryDefinition>> idToSentryCache = new ThreadLocal<>();
    private ThreadLocal<Map<SentrySearchKey, List<SentryDefinition>>> searchKeyToSentryCache = new ThreadLocal<>();
    private ThreadLocal<AtomicInteger> triggerIdCounter = new ThreadLocal<>();
    private ThreadLocal<AtomicInteger> evaluatorIdCounter = new ThreadLocal<>();
    private ThreadLocal<AtomicInteger> activityIndexCounter = new ThreadLocal<>();
    private ThreadLocal<Map<String, String>> idToVarNameRoleCache = new ThreadLocal<>();
    private ThreadLocal<Map<String, CompletenessLevels>> idToCompletenessLevelsCache = new ThreadLocal<>();
    private ThreadLocal<Map<String, TUserEventListener>> idToUserEventListenerCache = new ThreadLocal<>();
    private ThreadLocal<Map<String, Set<ActivityDefinition>>> roleVarNameToTaskDefinitionCache = new ThreadLocal<>();

    @Autowired
    public CmmnSchemaParser(@Qualifier("caseTemplateContentDAO") XmlContentDAO<Definitions> xmlContentDAO,
                            DictionaryService dictionaryService,
                            NamespaceService namespaceService,
                            CMMNUtils utils) {

        this.xmlContentDAO = xmlContentDAO;
        this.dictionaryService = dictionaryService;
        this.namespaceService = namespaceService;
        this.utils = utils;
    }

    public OptimizedProcessDefinition parse(byte[] source) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(source)) {
            Definitions definitions = xmlContentDAO.read(stream);
            return parse(definitions);
        } catch (IOException e) {
            throw new RuntimeException("Could not parse definition", e);
        }
    }

    private OptimizedProcessDefinition parse(Definitions jaxbDefinitions) {
        try {
            if (jaxbDefinitions == null || CollectionUtils.isEmpty(jaxbDefinitions.getCase())) {
                return null;
            }

            Case caseItem = jaxbDefinitions.getCase().get(0);

            ProcessDefinition definition = new ProcessDefinition();
            definition.setId(caseItem.getId());
            definition.setRoles(parseRoleDefinitions(caseItem));
            definition.setActivityDefinition(parseRootActivityDefinition(caseItem));
            return composeOptimized(definition, jaxbDefinitions);
        } finally {
            clearParsingExecutionCache();
        }
    }

    private OptimizedProcessDefinition composeOptimized(ProcessDefinition definition, Definitions jaxbDefinitions) {
        OptimizedProcessDefinition optimizedProcessDefinition = new OptimizedProcessDefinition();
        optimizedProcessDefinition.setXmlProcessDefinition(jaxbDefinitions);
        optimizedProcessDefinition.setProcessDefinition(definition);
        optimizedProcessDefinition.setIdToActivityCache(copy(idToActivityCache.get()));
        optimizedProcessDefinition.setIdToSentryCache(copy(idToSentryCache.get()));
        optimizedProcessDefinition.setSentrySearchCache(copy(searchKeyToSentryCache.get()));
        optimizedProcessDefinition.setRoleVarNameToTaskDefinitionCache(copy(roleVarNameToTaskDefinitionCache.get()));
        return optimizedProcessDefinition;
    }

    private <K, V> Map<K, V> copy(Map<K, V> source) {
        if (source == null) {
            return Collections.emptyMap();
        } else {
            return new HashMap<>(source);
        }
    }

    private ActivityDefinition parseRootActivityDefinition(Case caseItem) {
        ActivityDefinition activityDefinition = newCommonActivityDefinition(caseItem, ActivityType.ROOT);
        activityDefinition.setData(parseCommonElementData(caseItem));
        activityDefinition.setActivities(parseChildActivityDefinitions(caseItem.getCasePlanModel()));
        return activityDefinition;
    }

    private List<ActivityDefinition> parseChildActivityDefinitions(Stage stage) {
        List<JAXBElement<? extends TPlanItemDefinition>> planItemDefinitionList = stage.getPlanItemDefinition();
        if (CollectionUtils.isEmpty(planItemDefinitionList)) {
            return Collections.emptyList();
        }

        List<ActivityDefinition> result = new ArrayList<>(planItemDefinitionList.size());

        for (JAXBElement<? extends TPlanItemDefinition> jaxbPlanItemDefinition : planItemDefinitionList) {
            TPlanItemDefinition planItemDefinition = jaxbPlanItemDefinition.getValue();
            if (utils.isTask(jaxbPlanItemDefinition) || utils.isProcessTask(jaxbPlanItemDefinition)) {
                if (isUserTask((TTask) planItemDefinition)) {
                    result.add(parseUserTask((TTask) planItemDefinition));
                } else if (isAction((TTask) planItemDefinition)) {
                    result.add(parseAction((TTask) planItemDefinition));
                } else {
                    log.warn("Could not parse unknown task: " + ReflectionToStringBuilder.toString(planItemDefinition));
                }
            } else if (utils.isTimer(jaxbPlanItemDefinition)) {
                result.add(parseTimer((TTimerEventListener) planItemDefinition));
            } else if (utils.isUserEventListener(jaxbPlanItemDefinition)) {
                result.add(parseUserAction((TUserEventListener) planItemDefinition));
            } else if (utils.isStage(jaxbPlanItemDefinition)) {
                result.add(parseStage((Stage) planItemDefinition));
            } else {
                log.warn("Could not parse unknown item: " + ReflectionToStringBuilder.toString(planItemDefinition));
            }
        }

        parseAndAddTransitions(stage);

        return result;
    }

    private ActivityDefinition parseStage(Stage stage) {
        ActivityDefinition activityDefinition = newCommonActivityDefinition(stage, ActivityType.STAGE);
        activityDefinition.setData(parseStageDefinitionData(stage));
        activityDefinition.setActivities(parseChildActivityDefinitions(stage));

        parseAndAddTransitions(stage);

        return activityDefinition;
    }

    private ObjectData parseStageDefinitionData(Stage stage) {
        ObjectData objectData = parseCommonDefinitionData(stage);
        addAttributeIfExists(stage, CMMNUtils.QNAME_CASE_STATUS, objectData);
        return objectData;
    }

    private boolean isAction(TTask item) {
        QName nodeType = getNodeType(item);
        return dictionaryService.isSubClass(nodeType, ActionModel.TYPE_ACTION);
    }

    private ActivityDefinition parseAction(TTask action) {
        ActivityDefinition activityDefinition = newCommonActivityDefinition(action, ActivityType.ACTION);
        activityDefinition.setData(parseActionDefinitionData(action));
        activityDefinition.setActivities(Collections.emptyList());
        return activityDefinition;
    }

    private ObjectData parseActionDefinitionData(TTask action) {
        ObjectData objectData = parseCommonDefinitionData(action);
        addAttributeIfExists(action, CMMNUtils.QNAME_ACTION_CASE_STATUS, objectData);
        addAttributeIfExists(CmmnDefinitionConstants.ACTION_TYPE, getActionType(action), objectData);
        return objectData;
    }

    private String getActionType(TTask action) {
        QName nodeType = getNodeType(action);
        if (nodeType == null) {
            return null;
        }
        return nodeType.getLocalName();
    }

    private boolean isUserTask(TTask item) {
        QName nodeType = getNodeType(item);
        return dictionaryService.isSubClass(nodeType, ICaseTaskModel.TYPE_TASK);
    }

    private ActivityDefinition parseUserTask(TTask task) {
        ActivityDefinition activityDefinition = newCommonActivityDefinition(task, ActivityType.USER_TASK);
        activityDefinition.setData(parseTaskDefinitionData(task));
        activityDefinition.setActivities(Collections.emptyList());

        String[] roleVarNames = activityDefinition.getData().get(
                CmmnDefinitionConstants.TASK_ROLE_VAR_NAMES_SET_KEY, String[].class);
        addRoleVarNameToTaskDefinitionCache(roleVarNames, activityDefinition);

        return activityDefinition;
    }

    private ObjectData parseTaskDefinitionData(TTask task) {
        ObjectData objectData = parseCommonDefinitionData(task);
        addRoles(task, objectData);
        return objectData;
    }

    private ActivityDefinition parseUserAction(TUserEventListener userEventListener) {
        ActivityDefinition definition = newCommonActivityDefinition(userEventListener, ActivityType.USER_EVENT_LISTENER);
        definition.setRepeatable(true);
        definition.setData(parseUserActionData(userEventListener));
        definition.setActivities(Collections.emptyList());
        return definition;
    }

    private ObjectData parseUserActionData(TUserEventListener userEventListener) {
        ObjectData objectData = parseCommonDefinitionData(userEventListener);
        addUserActionAuthorizedRoles(userEventListener, objectData);
        return objectData;
    }

    private void addUserActionAuthorizedRoles(TUserEventListener userEventListener, ObjectData objectData) {
        List<Object> authorizedRoles = userEventListener.getAuthorizedRoleRefs();

        Set<String> authorizedRoleVarNames = new HashSet<>(authorizedRoles.size());
        for (Object role : authorizedRoles) {
            String roleVarName = getRoleVarName((Role) role);
            authorizedRoleVarNames.add(roleVarName.trim());
        }

        objectData.set(CmmnDefinitionConstants.AUTHORIZED_ROLE_VAR_NAMES_SET, authorizedRoleVarNames);
    }

    private ActivityDefinition parseTimer(TTimerEventListener timer) {
        ActivityDefinition activityDefinition = newCommonActivityDefinition(timer, ActivityType.TIMER);
        activityDefinition.setData(parseCommonDefinitionData(timer));
        activityDefinition.setActivities(Collections.emptyList());
        return activityDefinition;
    }

    private ActivityDefinition newCommonActivityDefinition(TCmmnElement element, ActivityType activityType) {
        ActivityDefinition activityDefinition = new ActivityDefinition();
        if (activityType == ActivityType.ROOT) {
            activityDefinition.setId(ActivityRef.ROOT_ID);
            addActivityDefinitionToIdentityCache(element.getId(), activityDefinition);
            addActivityDefinitionToIdentityCache(ActivityRef.ROOT_ID, activityDefinition);
        } else {
            activityDefinition.setId(element.getId());
            addActivityDefinitionToIdentityCache(element.getId(), activityDefinition);
        }
        activityDefinition.setType(activityType);
        activityDefinition.setIndex(getNextIndex());
        activityDefinition.setRepeatable(isRepeatable(element));

        addCompletenessLevelsIfExists(element);

        return activityDefinition;
    }

    private String toValidCompletenessLevelRef(String rawCompletenessLevelRef) {
        return rawCompletenessLevelRef.replaceAll("workspace-SpacesStore-", "workspace://SpacesStore/");
    }

    private boolean isRepeatable(TCmmnElement element) {
        Map<javax.xml.namespace.QName, String> otherAttributes = element.getOtherAttributes();
        javax.xml.namespace.QName qName = utils.convertToXMLQName(ActivityModel.PROP_REPEATABLE);
        return MapUtils.getBoolean(otherAttributes, qName, false);
    }

    private ObjectData parseCommonDefinitionData(TPlanItemDefinition definition) {
        ObjectData data = parseCommonElementData(definition);
        data.set(CmmnDefinitionConstants.NAME, definition.getName());
        return data;
    }

    private ObjectData parseCommonElementData(TCmmnElement element) {
        Map<javax.xml.namespace.QName, String> otherAttributes = element.getOtherAttributes();

        ObjectData data = ObjectData.create();
        for (Map.Entry<javax.xml.namespace.QName, String> otherAttribute : otherAttributes.entrySet()) {
            javax.xml.namespace.QName key = otherAttribute.getKey();
            if (!key.getNamespaceURI().equals(CMMNUtils.NAMESPACE)) {
                String value = otherAttribute.getValue();
                data.set(key.getLocalPart(), convertAttributeValue(value));
            }
        }

        data.set(CmmnDefinitionConstants.TITLE, otherAttributes.get(CMMNUtils.QNAME_TITLE));

        return data;
    }

    private String convertAttributeValue(String value) {
        try {
            if (isFullQName(value)) {
                return consumeValueAsShortQNameString(value);
            }
        } catch (Exception ignored) { // will be logged in ObjectData's json mapper
        }
        return value;
    }

    private boolean isFullQName(String value) {
        Matcher matcher = qnamePattern.matcher(value);
        return matcher.matches();
    }

    private String consumeValueAsShortQNameString(String value) {
        QName qname = QName.resolveToQName(namespaceService, value);
        return qname.toPrefixString(namespaceService);
    }

    private void addAttributeIfExists(TCmmnElement element, javax.xml.namespace.QName qname, ObjectData objectData) {
        Map<javax.xml.namespace.QName, String> otherAttributes = element.getOtherAttributes();
        String value = otherAttributes.get(qname);
        addAttributeIfExists(qname.getLocalPart(), value, objectData);
    }

    private void addAttributeIfExists(String name, String value, ObjectData objectData) {
        if (StringUtils.isNotBlank(value)) {
            objectData.set(name, value);
        }
    }

    private QName getNodeType(TCmmnElement element) {
        String rawQName = element.getOtherAttributes().get(CMMNUtils.QNAME_NODE_TYPE);
        if (StringUtils.isEmpty(rawQName)) {
            return null;
        }

        return QName.createQName(rawQName);
    }

    // Completeness Levels parsing logic area
    private void addCompletenessLevelsIfExists(TCmmnElement element) {
        String startCompleteness = parseCompletenessLevels(element, CMMNUtils.QNAME_START_COMPLETNESS_LEVELS);
        String stopCompleteness = parseCompletenessLevels(element, CMMNUtils.QNAME_STOP_COMPLETNESS_LEVELS);

        if (StringUtils.isNotBlank(startCompleteness) || StringUtils.isNotBlank(stopCompleteness)) {
            CompletenessLevels levels = new CompletenessLevels();
            levels.startCompletenessLevels = startCompleteness;
            levels.stopCompletenessLevels = stopCompleteness;
            addCompletenessLevelsCache(element.getId(), levels);
        }
    }

    private String parseCompletenessLevels(TCmmnElement definition, javax.xml.namespace.QName xmlQName) {
        String rawCompletenessLevels = definition.getOtherAttributes().get(xmlQName);
        if (StringUtils.isNotBlank(rawCompletenessLevels)) {
            String[] splitCompletenessLevels = rawCompletenessLevels.split(",");
            Set<String> formattedCompletenessLevels = Arrays.stream(splitCompletenessLevels)
                    .map(this::toValidCompletenessLevelRef)
                    .map(String::trim)
                    .collect(Collectors.toSet());
            return String.join(",", formattedCompletenessLevels);
        }
        return null;
    }

    // Roles parsing logic area
    private void addRoles(TTask task, ObjectData objectData) {
        Set<String> roleIds = getTaskRoleIds(task);
        Set<String> roleVarNames = convertRoleIdsToVarNames(roleIds);
        objectData.set(CmmnDefinitionConstants.TASK_ROLE_VAR_NAMES_SET_KEY, roleVarNames);
    }

    private Set<String> getTaskRoleIds(TTask task) {

        Set<String> taskRoleIds = new HashSet<>();

        Map<String, String> propsByLocalName = new HashMap<>();
        task.getOtherAttributes().forEach((k, v) -> propsByLocalName.put(k.getLocalPart(), v));

        for (Map.Entry<javax.xml.namespace.QName, QName> entry : CMMNUtils.ROLES_ASSOCS_MAPPING.entrySet()) {
            String value = task.getOtherAttributes().get(entry.getKey());
            if (value != null && !value.isEmpty()) {
                String[] rolesArray = value.split(",");
                taskRoleIds.addAll(Arrays.asList(rolesArray));
            }
            value = propsByLocalName.get(entry.getKey().getLocalPart() + "-prop");
            if (StringUtils.isNotBlank(value) && value.length() > 2 && value.charAt(0) == '[') {
                DataValue dataValue = Json.getMapper().read(value, DataValue.class);
                if (dataValue != null && dataValue.isNotNull()) {
                    taskRoleIds.addAll(dataValue.asList(String.class));
                }
            }
        }
        return taskRoleIds;
    }

    private Set<String> convertRoleIdsToVarNames(Set<String> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return Collections.emptySet();
        }

        Set<String> roleVarNames = new HashSet<>();
        for (String roleId : roleIds) {
            if (roleId.startsWith(CaseRoleService.ROLE_REF_PROTOCOL + "://")) {
                int roleIdDelimIdx = roleId.lastIndexOf('/') + 1;
                if (roleIdDelimIdx < roleId.length()) {
                    roleVarNames.add(roleId.substring(roleIdDelimIdx));
                }
                continue;
            }
            String roleVarName = getRoleVarNameByIdFromThreadCache(roleId);
            if (StringUtils.isBlank(roleVarName)) {
                log.warn("RoleVarName not found for id " + roleId);
                continue;
            }
            roleVarNames.add(roleVarName);
        }
        return roleVarNames;
    }

    private List<RoleReference> parseRoleDefinitions(Case caseItem) {
        CaseRoles caseRoles = caseItem.getCaseRoles();
        if (caseRoles == null) {
            return Collections.emptyList();
        }

        List<Role> roles = caseRoles.getRole();
        if (CollectionUtils.isEmpty(roles)) {
            return Collections.emptyList();
        }

        List<RoleReference> result = new ArrayList<>(roles.size());
        for (Role role : roles) {
            RoleReference roleReference = new RoleReference();
            roleReference.setId(role.getId());
            roleReference.setVarName(getRoleVarName(role));
            addRoleReferenceToThreadCache(roleReference);
            result.add(roleReference);
        }

        return result;
    }

    private String getRoleVarName(Role role) {
        Map<javax.xml.namespace.QName, String> otherAttributes = role.getOtherAttributes();
        return otherAttributes.get(CMMNUtils.QNAME_ROLE_VARNAME);
    }

    // Transitions parsing logic area
    private void parseAndAddTransitions(Stage stage) {
        for (TPlanItem planItem : stage.getPlanItem()) {
            TPlanItemDefinition planItemDefinition = (TPlanItemDefinition) planItem.getDefinitionRef();
            String id = planItemDefinition.getId();
            ActivityDefinition activityDefinition = getCachedActivityDefinitionById(id);
            if (activityDefinition == null) {
                log.warn("Activity definition was not fount by id=" + id + ". Events has no been parsed!");
                continue;
            }

            log.debug("Importing events for " + planItem.getId());
            Map<String, Sentry> stageSentries = getSentryIdToInstanceMap(stage);

            getOrCreateTransitionByState(activityDefinition, ActivityState.NOT_STARTED, ActivityState.STARTED, false);
            getOrCreateTransitionByState(activityDefinition, ActivityState.NOT_STARTED, ActivityState.STARTED, true);
            getOrCreateTransitionByState(activityDefinition, ActivityState.STARTED, ActivityState.COMPLETED, false);

            for (Sentry sentry : getEntrySentries(planItem, stageSentries)) {
                addTransitionImpl(activityDefinition, sentry, ActivityState.NOT_STARTED, ActivityState.STARTED);
            }
            for (Sentry sentry : getExitSentries(planItem, stageSentries)) {
                addTransitionImpl(activityDefinition, sentry, ActivityState.STARTED, ActivityState.COMPLETED);
            }
        }
    }

    private List<Sentry> getEntrySentries(TPlanItem planItem, Map<String, Sentry> stageSentries) {
        List<Sentry> result = new ArrayList<>(utils.criterionToSentries(planItem.getEntryCriterion()));
        String sentriesStr = planItem.getOtherAttributes().get(CMMNUtils.QNAME_ENTRY_SENTRY);
        result.addAll(utils.stringToElements(sentriesStr, stageSentries));
        return result;
    }

    private List<Sentry> getExitSentries(TPlanItem planItem, Map<String, Sentry> stageSentries) {
        List<Sentry> result = new ArrayList<>(utils.criterionToSentries(planItem.getExitCriterion()));
        String sentriesStr = planItem.getOtherAttributes().get(CMMNUtils.QNAME_EXIT_SENTRY);
        result.addAll(utils.stringToElements(sentriesStr, stageSentries));
        return result;
    }

    private Map<String, Sentry> getSentryIdToInstanceMap(Stage stage) {
        Map<String, Sentry> stageSentries = new HashMap<>();
        for (Sentry sentry : stage.getSentry()) {
            stageSentries.put(sentry.getId(), sentry);
        }
        return stageSentries;
    }

    private boolean isUserEventListenerSentry(Sentry sentry) {
        Map<javax.xml.namespace.QName, String> otherAttributes = sentry.getOtherAttributes();
        if (MapUtils.isEmpty(otherAttributes)) {
            return false;
        }

        return USER_ACTION_EVENT_NAME.equals(otherAttributes.get(CMMNUtils.QNAME_ORIGINAL_EVENT));
    }

    private ActivityDefinition findUserEventListenerDefinition(Sentry sentry) {
        TUserEventListener userEventListenerElement = searchUserEventListenerFromSentry(sentry);
        if (userEventListenerElement == null) {
            throw new RuntimeException("UserEventListener not found for sentry with id=" + sentry.getId() + ". " +
                    "Sentry will not be parsed");
        }

        ActivityDefinition userEventDefinition = getCachedActivityDefinitionById(userEventListenerElement.getId());
        if (userEventDefinition == null) {
            throw new RuntimeException("ActivityDefinition for userEventListener not found in cache. " +
                    "SentryId=" + sentry.getId());
        }

        return userEventDefinition;
    }

    private TUserEventListener searchUserEventListenerFromSentry(Sentry sentry) {
        TPlanItemOnPart sentryOnPart = (TPlanItemOnPart) getOnPart(sentry);
        Object rawUserEventListenerPlanItem = sentryOnPart.getSourceRef();
        if (rawUserEventListenerPlanItem == null || !rawUserEventListenerPlanItem.getClass().equals(TPlanItem.class)) {
            return null;
        }

        TPlanItem userEventListenerPlanItem = (TPlanItem) rawUserEventListenerPlanItem;
        Object userEventListenerElement = userEventListenerPlanItem.getDefinitionRef();
        if (userEventListenerElement != null && userEventListenerElement.getClass().equals(TUserEventListener.class)) {
            return (TUserEventListener) userEventListenerElement;
        }
        return null;
    }

    private void addUserActionAdditionalAttributes(ActivityDefinition activityDefinition, Sentry sentry) {
        Map<javax.xml.namespace.QName, String> sentryAttributes = sentry.getOtherAttributes();

        for (javax.xml.namespace.QName userActionAttributeQName : CMMNUtils.EVENT_PROPS_MAPPING.keySet()) {
            String attributeValue = sentryAttributes.get(userActionAttributeQName);
            if (attributeValue != null) {
                activityDefinition.getData().set(userActionAttributeQName.getLocalPart(), attributeValue);
            }
        }
    }

    private void addUserActionTitle(ActivityDefinition activityDefinition, Sentry sentry) {
        Map<javax.xml.namespace.QName, String> otherAttributes = getOnPart(sentry).getOtherAttributes();
        if (MapUtils.isNotEmpty(otherAttributes)) {
            String title = otherAttributes.get(CMMNUtils.QNAME_TITLE);
            if (title != null) {
                activityDefinition.getData().set(CmmnDefinitionConstants.TITLE, title);
            }
        }
    }

    private void addTransitionImpl(ActivityDefinition activityDefinition, Sentry sentry,
                                   ActivityState fromState, ActivityState toState) {

        if (isUserEventListenerSentry(sentry)) {
            ActivityDefinition userEventDefinition = findUserEventListenerDefinition(sentry);

            addUserActionAdditionalAttributes(userEventDefinition, sentry);
            addUserActionTitle(userEventDefinition, sentry);

            addSentryTransitionImpl(userEventDefinition, sentry, ActivityState.NOT_STARTED, ActivityState.STARTED);

            ActivityTransitionDefinition transition = getOrCreateTransitionByState(
                    activityDefinition, fromState, toState, isRestartSentry(sentry));
            TriggerDefinition trigger = getOrCreateTriggerDefinition(transition);
            SentryTriggerDefinition sentryTriggerDefinition = getOrCreateSentryTriggerDefinition(trigger);

            String userEventListenerSentryId = sentry.getId() + "-uel";
            addSentryDefinitionImpl(
                    userEventListenerSentryId,
                    ICaseEventModel.CONSTR_ACTIVITY_STARTED,
                    getSourceRef(userEventDefinition),
                    null,
                    trigger,
                    sentryTriggerDefinition);
        } else {
            addSentryTransitionImpl(activityDefinition, sentry, fromState, toState);
        }
    }

    private void addSentryTransitionImpl(ActivityDefinition activityDefinition, Sentry sentry,
                                         ActivityState fromState, ActivityState toState) {
        ActivityTransitionDefinition transition = getOrCreateTransitionByState(
                activityDefinition, fromState, toState, isRestartSentry(sentry));
        TriggerDefinition trigger = getOrCreateTriggerDefinition(transition);
        SentryTriggerDefinition sentryTriggerDefinition = getOrCreateSentryTriggerDefinition(trigger);
        addSentryDefinition(trigger, sentryTriggerDefinition, sentry);
    }

    private boolean isRestartSentry(Sentry sentry) {
        TOnPart onPart = getOnPart(sentry);
        String isRestartEvent = onPart.getOtherAttributes().get(CMMNUtils.QNAME_IS_RESTART_EVENT);
        return Boolean.parseBoolean(isRestartEvent);
    }

    private ActivityTransitionDefinition getOrCreateTransitionByState(ActivityDefinition activityDefinition,
                                                                      ActivityState fromState,
                                                                      ActivityState toState,
                                                                      boolean isRestart) {
        ActivityTransitionDefinition transition = getTransitionDefinitionByFromAndToState(
                activityDefinition, fromState, toState, isRestart);
        if (transition == null) {
            transition = new ActivityTransitionDefinition();
            transition.setFromState(fromState);
            transition.setToState(toState);
            transition.setParentActivityDefinition(activityDefinition);
            transition.setRestartRequired(isRestart);

            addCompletenessLevelsToTransition(activityDefinition.getId(), transition);

            if (activityDefinition.getTransitions() == null) {
                activityDefinition.setTransitions(new ArrayList<>());
            }
            activityDefinition.getTransitions().add(transition);
        }
        return transition;
    }

    private ActivityTransitionDefinition getTransitionDefinitionByFromAndToState(ActivityDefinition activityDefinition,
                                                                                 ActivityState fromState,
                                                                                 ActivityState toState,
                                                                                 boolean isRestart) {

        List<ActivityTransitionDefinition> transitions = activityDefinition.getTransitions();
        if (CollectionUtils.isNotEmpty(transitions)) {
            return transitions.stream()
                    .filter(transition -> transition.getFromState() == fromState)
                    .filter(transition -> transition.getToState() == toState)
                    .filter(transition -> transition.isRestartRequired() == isRestart)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private void addCompletenessLevelsToTransition(String activityId, ActivityTransitionDefinition transition) {
        ActivityState toState = transition.getToState();
        CompletenessLevels completenessLevels = getCompletenessLevelsFromCache(activityId);
        if (completenessLevels != null) {
            if (toState == ActivityState.STARTED) {
                String startCompletenessLevels = completenessLevels.getStartCompletenessLevels();
                if (StringUtils.isNotBlank(startCompletenessLevels)) {
                    addCompletenessLevels(transition, startCompletenessLevels);
                }
            } else if (toState == ActivityState.COMPLETED) {
                String stopCompletenessLevels = completenessLevels.getStopCompletenessLevels();
                if (StringUtils.isNotBlank(stopCompletenessLevels)) {
                    addCompletenessLevels(transition, stopCompletenessLevels);
                }
            }
        }
    }

    private void addCompletenessLevels(ActivityTransitionDefinition transition, String completenessLevels) {
        EvaluatorDefinition evaluator = new EvaluatorDefinition();
        evaluator.setId(getNextEvaluatorDefinitionId());
        evaluator.setInverse(false);
        evaluator.setData(ObjectData.create(composeCompletenessLevelsEvaluatorData(completenessLevels)));
        transition.setEvaluator(evaluator);
    }

    private EvaluatorDefinitionDataHolder composeCompletenessLevelsEvaluatorData(String completenessLevels) {
        EvaluatorDefinitionData definitionData = new EvaluatorDefinitionData();
        definitionData.setType(CmmnDefinitionConstants.COMPLETENESS_TYPE);

        definitionData.setAttributes(Collections.singletonMap(
                CmmnDefinitionConstants.COMPLETENESS_LEVELS_SET, completenessLevels));

        List<EvaluatorDefinitionData> result = Collections.singletonList(definitionData);
        return new EvaluatorDefinitionDataHolder(result);
    }

    private TriggerDefinition getOrCreateTriggerDefinition(ActivityTransitionDefinition transition) {
        TriggerDefinition trigger = transition.getTrigger();
        if (trigger == null) {
            trigger = new TriggerDefinition();
            trigger.setId(getNextTriggerDefinitionId());
            trigger.setType(SentryTriggerDefinition.TYPE_NAME);
            trigger.setParentActivityTransitionDefinition(transition);
            transition.setTrigger(trigger);
        }
        return trigger;
    }

    private SentryTriggerDefinition getOrCreateSentryTriggerDefinition(TriggerDefinition trigger) {
        SentryTriggerDefinition data = trigger.getData();
        if (data == null) {
            SentryTriggerDefinition sentryTriggerDefinition = new SentryTriggerDefinition();
            trigger.setData(sentryTriggerDefinition);
            return sentryTriggerDefinition;
        }
        return data;
    }

    private void addSentryDefinition(TriggerDefinition trigger, SentryTriggerDefinition sentryTriggerDefinition,
                                     Sentry sentry) {
        addSentryDefinitionImpl(
                sentry.getId(),
                getEventType(sentry),
                getSourceRef(sentry),
                composeEvaluatorDefinition(sentry),
                trigger,
                sentryTriggerDefinition);
    }

    private void addSentryDefinitionImpl(String sentryId, String eventType, SourceRef sourceRef,
                                         EvaluatorDefinition evaluatorDefinition, TriggerDefinition parentTrigger,
                                         SentryTriggerDefinition sentryTriggerDefinition) {

        if (sentryExistsInCache(sentryId)) {
            return;
        }

        SentryDefinition sentryDefinition = new SentryDefinition();
        sentryDefinition.setId(sentryId);
        sentryDefinition.setEvent(eventType);
        sentryDefinition.setSourceRef(sourceRef);
        sentryDefinition.setEvaluator(evaluatorDefinition);
        sentryDefinition.setParentTriggerDefinition(parentTrigger);

        List<SentryDefinition> sentries = sentryTriggerDefinition.getSentries();
        if (sentries == null) {
            sentries = new ArrayList<>();
            sentryTriggerDefinition.setSentries(sentries);
        }

        sentries.add(sentryDefinition);
        addSentryDefinitionToIdentityCache(sentryDefinition);
        addSentryDefinitionToSearchCache(sentryDefinition);
    }

    private String getEventType(Sentry sentry) {
        return sentry.getOtherAttributes().get(CMMNUtils.QNAME_ORIGINAL_EVENT);
    }

    private SourceRef getSourceRef(Sentry sentry) {
        String sourceId = getOnPart(sentry).getOtherAttributes().get(CMMNUtils.QNAME_SOURCE_ID);

        ActivityDefinition definition = getCachedActivityDefinitionById(sourceId);
        if (definition != null) {
            return getSourceRef(definition);
        } else {
            log.warn("Can not find sourceRef " + sourceId);
            return new SourceRef(sourceId, null);
        }
    }

    private SourceRef getSourceRef(ActivityDefinition sourceDefinition) {
        if (sourceDefinition.getType() == ActivityType.ROOT) {
            return new SourceRef(ActivityRef.ROOT_ID, null);
        } else {
            return new SourceRef(sourceDefinition.getId(), null);
        }
    }

    private EvaluatorDefinition composeEvaluatorDefinition(Sentry sentry) {
        if (sentry.getIfPart() == null) {
            return null;
        }

        TIfPart ifPart = sentry.getIfPart();
        TExpression expression = ifPart.getCondition();
        String content = (String) expression.getContent().get(0);
        content = content.replace("<!CDATA[", "").replace("]]>", "");
        try {
            List<Condition> conditions = unmarshalConditions(content).getConditions();
            EvaluatorDefinition evaluatorDef = new EvaluatorDefinition();
            evaluatorDef.setId(getNextEvaluatorDefinitionId());
            evaluatorDef.setInverse(false);
            evaluatorDef.setData(ObjectData.create(composeEvaluatorDefinitionData(conditions)));
            return evaluatorDef;
        } catch (JAXBException e) {
            throw new RuntimeException("Error of parsing condition of sentry " + sentry.getId(), e);
        }
    }

    private EvaluatorDefinitionDataHolder composeEvaluatorDefinitionData(List<Condition> conditions) {
        List<EvaluatorDefinitionData> result = new ArrayList<>();
        for (Condition condition : conditions) {
            EvaluatorDefinitionData definitionData = new EvaluatorDefinitionData();

            QName type = utils.convertFromXMLQName(condition.getType());
            definitionData.setType(type.getLocalName());

            Map<String, String> evaluatorProperties = new HashMap<>(condition.getProperties().size());
            for (ConditionProperty property : condition.getProperties()) {
                String propertyName = utils.convertFromXMLQName(property.getType()).getLocalName();
                String propertyValue = property.getValue();
                evaluatorProperties.put(propertyName, propertyValue);
            }
            definitionData.setAttributes(evaluatorProperties);

            result.add(definitionData);
        }
        return new EvaluatorDefinitionDataHolder(result);
    }

    private ConditionsList unmarshalConditions(String xml) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(ConditionsList.class);
        Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
        StringReader stringReader = new StringReader(xml);
        return (ConditionsList) jaxbUnmarshaller.unmarshal(stringReader);
    }

    // parser utility area

    private TOnPart getOnPart(Sentry sentry) {
        List<JAXBElement<? extends TOnPart>> onParts = sentry.getOnPart();
        return onParts.get(0).getValue();
    }

    // caches area

    private int getNextIndex() {
        return getNextAtomicValue(activityIndexCounter);
    }

    private String getNextTriggerDefinitionId() {
        return "trgr-" + getNextAtomicValue(triggerIdCounter);
    }

    private String getNextEvaluatorDefinitionId() {
        return "evltr-" + getNextAtomicValue(evaluatorIdCounter);
    }

    private void addActivityDefinitionToIdentityCache(String id, ActivityDefinition activityDefinition) {
        addToThreadLocalCache(id, activityDefinition, idToActivityCache);
    }

    private void addSentryDefinitionToIdentityCache(SentryDefinition sentryDefinition) {
        addToThreadLocalCache(sentryDefinition.getId(), sentryDefinition, idToSentryCache);
    }

    private boolean sentryExistsInCache(String id) {
        return getFromThreadLocalCache(id, idToSentryCache) != null;
    }

    private void addSentryDefinitionToSearchCache(SentryDefinition sentryDefinition) {
        Map<SentrySearchKey, List<SentryDefinition>> cache = searchKeyToSentryCache.get();
        if (cache == null) {
            cache = new HashMap<>();
            searchKeyToSentryCache.set(cache);
        }

        SentrySearchKey key = new SentrySearchKey(sentryDefinition.getSourceRef(), sentryDefinition.getEvent());
        List<SentryDefinition> sentryDefinitions = cache.computeIfAbsent(key, k -> new ArrayList<>());

        sentryDefinitions.add(sentryDefinition);
    }

    private void addRoleVarNameToTaskDefinitionCache(String[] roleVarNames, ActivityDefinition activityDefinition) {
        if (roleVarNames == null) {
            return;
        }

        for (String roleVarName : roleVarNames) {
            Set<ActivityDefinition> cache = getFromThreadLocalCache(roleVarName, roleVarNameToTaskDefinitionCache);
            if (cache != null) {
                cache.add(activityDefinition);
            } else {
                addToThreadLocalCache(roleVarName, Sets.newHashSet(activityDefinition), roleVarNameToTaskDefinitionCache);
            }
        }
    }

    private ActivityDefinition getCachedActivityDefinitionById(String id) {
        return getFromThreadLocalCache(id, idToActivityCache);
    }

    private void addRoleReferenceToThreadCache(RoleReference roleReference) {
        addToThreadLocalCache(roleReference.getId(), roleReference.getVarName(), idToVarNameRoleCache);
    }

    private String getRoleVarNameByIdFromThreadCache(String roleId) {
        return getFromThreadLocalCache(roleId, idToVarNameRoleCache);
    }

    private void addCompletenessLevelsCache(String id, CompletenessLevels completenessLevels) {
        addToThreadLocalCache(id, completenessLevels, idToCompletenessLevelsCache);
    }

    private CompletenessLevels getCompletenessLevelsFromCache(String id) {
        return getFromThreadLocalCache(id, idToCompletenessLevelsCache);
    }

    private int getNextAtomicValue(ThreadLocal<AtomicInteger> triggerDefinitionIdCounter) {
        AtomicInteger atomicInteger = triggerDefinitionIdCounter.get();
        if (atomicInteger == null) {
            atomicInteger = new AtomicInteger(0);
            triggerDefinitionIdCounter.set(atomicInteger);
        }
        return atomicInteger.incrementAndGet();
    }

    private <K, V> void addToThreadLocalCache(K key, V value, ThreadLocal<Map<K, V>> threadLocal) {
        Map<K, V> cacheMap = threadLocal.get();
        if (cacheMap == null) {
            cacheMap = new HashMap<>();
            threadLocal.set(cacheMap);
        }
        cacheMap.put(key, value);
    }

    private <K, V> V getFromThreadLocalCache(K key, ThreadLocal<Map<K, V>> threadLocal) {
        Map<K, V> cacheMap = threadLocal.get();
        if (cacheMap == null) {
            return null;
        }
        return cacheMap.get(key);
    }

    private void clearParsingExecutionCache() {
        idToActivityCache.remove();
        idToSentryCache.remove();
        searchKeyToSentryCache.remove();
        triggerIdCounter.remove();
        evaluatorIdCounter.remove();
        activityIndexCounter.remove();
        idToVarNameRoleCache.remove();
        idToCompletenessLevelsCache.remove();
        idToUserEventListenerCache.remove();
    }

    @Data
    private static class CompletenessLevels {
        private String startCompletenessLevels;
        private String stopCompletenessLevels;
    }

}
