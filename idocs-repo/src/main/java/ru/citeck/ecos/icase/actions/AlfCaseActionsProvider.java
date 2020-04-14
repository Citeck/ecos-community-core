package ru.citeck.ecos.icase.actions;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.model.Repository;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.action.node.CreateNodeAction;
import ru.citeck.ecos.action.node.NodeActionDefinition;
import ru.citeck.ecos.action.node.RequestAction;
import ru.citeck.ecos.icase.activity.dto.CaseServiceType;
import ru.citeck.ecos.icase.activity.dto.EventRef;
import ru.citeck.ecos.icase.activity.service.CaseActivityEventService;
import ru.citeck.ecos.model.EventModel;
import ru.citeck.ecos.model.ICaseRoleModel;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.service.CiteckServices;
import ru.citeck.ecos.utils.RepoUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AlfCaseActionsProvider implements CaseActionsProvider {

    private static final String FIRE_EVENT_URL_TEMPLATE = "citeck/event/fire-event?eventRef=%s";

    private CaseActivityEventService caseActivityEventService;
    private NodeService nodeService;
    private DictionaryService dictionaryService;
    private AuthorityService authorityService;
    private NamespaceService namespaceService;
    private Repository repositoryHelper;

    @Autowired
    public AlfCaseActionsProvider(ServiceRegistry serviceRegistry,
                                  @Qualifier("repositoryHelper") Repository repositoryHelper) {

        this.caseActivityEventService = (CaseActivityEventService) serviceRegistry
                .getService(CiteckServices.CASE_ACTIVITY_EVENT_SERVICE);
        this.nodeService = serviceRegistry.getNodeService();
        this.dictionaryService = serviceRegistry.getDictionaryService();
        this.authorityService = serviceRegistry.getAuthorityService();
        this.namespaceService = serviceRegistry.getNamespaceService();
        this.repositoryHelper = repositoryHelper;
    }

    @Override
    public List<NodeActionDefinition> getCaseActions(NodeRef nodeRef) {
        return getActions(nodeRef);
    }

    private List<NodeActionDefinition> getActions(NodeRef eventSource) {
        List<NodeRef> events = getUserActionEvents(eventSource);
        List<NodeActionDefinition> actions = new ArrayList<>(events.size());
        for (NodeRef event : events) {
            String additionalDataType = (String) nodeService.getProperty(event, EventModel.PROP_ADDITIONAL_DATA_TYPE);
            NodeActionDefinition definition;
            if (StringUtils.isBlank(additionalDataType)) {
                RequestAction requestAction = new RequestAction();
                requestAction.setUrl(String.format(FIRE_EVENT_URL_TEMPLATE, event.toString()));
                requestAction.setConfirmationMessage(getMessage(event, EventModel.PROP_CONFIRMATION_MESSAGE));
                requestAction.setSuccessMessage(getMessage(event, EventModel.PROP_SUCCESS_MESSAGE));
                String spanClass = (String) nodeService.getProperty(event, EventModel.PROP_SUCCESS_MESSAGE_SPAN_CLASS);
                if (spanClass != null) {
                    requestAction.setSuccessMessageSpanClass(spanClass);
                }
                definition = requestAction;
            } else {
                CreateNodeAction createNodeAction = new CreateNodeAction();
                createNodeAction.setNodeType(additionalDataType);
                createNodeAction.setDestination(event);
                String destinationAssoc = EventModel.ASSOC_ADDITIONAL_DATA_ITEMS.toPrefixString(namespaceService);
                createNodeAction.setDestinationAssoc(destinationAssoc);
                definition = createNodeAction;
            }
            definition.setTitle((String) nodeService.getProperty(event, ContentModel.PROP_TITLE));
            actions.add(definition);
        }
        return actions;
    }

    private String getMessage(NodeRef eventRef, QName property) {
        String message = (String) nodeService.getProperty(eventRef, property);
        if (StringUtils.isNotBlank(message)) {
            String messageFromProperties = I18NUtil.getMessage(message);
            return StringUtils.defaultIfBlank(messageFromProperties, message);
        }
        return "";
    }

    private List<NodeRef> getUserActionEvents(final NodeRef eventSource) {
        List<NodeRef> events = RepoUtils.getSourceNodeRefs(eventSource, EventModel.ASSOC_EVENT_SOURCE, nodeService);

        return CollectionUtils.filter(events, eventRef -> {
            return checkEventType(eventRef)
                    && checkRoles(eventRef)
                    && checkEventConditions(eventRef, eventSource);
        });
    }

    private boolean checkEventType(NodeRef eventRef) {
        QName eventType = nodeService.getType(eventRef);
        return eventType.equals(EventModel.TYPE_USER_ACTION);
    }

    private boolean checkRoles(NodeRef eventNodeRef) {

        List<AssociationRef> roleAssocList = nodeService.getTargetAssocs(eventNodeRef, EventModel.ASSOC_AUTHORIZED_ROLES);
        if (roleAssocList == null || roleAssocList.isEmpty()) {
            return true;
        }

        Set<String> authorizedAuthorities = new HashSet<>();
        for (AssociationRef ref : roleAssocList) {
            List<NodeRef> assignees = RepoUtils.getTargetAssoc(ref.getTargetRef(), ICaseRoleModel.ASSOC_ASSIGNEES, nodeService);
            for (NodeRef assignee : assignees) {
                authorizedAuthorities.add(RepoUtils.getAuthorityName(assignee, nodeService, dictionaryService));
            }
        }

        NodeRef person = repositoryHelper.getPerson();
        String userName = (String) nodeService.getProperty(person, ContentModel.PROP_USERNAME);
        Set<String> userAuthorities = new HashSet<>(authorityService.getAuthoritiesForUser(userName));
        userAuthorities.add(userName);

        userAuthorities.retainAll(authorizedAuthorities);
        return !userAuthorities.isEmpty();
    }

    private boolean checkEventConditions(NodeRef eventNodeRef, NodeRef eventSource) {
        RecordRef caseRef = RecordRef.valueOf(eventSource.toString());
        EventRef eventRef = EventRef.of(CaseServiceType.ALFRESCO, caseRef, eventNodeRef.toString());
        return caseActivityEventService.checkConditions(eventRef);
    }
}
