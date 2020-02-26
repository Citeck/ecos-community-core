package ru.citeck.ecos.icase.activity.service;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.policy.ClassPolicyDelegate;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.QNamePattern;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.alfresco.util.Pair;
import org.apache.commons.lang.StringUtils;
import ru.citeck.ecos.icase.activity.CaseActivityPolicies.*;
import ru.citeck.ecos.icase.activity.dto.CaseActivity;
import ru.citeck.ecos.model.ActivityModel;
import ru.citeck.ecos.model.LifeCycleModel;
import ru.citeck.ecos.utils.DictionaryUtils;
import ru.citeck.ecos.utils.RepoUtils;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class CaseActivityServiceImpl implements CaseActivityService {

    private DictionaryService dictionaryService;
    private PolicyComponent policyComponent;
    private NodeService nodeService;

    private ClassPolicyDelegate<BeforeCaseActivityStartedPolicy> beforeStartedDelegate;
    private ClassPolicyDelegate<OnCaseActivityStartedPolicy> onStartedDelegate;
    private ClassPolicyDelegate<BeforeCaseActivityStoppedPolicy> beforeStoppedDelegate;
    private ClassPolicyDelegate<OnCaseActivityStoppedPolicy> onStoppedDelegate;
    private ClassPolicyDelegate<OnCaseActivityResetPolicy> onResetDelegate;
    private ClassPolicyDelegate<OnChildrenIndexChangedPolicy> onIndexChangedDelegate;

    private Map<String, List<String>> allowedTransitions = new HashMap<>();

    public void init() {
        beforeStartedDelegate = policyComponent.registerClassPolicy(BeforeCaseActivityStartedPolicy.class);
        onStartedDelegate = policyComponent.registerClassPolicy(OnCaseActivityStartedPolicy.class);
        beforeStoppedDelegate = policyComponent.registerClassPolicy(BeforeCaseActivityStoppedPolicy.class);
        onStoppedDelegate = policyComponent.registerClassPolicy(OnCaseActivityStoppedPolicy.class);
        onResetDelegate = policyComponent.registerClassPolicy(OnCaseActivityResetPolicy.class);
        onIndexChangedDelegate = policyComponent.registerClassPolicy(OnChildrenIndexChangedPolicy.class);

        allowedTransitions.put(CaseActivity.State.NOT_STARTED.getContent(),
            Collections.singletonList(CaseActivity.State.STARTED.getContent()));
        allowedTransitions.put(CaseActivity.State.STARTED.getContent(),
            Collections.singletonList(CaseActivity.State.COMPLETED.getContent()));
    }

    @Override
    public void startActivity(CaseActivity activity) {

        String activityId = activity.getId();

        if (!canSetState(activityId, CaseActivity.State.STARTED.getContent())) {
            return;
        }

        Map<QName, Serializable> props = new HashMap<>();
        props.put(LifeCycleModel.PROP_STATE, CaseActivity.State.STARTED.getContent());
        props.put(ActivityModel.PROP_ACTUAL_START_DATE, new Date());

        NodeRef activityRef = new NodeRef(activityId);
        nodeService.addProperties(activityRef, props);

        List<QName> nodeClassNames = DictionaryUtils.getNodeClassNames(activityRef, nodeService);
        Set<QName> classes = new HashSet<>(nodeClassNames);

        beforeStartedDelegate.get(classes).beforeCaseActivityStarted(activityRef);
        onStartedDelegate.get(classes).onCaseActivityStarted(activityRef);
    }

    @Override
    public void stopActivity(CaseActivity activity) {

        String activityId = activity.getId();

        if (!canSetState(activityId, CaseActivity.State.COMPLETED.getContent())) {
            return;
        }

        Map<QName, Serializable> props = new HashMap<>();
        props.put(LifeCycleModel.PROP_STATE, CaseActivity.State.COMPLETED.getContent());
        props.put(ActivityModel.PROP_ACTUAL_END_DATE, new Date());

        NodeRef activityRef = new NodeRef(activityId);
        nodeService.addProperties(activityRef, props);

        List<QName> nodeClassNames = DictionaryUtils.getNodeClassNames(activityRef, nodeService);
        Set<QName> classes = new HashSet<>(nodeClassNames);

        beforeStoppedDelegate.get(classes).beforeCaseActivityStopped(activityRef);
        onStoppedDelegate.get(classes).onCaseActivityStopped(activityRef);
    }

    @Override
    public void restartChildActivity(CaseActivity parent, CaseActivity child) {

        NodeRef parentNodeRef = new NodeRef(parent.getId());
        NodeRef childNodeRef = new NodeRef(child.getId());

        if (nodeService.exists(parentNodeRef) && nodeService.exists(childNodeRef)) {

            if (!parent.isActive()) {

                Map<QName, Serializable> props = new HashMap<>();
                props.put(ActivityModel.PROP_ACTUAL_END_DATE, null);
                props.put(LifeCycleModel.PROP_STATE, CaseActivity.State.STARTED.getContent());
                nodeService.addProperties(parentNodeRef, props);

                resetChildrenActivities(child.getId());
                startActivity(child);
            }
        }
    }

    @Override
    public String getDocumentId(String activityId) {

        NodeRef activityRef = new NodeRef(activityId);

        ChildAssociationRef parent = nodeService.getPrimaryParent(activityRef);
        while (parent.getParentRef() != null
            && RepoUtils.isSubType(parent.getParentRef(), ActivityModel.TYPE_ACTIVITY, nodeService, dictionaryService)) {
            parent = nodeService.getPrimaryParent(parent.getParentRef());
        }

        NodeRef rootNodeRef = parent.getParentRef();

        return rootNodeRef != null ? rootNodeRef.toString() : null;
    }

    @Override
    public void setParent(String activityId, String receivedParentId) {
        mandatoryActivity("activityId", activityId);
        mandatoryNodeRef("parentId", receivedParentId);

        NodeRef activityRef = new NodeRef(activityId);
        NodeRef receivedParentRef = new NodeRef(receivedParentId);

        ChildAssociationRef assocRef = nodeService.getPrimaryParent(activityRef);
        NodeRef parent = assocRef.getParentRef();

        if (!parent.equals(receivedParentRef)) {
            if (!nodeService.hasAspect(receivedParentRef, ActivityModel.ASPECT_HAS_ACTIVITIES)) {
                throw new IllegalArgumentException("New parent doesn't have aspect 'hasActivities'");
            }
            nodeService.moveNode(activityRef, receivedParentRef, ActivityModel.ASSOC_ACTIVITIES,
                ActivityModel.ASSOC_ACTIVITIES);
        }
    }

    @Override
    public CaseActivity getActivity(String activityId) {

        if (StringUtils.isEmpty(activityId)) {
            return null;
        }

        CaseActivity documentActivity = new CaseActivity();

        documentActivity.setId(activityId);

        String documentId = this.getDocumentId(documentActivity.getId());
        documentActivity.setDocumentId(documentId);

        String activityState = getActivityState(activityId);
        boolean isActive = CaseActivity.State.STARTED.getContent().equals(activityState);
        documentActivity.setActive(isActive);

        NodeRef activityNodeRef = new NodeRef(activityId);
        String title = (String) nodeService.getProperty(activityNodeRef, ContentModel.PROP_TITLE);
        documentActivity.setTitle(title);

        return documentActivity;
    }

    @Override
    public List<CaseActivity> getActivities(String documentId) {
        return getActivities(documentId, RegexQNamePattern.MATCH_ALL);
    }

    private List<CaseActivity> getActivities(String documentId, boolean recurse) {
        return getActivities(documentId, RegexQNamePattern.MATCH_ALL, recurse);
    }

    @Override
    public List<CaseActivity> getActivities(String documentId, QNamePattern type) {
        return getActivities(documentId, ActivityModel.ASSOC_ACTIVITIES, type);
    }

    private List<CaseActivity> getActivities(String documentId, QNamePattern type, boolean recurse) {
        return getActivities(documentId, ActivityModel.ASSOC_ACTIVITIES, type, recurse);
    }

    @Override
    public List<CaseActivity> getActivities(String documentId, QName assocType, QNamePattern type) {
        return getActivities(documentId, assocType, type, false);
    }

    @Override
    public List<CaseActivity> getActivities(String documentId,
                                             QName assocType,
                                             QNamePattern type,
                                             boolean recurse) {

        NodeRef documentNodeRef = new NodeRef(documentId);

        List<ChildAssociationRef> children = nodeService.getChildAssocs(documentNodeRef, assocType,
            RegexQNamePattern.MATCH_ALL);
        if (children == null || children.isEmpty()) {
            return new ArrayList<>();
        }

        List<Pair<NodeRef, Integer>> indexedChildren = new ArrayList<>(children.size());
        for (ChildAssociationRef child : children) {
            NodeRef childRef = child.getChildRef();
            if (type.isMatch(nodeService.getType(childRef))) {
                Integer index = (Integer) nodeService.getProperty(childRef, ActivityModel.PROP_INDEX);
                indexedChildren.add(new Pair<>(childRef, index != null ? index : 0));
            }
        }

        indexedChildren.sort(Comparator.comparingInt(Pair::getSecond));

        List<CaseActivity> result = new ArrayList<>(indexedChildren.size());
        for (Pair<NodeRef, Integer> child : indexedChildren) {

            String childActivityId = child.getFirst().toString();
            CaseActivity childActivity = getActivity(childActivityId);
            result.add(childActivity);
        }

        if (recurse) {
            for (ChildAssociationRef child : children) {
                result.addAll(getActivities(child.getChildRef().toString(), assocType, type, true));
            }
        }

        return result;
    }

    @Override
    public List<CaseActivity> getStartedActivities(String documentId) {

        List<CaseActivity> activities = getActivities(documentId);
        return activities.stream()
            .filter(a -> {
                NodeRef activityNodeRef = new NodeRef(a.getId());
                String status = (String) nodeService.getProperty(activityNodeRef, LifeCycleModel.PROP_STATE);
                return status != null && status.equals(CaseActivity.State.STARTED.getContent());
            })
            .collect(Collectors.toList());
    }

    @Override
    public CaseActivity getActivityByTitle(String documentId, String title, boolean recurse) {

        List<CaseActivity> activities = getActivities(documentId, recurse);
        for (CaseActivity activity : activities) {

            NodeRef activityNodeRef = new NodeRef(activity.getId());
            String actTitle = (String) nodeService.getProperty(activityNodeRef, ContentModel.PROP_TITLE);
            if (actTitle.equals(title)) {
                return activity;
            }
        }
        return null;
    }

    @Override
    public void reset(String id) {

        NodeRef nodeRef = new NodeRef(id);
        QName nodeType = nodeService.getType(nodeRef);
        if (dictionaryService.isSubClass(nodeType, ActivityModel.TYPE_ACTIVITY)) {
            resetActivity(id);
        } else {
            resetChildrenActivities(id);
        }
    }

    @Override
    public void setParentInIndex(CaseActivity activity, int newIndex) {

        String activityId = activity.getId();
        mandatoryActivity("activityRef", activityId);

        NodeRef activityNodeRef = new NodeRef(activityId);
        ChildAssociationRef assocRef = nodeService.getPrimaryParent(activityNodeRef);
        NodeRef parentRef = assocRef.getParentRef();

        List<CaseActivity> activities = getActivities(parentRef.toString());

        if (newIndex >= activities.size()) {
            newIndex = activities.size() - 1;
        } else if (newIndex < 0) {
            newIndex = 0;
        }

        if (newIndex != activities.indexOf(activity)) {

            activities.remove(activity);
            activities.add(newIndex, activity);

            for (int index = 0; index < activities.size(); index++) {
                CaseActivity selectedActivity = activities.get(index);
                NodeRef selectedActivityNodeRef = new NodeRef(selectedActivity.getId());
                nodeService.setProperty(selectedActivityNodeRef, ActivityModel.PROP_INDEX, index);
            }

            HashSet<QName> classes = new HashSet<>(DictionaryUtils.getNodeClassNames(parentRef, nodeService));
            onIndexChangedDelegate.get(classes).onChildrenIndexChanged(parentRef);
        }
    }

    @Override
    public boolean hasActiveChildren(CaseActivity activity) {
        mandatoryParameter("activityId", activity.getId());
        mandatoryNodeRef("activityId", new NodeRef(activity.getId()));

        NodeRef activityNodeRef = new NodeRef(activity.getId());
        List<NodeRef> children = RepoUtils.getChildrenByAssoc(activityNodeRef,
            ActivityModel.ASSOC_ACTIVITIES, nodeService);
        for (NodeRef childRef : children) {
            String state = (String) nodeService.getProperty(childRef, LifeCycleModel.PROP_STATE);
            if (state != null && state.equals(CaseActivity.State.STARTED.getContent())) {
                return true;
            }
        }
        return false;
    }

    private void resetActivity(String activityId) {

        NodeRef activityNodeRef = new NodeRef(activityId);
        Map<QName, Serializable> props = new HashMap<>();
        props.put(ActivityModel.PROP_ACTUAL_START_DATE, null);
        props.put(ActivityModel.PROP_ACTUAL_END_DATE, null);
        props.put(LifeCycleModel.PROP_STATE, CaseActivity.State.NOT_STARTED.getContent());
        nodeService.addProperties(activityNodeRef, props);

        HashSet<QName> classes = new HashSet<>(DictionaryUtils.getNodeClassNames(activityNodeRef, nodeService));
        onResetDelegate.get(classes).onCaseActivityReset(activityNodeRef);

        resetChildrenActivities(activityId);
    }

    private void resetChildrenActivities(String documentId) {

        NodeRef documentNodeRef = new NodeRef(documentId);
        List<NodeRef> childrenNodeRefs = RepoUtils.getChildrenByAssoc(documentNodeRef, ActivityModel.ASSOC_ACTIVITIES,
            nodeService);
        for (NodeRef activityNodeRef : childrenNodeRefs) {
            resetActivity(activityNodeRef.toString());
        }
    }

    private boolean canSetState(String activityId, String state) {

        NodeRef activityRef = new NodeRef(activityId);

        if (!nodeService.exists(activityRef)) {
            return false;
        }

        String currentState = getActivityState(activityId);

        if (isRequiredReset(activityRef, currentState, state)) {
            reset(activityId);
            currentState = getActivityState(activityId);
        }

        if (!currentState.equals(state)) {

            List<String> transitions = allowedTransitions.get(currentState);
            if (transitions != null && transitions.contains(state)) {
                return true;
            }
        }

        return false;
    }

    private boolean isRequiredReset(NodeRef activityRef, String fromState, String toState) {
        if (!CaseActivity.State.NOT_STARTED.getContent().equals(fromState) &&
            CaseActivity.State.STARTED.getContent().equals(toState)) {
            Boolean repeatable = (Boolean) nodeService.getProperty(activityRef, ActivityModel.PROP_REPEATABLE);
            return Boolean.TRUE.equals(repeatable);
        }
        return false;
    }

    private String getActivityState(String activityId) {
        NodeRef activityRef = new NodeRef(activityId);
        String state = (String) nodeService.getProperty(activityRef, LifeCycleModel.PROP_STATE);
        return state != null ? state : CaseActivity.State.NOT_STARTED.getContent();
    }

    private void mandatoryActivity(String paramName, String activityId) {

        mandatoryParameter(paramName, activityId);
        mandatoryNodeRef(paramName, new NodeRef(activityId));

        NodeRef nodeRef = new NodeRef(activityId);
        QName type = nodeService.getType(nodeRef);
        if (!dictionaryService.isSubClass(type, ActivityModel.TYPE_ACTIVITY)) {
            throw new IllegalArgumentException(paramName + " must inherit activ:activity");
        }
    }

    /*
     * This is method for check existing ActivityID in NodeService
     * When all dependencies to Alfresco is gone, we can remove it and check only with mandatoryParameter()
     **/
    private void mandatoryNodeRef(String paramName, Object value) {
        if (value instanceof NodeRef && !nodeService.exists((NodeRef) value)) {
            throw new IllegalArgumentException("Parameter " + paramName + " have incorrect " +
                "NodeRef: " + value + ". The node doesn't exists.");
        }
    }

    private void mandatoryParameter(String paramName, Object value) {
        if (value == null) {
            throw new IllegalArgumentException(paramName + " is a mandatory parameter");
        }
    }

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setDictionaryService(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

}