package ru.citeck.ecos.behavior.event;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.action.ActionConditionUtils;
import ru.citeck.ecos.behavior.ChainingJavaBehaviour;
import ru.citeck.ecos.behavior.event.trigger.EProcUserActionEventTrigger;
import ru.citeck.ecos.behavior.event.trigger.UserActionEventTrigger;
import ru.citeck.ecos.comment.CommentTag;
import ru.citeck.ecos.comment.EcosCommentTagService;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.icase.activity.dto.ActivityDefinition;
import ru.citeck.ecos.icase.activity.dto.ActivityType;
import ru.citeck.ecos.icase.activity.dto.EventRef;
import ru.citeck.ecos.icase.activity.dto.SentryDefinition;
import ru.citeck.ecos.icase.activity.service.alfresco.EventPolicies;
import ru.citeck.ecos.icase.activity.service.eproc.EProcActivityService;
import ru.citeck.ecos.icase.activity.service.eproc.EProcCaseActivityListenerManager;
import ru.citeck.ecos.icase.activity.service.eproc.listeners.BeforeEventListener;
import ru.citeck.ecos.model.EventModel;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.spring.registry.MappingRegistry;
import ru.citeck.ecos.utils.DictUtils;
import ru.citeck.ecos.utils.RepoUtils;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Roman Makarskiy
 */
@Component
public class AddCommentWithActionTagBehaviour implements EventPolicies.BeforeEventPolicy, BeforeEventListener {

    private final EProcActivityService eprocActivityService;
    private final EProcCaseActivityListenerManager manager;
    private final EcosCommentTagService commentTagService;
    private final NamespaceService namespaceService;
    private final PolicyComponent policyComponent;
    private final NodeService nodeService;
    private final DictUtils dictUtils;

    private Map<QName, QName> typeToComment;

    @Autowired
    public AddCommentWithActionTagBehaviour(EProcCaseActivityListenerManager manager,
                                            EProcActivityService eprocActivityService,
                                            NamespaceService namespaceService,
                                            NodeService nodeService,
                                            EcosCommentTagService commentTagService,
                                            PolicyComponent policyComponent,
                                            DictUtils dictUtils) {
        this.manager = manager;
        this.eprocActivityService = eprocActivityService;
        this.namespaceService = namespaceService;
        this.nodeService = nodeService;
        this.commentTagService = commentTagService;
        this.policyComponent = policyComponent;
        this.dictUtils = dictUtils;
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
        QName eventType = nodeService.getType(eventRef);
        if (!eventType.equals(EventModel.TYPE_USER_ACTION)) {
            return;
        }

        NodeRef additionalData = (NodeRef) ActionConditionUtils.getTransactionVariables()
            .get(UserActionEventTrigger.ADDITIONAL_DATA_VARIABLE);
        if (additionalData == null) {
            return;
        }

        NodeRef eventSource = RepoUtils.getFirstTargetAssoc(eventRef, EventModel.ASSOC_EVENT_SOURCE, nodeService);
        if (eventSource == null) {
            return;
        }

        addCommentWithTag(additionalData, RecordRef.valueOf(eventSource.toString()));
    }

    @Override
    public void beforeEvent(EventRef eventRef) {
        if (eventRef == null) {
            return;
        }

        SentryDefinition sentry = eprocActivityService.getSentryDefinition(eventRef);
        if (!isUserAction(sentry)) {
            return;
        }

        NodeRef additionalDataRef = (NodeRef) ActionConditionUtils.getTransactionVariables()
            .get(EProcUserActionEventTrigger.ADDITIONAL_DATA_VARIABLE);
        if (additionalDataRef == null) {
            return;
        }

        RecordRef caseRef = eventRef.getProcessId();
        if (RecordRef.isEmpty(caseRef)) {
            return;
        }

        addCommentWithTag(additionalDataRef, caseRef);
    }

    private boolean isUserAction(SentryDefinition sentry) {
        ActivityDefinition activityDefinition = sentry.getParentTriggerDefinition()
            .getParentActivityTransitionDefinition()
            .getParentActivityDefinition();
        return activityDefinition.getType() == ActivityType.USER_EVENT_LISTENER;
    }

    private void addCommentWithTag(NodeRef additionalDataRef, RecordRef caseRef) {
        if (RecordRef.isEmpty(caseRef)) {
            return;
        }

        QName commentProp = resolveCommentProp(additionalDataRef);
        String comment = (String) nodeService.getProperty(additionalDataRef, commentProp);
        if (StringUtils.isBlank(comment)) {
            return;
        }

        MLText actionName = getActionTitle(additionalDataRef);

        commentTagService.addCommentWithTag(caseRef, comment, CommentTag.ACTION, actionName);
    }

    private QName resolveCommentProp(NodeRef additionalDataRef) {
        QName additionalDataType = nodeService.getType(additionalDataRef);
        return typeToComment.getOrDefault(additionalDataType, EventModel.PROP_COMMENT);
    }

    private MLText getActionTitle(NodeRef additionalDataRef) {
        QName dataType = nodeService.getType(additionalDataRef);
        return new MLText(dictUtils.getTypeMlTitle(dataType));
    }

    @Autowired
    @Qualifier("case.actions.additional-data.add-comment-with-tag.mappingRegistry")
    public void setTypeToCommentRegistry(MappingRegistry<String, String> typeToCommentRegistry) {
        this.typeToComment = typeToCommentRegistry.getMapping().entrySet().stream().collect(Collectors.toMap(
            entry -> QName.resolveToQName(namespaceService, entry.getKey()),
            entry -> QName.resolveToQName(namespaceService, entry.getValue())
        ));
    }
}
