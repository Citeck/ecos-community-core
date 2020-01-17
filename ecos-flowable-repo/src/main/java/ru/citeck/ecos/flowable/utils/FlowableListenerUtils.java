package ru.citeck.ecos.flowable.utils;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.repo.workflow.WorkflowConstants;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.workflow.WorkflowTask;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.service.IdentityLinkType;
import org.flowable.task.service.delegate.DelegateTask;
import org.flowable.variable.api.delegate.VariableScope;
import ru.citeck.ecos.utils.ReflectionUtils;

import java.util.*;

/**
 * Flowable listener utils
 */
public class FlowableListenerUtils {
    /**
     * Constants
     */
    public static final String VAR_PACKAGE = "bpm_package";
    public static final String VAR_ATTACHMENTS = "cwf_taskAttachments";

    /**
     * Get workflow package node
     *
     * @param execution Execution
     * @return Node reference
     */
    public static NodeRef getWorkflowPackage(VariableScope execution) {
        return (NodeRef) execution.getVariable(VAR_PACKAGE);
    }

    /**
     * Get workflow package node
     *
     * @param task Workflow task
     * @return Node reference
     */
    public static NodeRef getWorkflowPackage(WorkflowTask task) {
        return (NodeRef) task.getProperties().get(WorkflowModel.TYPE_PACKAGE);
    }

    /**
     * Get document node reference by execution
     *
     * @param execution   Execution
     * @param nodeService Node service
     * @return Document node reference
     */
    public static NodeRef getDocument(VariableScope execution, NodeService nodeService) {
        NodeRef wfPackage = getWorkflowPackage(execution);
        if (wfPackage == null || !nodeService.exists(wfPackage)) {
            return null;
        }
        List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(wfPackage, WorkflowModel.ASSOC_PACKAGE_CONTAINS,
                RegexQNamePattern.MATCH_ALL);
        if (!childAssocs.isEmpty()) {
            return childAssocs.get(0).getChildRef();
        }
        childAssocs = nodeService.getChildAssocs(wfPackage, ContentModel.ASSOC_CONTAINS, RegexQNamePattern.MATCH_ALL);
        if (!childAssocs.isEmpty()) {
            return childAssocs.get(0).getChildRef();
        }
        return null;
    }

    /**
     * Get initiator username
     *
     * @param execution Execution
     * @return Initiator username
     */
    //TODO: script node and simple node
    public static String getInitiator(VariableScope execution) {
        return (String) ((ScriptNode) execution.getVariable(WorkflowConstants.PROP_INITIATOR)).getProperties().get(ContentModel.PROP_USERNAME);
    }

    /**
     * Get pooled actors
     *
     * @param task             Task
     * @param authorityService Authority service
     * @return List of actors node reference
     */
    public static ArrayList<NodeRef> getPooledActors(DelegateTask task, AuthorityService authorityService) {
        Set<IdentityLink> candidates = (Set) ReflectionUtils.callGetterIfDeclared(task, "getCandidates", new HashSet());
        ArrayList<NodeRef> pooledActors = new ArrayList<>(candidates.size());
        for (IdentityLink candidate : candidates) {
            if (!candidate.getType().equals(IdentityLinkType.CANDIDATE)) {
                continue;
            }
            String userId = candidate.getUserId();
            if (userId != null) {
                NodeRef person = authorityService.getAuthorityNodeRef(userId);
                if (person != null) {
                    pooledActors.add(person);
                }
            }

            String groupId = candidate.getGroupId();
            if (groupId != null) {
                NodeRef group = authorityService.getAuthorityNodeRef(groupId);
                if (group != null) {
                    pooledActors.add(group);
                }
            }
        }
        return pooledActors;
    }

    /**
     * Get task attachments
     *
     * @param task Task
     * @return List of task attachments
     */
    public static ArrayList<NodeRef> getTaskAttachments(DelegateTask task) {
        Object taskAttachments = task.getVariable(VAR_ATTACHMENTS);
        if (!(taskAttachments instanceof Collection)) {
            return null;
        }
        @SuppressWarnings("rawtypes")
        Collection source = (Collection) taskAttachments;
        ArrayList<NodeRef> target = new ArrayList<>(source.size());
        for (Object item : source) {
            if (item == null) {
                // empty
            } else if (item instanceof NodeRef) {
                target.add((NodeRef) item);
            } else if (item instanceof ScriptNode) {
                target.add(((ScriptNode) item).getNodeRef());
            } else if (item instanceof String) {
                target.add(new NodeRef((String) item));
            } else {
                throw new IllegalArgumentException("Unsupported task attachment class: " + item.getClass());
            }
        }
        return target;
    }

    public static boolean getBooleanFromExpressionOrDefault(Expression exp, VariableScope scope, boolean defaultValue) {
        if (exp == null) {
            return defaultValue;
        }

        final String expText = exp.getExpressionText();
        if (Boolean.TRUE.toString().equals(expText) || Boolean.FALSE.toString().equals(expText)) {
            return Boolean.valueOf(expText);
        }

        return (boolean) exp.getValue(scope);
    }
}
