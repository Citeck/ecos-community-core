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
package ru.citeck.ecos.action;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.action.executer.ActionExecuterAbstractBase;
import org.alfresco.repo.admin.SysAdminParams;
import org.alfresco.repo.jscript.ScriptAction;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.ScriptService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.util.UrlUtil;
import ru.citeck.ecos.service.AlfrescoServices;

public class ScriptContentActionExecuter extends ActionExecuterAbstractBase
{
    public static final String NAME = "script-auto-execute";
    
    private ServiceRegistry serviceRegistry;
    private SysAdminParams sysAdminParams;
    private String companyHomePath;
    private StoreRef storeRef;

    /**
     * @param serviceRegistry       The serviceRegistry to set.
     */
    public void setServiceRegistry(ServiceRegistry serviceRegistry)
    {
        this.serviceRegistry = serviceRegistry;
    }
    
    /**
     * @param sysAdminParams The sysAdminParams to set.
     */
    public void setSysAdminParams(SysAdminParams sysAdminParams)
    {
        this.sysAdminParams = sysAdminParams;
    }

    public void setStoreUrl(String storeUrl)
    {
        this.storeRef = new StoreRef(storeUrl);
    }

    public void setCompanyHomePath(String companyHomePath)
    {
        this.companyHomePath = companyHomePath;
    }
    
    /**
     * Allow adhoc properties to be passed to this action
     * 
     * @see org.alfresco.repo.action.ParameterizedItemAbstractBase#getAdhocPropertiesAllowed()
     */
    protected boolean getAdhocPropertiesAllowed()
    {
        return true;
    }

    /**
     * @see org.alfresco.repo.action.executer.ActionExecuterAbstractBase#executeImpl(org.alfresco.service.cmr.action.Action, org.alfresco.service.cmr.repository.NodeRef)
     */
    protected void executeImpl(Action action, NodeRef actionedUponNodeRef)
    {
        NodeService nodeService = this.serviceRegistry.getNodeService();
        if (nodeService.exists(actionedUponNodeRef))
        {
            NodeRef spaceRef = this.serviceRegistry.getRuleService().getOwningNodeRef(action);
            
            // get the references we need to build the default scripting data-model
            String userName = this.serviceRegistry.getAuthenticationService().getCurrentUserName();
            NodeRef personRef = this.serviceRegistry.getPersonService().getPerson(userName);
            NodeRef homeSpaceRef = (NodeRef)nodeService.getProperty(personRef, ContentModel.PROP_HOMEFOLDER);
            
            // the default scripting model provides access to well known objects and searching
            // facilities - it also provides basic create/update/delete/copy/move services
            Map<String, Object> model = getScriptService().buildDefaultModel(
                    personRef,
                    getCompanyHome(),
                    homeSpaceRef,
                    actionedUponNodeRef,
                    actionedUponNodeRef,
                    spaceRef);
            
            // Add the action to the default model
            ScriptAction scriptAction = new ScriptAction(this.serviceRegistry, action, this.actionDefinition);
            model.put("action", scriptAction);
            model.put("webApplicationContextUrl", UrlUtil.getAlfrescoUrl(sysAdminParams)); 

            Object result = getScriptService().executeScript(actionedUponNodeRef, ContentModel.PROP_CONTENT, model);
            if (result != null)
            {
                action.setParameterValue(PARAM_RESULT, (Serializable)result);
            }
        }
    }

    private ScriptService getScriptService() {
        return (ScriptService) serviceRegistry.getService(AlfrescoServices.SCRIPT_SERVICE);
    }
    
    /**
     * @see org.alfresco.repo.action.ParameterizedItemAbstractBase#addParameterDefinitions(java.util.List)
     */
    protected void addParameterDefinitions(List<ParameterDefinition> paramList)
    {
    }
    
    /**
     * Gets the company home node
     * 
     * @return  the company home node ref
     */
    private NodeRef getCompanyHome()
    {
        NodeRef companyHomeRef;
        
        List<NodeRef> refs = this.serviceRegistry.getSearchService().selectNodes(
                this.serviceRegistry.getNodeService().getRootNode(storeRef),
                companyHomePath,
                null,
                this.serviceRegistry.getNamespaceService(),
                false);
        if (refs.size() != 1)
        {
            throw new IllegalStateException("Invalid company home path: " + companyHomePath + " - found: " + refs.size());
        }
        companyHomeRef = refs.get(0);

        return companyHomeRef;
    }
}
