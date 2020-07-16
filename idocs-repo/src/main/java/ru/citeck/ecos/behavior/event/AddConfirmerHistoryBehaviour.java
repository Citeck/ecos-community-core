package ru.citeck.ecos.behavior.event;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.action.ActionConditionUtils;
import ru.citeck.ecos.behavior.ChainingJavaBehaviour;
import ru.citeck.ecos.behavior.event.trigger.EProcUserActionEventTrigger;
import ru.citeck.ecos.behavior.event.trigger.UserActionEventTrigger;
import ru.citeck.ecos.history.HistoryService;
import ru.citeck.ecos.icase.activity.dto.ActivityDefinition;
import ru.citeck.ecos.icase.activity.dto.ActivityType;
import ru.citeck.ecos.icase.activity.dto.EventRef;
import ru.citeck.ecos.icase.activity.dto.SentryDefinition;
import ru.citeck.ecos.icase.activity.service.alfresco.EventPolicies;
import ru.citeck.ecos.icase.activity.service.eproc.EProcActivityService;
import ru.citeck.ecos.icase.activity.service.eproc.EProcCaseActivityListenerManager;
import ru.citeck.ecos.icase.activity.service.eproc.listeners.BeforeEventListener;
import ru.citeck.ecos.model.EventModel;
import ru.citeck.ecos.model.HistoryModel;
import ru.citeck.ecos.records.RecordsUtils;
import ru.citeck.ecos.utils.RepoUtils;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Pavel Simonov
 */
@Component
public class AddConfirmerHistoryBehaviour implements
        EventPolicies.BeforeEventPolicy, BeforeEventListener {

    private static final String HISTORY_EVENT_MESSAGE = "history.add.confirmer.message";
    private static final String HISTORY_EVENT_COMMENT = "history.comment.confirmer.message";
    private static final String USER_EVENT_HISTORY_TYPE = "user.action";

    private EProcActivityService eprocActivityService;
    private EProcCaseActivityListenerManager manager;
    private HistoryService historyService;
    private PolicyComponent policyComponent;
    private NodeService nodeService;

    @Autowired
    public AddConfirmerHistoryBehaviour(EProcActivityService eprocActivityService,
                                        EProcCaseActivityListenerManager manager,
                                        HistoryService historyService,
                                        PolicyComponent policyComponent,
                                        NodeService nodeService) {
        this.eprocActivityService = eprocActivityService;
        this.manager = manager;
        this.historyService = historyService;
        this.policyComponent = policyComponent;
        this.nodeService = nodeService;
    }

    @PostConstruct
    public void init() {
        policyComponent.bindClassBehaviour(EventPolicies.BeforeEventPolicy.QNAME,
                ContentModel.TYPE_CMOBJECT,
                new ChainingJavaBehaviour(this, "beforeEvent", Behaviour.NotificationFrequency.EVERY_EVENT));
        manager.subscribeBeforeEvent(this);
    }

    @Override
    public void beforeEvent(NodeRef eventRef) {
        if (nodeService.getType(eventRef).equals(EventModel.TYPE_USER_ACTION)) {
            NodeRef additionalData = (NodeRef) ActionConditionUtils.getTransactionVariables()
                    .get(UserActionEventTrigger.ADDITIONAL_DATA_VARIABLE);
            if (additionalData != null) {
                QName dataType = nodeService.getType(additionalData);
                NodeRef eventSource = RepoUtils.getFirstTargetAssoc(eventRef, EventModel.ASSOC_EVENT_SOURCE, nodeService);
                if (eventSource == null || !dataType.equals(EventModel.TYPE_ADDITIONAL_CONFIRMER)) {
                    return;
                }
                addHistoryEvent(additionalData, eventSource);
            }
        }
    }

    @Override
    public void beforeEvent(EventRef eventRef) {
        SentryDefinition sentry = eprocActivityService.getSentryDefinition(eventRef);
        if (!isUserAction(sentry)) {
            return;
        }
        NodeRef additionalDataRef = (NodeRef) ActionConditionUtils.getTransactionVariables()
                .get(EProcUserActionEventTrigger.ADDITIONAL_DATA_VARIABLE);
        if (additionalDataRef != null) {
            QName dataType = nodeService.getType(additionalDataRef);
            if (!dataType.equals(EventModel.TYPE_ADDITIONAL_CONFIRMER)) {
                return;
            }
            addHistoryEvent(additionalDataRef, RecordsUtils.toNodeRef(eventRef.getProcessId()));
        }
    }

    private boolean isUserAction(SentryDefinition sentry) {
        ActivityDefinition activityDefinition = sentry.getParentTriggerDefinition()
                .getParentActivityTransitionDefinition()
                .getParentActivityDefinition();
        return activityDefinition.getType() == ActivityType.USER_EVENT_LISTENER;
    }

    private void addHistoryEvent(NodeRef additionalDataRef, NodeRef caseRef) {
        Map<QName, Serializable> eventProperties = new HashMap<>();
        eventProperties.put(HistoryModel.PROP_NAME, USER_EVENT_HISTORY_TYPE);
        eventProperties.put(HistoryModel.ASSOC_DOCUMENT, caseRef);
        eventProperties.put(HistoryModel.PROP_TASK_COMMENT, buildEventComment(additionalDataRef));
        historyService.persistEvent(HistoryModel.TYPE_BASIC_EVENT, eventProperties);
    }

    private String buildEventComment(NodeRef additionalDataRef) {
        NodeRef confirmerRef = RepoUtils.getFirstTargetAssoc(additionalDataRef, EventModel.ASSOC_CONFIRMER, nodeService);
        QName confirmerType = nodeService.getType(confirmerRef);
        String confirmer;
        if (confirmerType.equals(ContentModel.TYPE_PERSON)) {
            String firstName = (String) nodeService.getProperty(confirmerRef, ContentModel.PROP_FIRSTNAME);
            String lastName = (String) nodeService.getProperty(confirmerRef, ContentModel.PROP_LASTNAME);
            confirmer = String.format("%s %s", firstName, lastName);
        } else {
            confirmer = (String) nodeService.getProperty(confirmerRef, ContentModel.PROP_AUTHORITY_DISPLAY_NAME);
        }
        String result = I18NUtil.getMessage(HISTORY_EVENT_MESSAGE, confirmer);

        String comment = (String) nodeService.getProperty(additionalDataRef, EventModel.PROP_COMMENT);
        if (StringUtils.isNotBlank(comment)) {
            result = result + " " + I18NUtil.getMessage(HISTORY_EVENT_COMMENT, comment);
        }
        return result;
    }
}
