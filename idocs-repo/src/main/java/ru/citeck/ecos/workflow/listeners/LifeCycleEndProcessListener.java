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
import org.activiti.engine.impl.context.Context;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import ru.citeck.ecos.lifecycle.LifeCycleService;
import ru.citeck.ecos.service.CiteckServices;
import ru.citeck.ecos.workflow.utils.ActivitiVariableScopeMap;

/**
 * @author: Alexander Nemerov
 * @date: 13.03.14
 */
public class LifeCycleEndProcessListener extends AbstractExecutionListener {

    private LifeCycleService lifeCycleService;
    private WorkflowDocumentResolverRegistry documentResolverRegistry;

    @Override
    protected void notifyImpl(final DelegateExecution delegateExecution) throws Exception {
        AuthenticationUtil.runAsSystem(() -> {
            NodeRef docRef = documentResolverRegistry.getResolver(delegateExecution).getDocument(delegateExecution);
            if (docRef == null) {
                return null;
            }
            String definitionId = "activiti$" + Context.getExecutionContext().getProcessDefinition().getKey();
            lifeCycleService.doTransitionOnEndProcess(docRef, definitionId,
                    new ActivitiVariableScopeMap(delegateExecution, serviceRegistry));
            return null;
        });
    }

    @Override
    protected void initImpl() {
        this.lifeCycleService = (LifeCycleService) serviceRegistry.getService(CiteckServices.LIFECYCLE_SERVICE);
        documentResolverRegistry = getBean(WorkflowDocumentResolverRegistry.BEAN_NAME, WorkflowDocumentResolverRegistry.class);
    }

}
