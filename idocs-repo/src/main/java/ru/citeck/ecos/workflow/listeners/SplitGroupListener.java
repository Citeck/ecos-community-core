package ru.citeck.ecos.workflow.listeners;

import org.activiti.engine.delegate.DelegateExecution;
import org.activiti.engine.delegate.Expression;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.workflow.activiti.ActivitiNodeConverter;
import org.alfresco.repo.workflow.activiti.ActivitiScriptNode;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AuthorityService;
import ru.citeck.ecos.providers.ApplicationContextProvider;
import ru.citeck.ecos.utils.AuthorityUtils;

import java.util.*;
import java.util.stream.Collectors;

public class SplitGroupListener extends AbstractExecutionListener {

    private NodeService nodeService;
    private AuthorityService authorityService;
    private AuthorityUtils authorityUtils;
    private ActivitiNodeConverter nodeConverter;

    private String varGroup;
    private String varIsSplit;

    @Override
    protected void notifyImpl(DelegateExecution execution) {

        Object performers = execution.getVariable(varGroup);
        Object isGroupSplit = execution.getVariable(varIsSplit);
        if (isGroupSplit instanceof Boolean && (Boolean) isGroupSplit) {
            if (performers instanceof List) {
                List<NodeRef> resultList = new ArrayList<>();
                ((List) performers).forEach(performer -> {
                    NodeRef performerRef = extractNodeRef(performer);
                    if (performer != null) {
                        if (nodeService.getType(performerRef).equals(ContentModel.TYPE_PERSON)) {
                            resultList.add(performerRef);
                        } else if (nodeService.getType(performerRef)
                            .equals(ContentModel.TYPE_AUTHORITY_CONTAINER)) {
                            Set<String> containedUsers = authorityUtils.getContainedUsers(performerRef, false);
                            resultList.addAll(containedUsers
                                .stream()
                                .map(userName -> authorityService.getAuthorityNodeRef(userName))
                                .collect(Collectors.toList()));
                        }
                    }
                });
                execution.setVariable(varGroup, nodeConverter.convertNodes(resultList
                    .stream()
                    .distinct()
                    .collect(Collectors.toList())));
            }
        }
    }

    private NodeRef extractNodeRef(Object node) {
        if (node instanceof ActivitiScriptNode) {
            return ((ActivitiScriptNode) node).getNodeRef();
        } else if (node instanceof NodeRef) {
            return (NodeRef) node;
        }
        return null;
    }

    @Override
    protected void initImpl() {
        nodeService = serviceRegistry.getNodeService();
        authorityService = serviceRegistry.getAuthorityService();
        nodeConverter = new ActivitiNodeConverter(serviceRegistry);
        authorityUtils = ApplicationContextProvider.getBean(AuthorityUtils.class);
    }

    public void setVarGroup(Expression varGroup) {
        this.varGroup = varGroup.getExpressionText();
    }

    public void setVarIsSplit(Expression varIsSplit) {
        this.varIsSplit = varIsSplit.getExpressionText();
    }
}
