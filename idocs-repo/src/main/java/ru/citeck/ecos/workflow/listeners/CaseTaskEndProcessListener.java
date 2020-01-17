/*
 * Copyright (C) 2008-2015 Citeck LLC.
 *
 * This file is part of Citeck EcoS
 *
 * Citeck EcoS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Citeck EcoS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Citeck EcoS. If not, see <http://www.gnu.org/licenses/>.
 */
package ru.citeck.ecos.workflow.listeners;

import org.activiti.engine.delegate.DelegateExecution;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.AssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import ru.citeck.ecos.action.ActionConditionUtils;
import ru.citeck.ecos.icase.activity.CaseActivityService;
import ru.citeck.ecos.model.CiteckWorkflowModel;
import ru.citeck.ecos.model.ICaseTaskModel;
import ru.citeck.ecos.service.CiteckServices;

import java.util.List;

/**
 * @author Maxim Strizhov
 */
public class CaseTaskEndProcessListener extends AbstractExecutionListener {

    private static final Log log = LogFactory.getLog(CaseTaskEndProcessListener.class);

    private CaseActivityService caseActivityService;
    private NodeService nodeService;
    private WorkflowDocumentResolverRegistry documentResolverRegistry;

    @Override
    protected void notifyImpl(final DelegateExecution delegateExecution) throws Exception {
        AuthenticationUtil.runAsSystem(() -> {
            CaseTaskEndProcessListener.this.doWork(delegateExecution);
            return null;
        });
    }

    private void doWork(DelegateExecution delegateExecution) {
        if (documentResolverRegistry.getResolver(delegateExecution).getDocument(delegateExecution) == null) {
            return;
        }
        stopActivity(delegateExecution);
    }

    private void stopActivity(DelegateExecution delegateExecution) {

        NodeRef bpmPackage = ListenerUtils.getWorkflowPackage(delegateExecution);
        nodeService.setProperty(bpmPackage, CiteckWorkflowModel.PROP_IS_WORKFLOW_ACTIVE, false);

        List<AssociationRef> packageAssocs = nodeService.getSourceAssocs(bpmPackage, ICaseTaskModel.ASSOC_WORKFLOW_PACKAGE);

        if (packageAssocs != null && !packageAssocs.isEmpty()) {
            ActionConditionUtils.getProcessVariables().putAll(delegateExecution.getVariables());
            caseActivityService.stopActivity(packageAssocs.get(0).getSourceRef());
        }
    }

    @Override
    protected void initImpl() {
        this.nodeService = serviceRegistry.getNodeService();
        this.caseActivityService = (CaseActivityService) serviceRegistry.getService(CiteckServices.CASE_ACTIVITY_SERVICE);
        documentResolverRegistry = getBean(WorkflowDocumentResolverRegistry.BEAN_NAME, WorkflowDocumentResolverRegistry.class);
    }
}
