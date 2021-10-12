package ru.citeck.ecos.flowable.scripts;

import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.ScriptService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.workflow.WorkflowException;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.impl.bpmn.listener.ScriptExecutionListener;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.context.Context;
import org.flowable.variable.api.delegate.VariableScope;
import ru.citeck.ecos.flowable.constants.FlowableConstants;
import ru.citeck.ecos.records.script.RepoScriptRecordsService;
import ru.citeck.ecos.service.AlfrescoServices;

import java.util.HashMap;
import java.util.Map;

/**
 * Flowable script base
 */
public class FlowableScriptBase extends ScriptExecutionListener {

    /**
     * Constants
     */
    protected static final String PERSON_BINDING_NAME = "person";
    protected static final String USERHOME_BINDING_NAME = "userhome";
    protected static final String EXECUTION_BINDING_NAME = "execution";
    protected static final String DOCUMENT = "document";
    protected static final String RECORD = "record";

    private static RepoScriptRecordsService repoScriptRecordsService;

    /**
     * Run as expression
     */
    protected Expression runAs;

    /**
     * Get script language
     *
     * @return Script language
     */
    protected String getLanguage() {
        if (this.language != null) {
            return this.language.getExpressionText();
        } else {
            return "javascript";
        }
    }

    /**
     * Validate parameters
     */
    protected void validateParameters() {
        if (this.script == null) {
            throw new IllegalArgumentException("The field 'script' should be set on the ExecutionListener");
        }
    }

    /**
     * Execute script
     *
     * @param theScript           Script string
     * @param model               Script model
     * @param scriptProcessorName Script engine
     * @param runAsUser           Run as user
     * @return Execution result
     */
    protected Object executeScript(String theScript, Map<String, Object> model, String scriptProcessorName, String runAsUser) {

        String user = AuthenticationUtil.getFullyAuthenticatedUser();

        Object scriptResult = null;
        if (runAsUser == null && user != null) {
            scriptResult = executeScript(theScript, model, scriptProcessorName);
        } else {
            if (runAsUser != null) {
                validateRunAsUser(runAsUser);
            } else {
                runAsUser = AuthenticationUtil.getSystemUserName();
            }
            executeScriptAsUser(theScript, model, scriptProcessorName, runAsUser);
        }
        return scriptResult;
    }

    /**
     * Execute script as user
     *
     * @param theScript           Script string
     * @param model               Script model
     * @param scriptProcessorName Script engine
     * @param runAsUser           Run as user
     * @return Execution result
     */
    protected Object executeScriptAsUser(final String theScript, final Map<String, Object> model,
                                         final String scriptProcessorName, final String runAsUser) {
        return AuthenticationUtil.runAs(() -> executeScript(theScript, model, scriptProcessorName), runAsUser);
    }

    /**
     * Execute script
     *
     * @param theScript           Script string
     * @param model               Script model
     * @param scriptProcessorName Script engine
     * @return Execution result
     */
    protected Object executeScript(String theScript, Map<String, Object> model, String scriptProcessorName) {
        Object scriptResult;
        model = prepareModelBeforeExecution(model);
        if (scriptProcessorName != null) {
            scriptResult = getScriptService().executeScriptString(scriptProcessorName, theScript, model);
        } else {
            scriptResult = getScriptService().executeScriptString(theScript, model);
        }
        return scriptResult;
    }

    private ScriptService getScriptService() {
        return (ScriptService) getServiceRegistry().getService(AlfrescoServices.SCRIPT_SERVICE);
    }

    /**
     * Get string value
     *
     * @param expression Expression
     * @param scope      Scope
     * @return String value
     */
    protected String getStringValue(Expression expression, VariableScope scope) {
        if (expression != null) {
            return expression.getExpressionText();
        }
        return null;
    }

    /**
     * Checks that the specified 'runAs' field
     * specifies a valid username.
     *
     * @param runAsUser Run as user
     */
    private void validateRunAsUser(final String runAsUser) {
        Boolean runAsExists = AuthenticationUtil.runAs(() -> getServiceRegistry().getPersonService().personExists(runAsUser), AuthenticationUtil.getSystemUserName());

        if (!runAsExists) {
            throw new WorkflowException("Run as user '" + runAsUser + "' does not exist.");
        }
    }

    protected Map<String, Object> prepareModelBeforeExecution(Map<String, Object> model) {
        Map<String, Object> newModel = new HashMap<>(model);
        Object document = model.get("document");
        if (document != null) {
            newModel.put(RECORD, getRepoScriptRecordsService().get(document));
        }
        return newModel;
    }

    /**
     * Get person node reference
     *
     * @param runAsUser Run as user
     * @return Person node reference
     */
    protected NodeRef getPersonNode(String runAsUser) {
        String userName = null;
        if (runAsUser != null) {
            userName = runAsUser;
        } else {
            userName = AuthenticationUtil.getFullyAuthenticatedUser();
        }

        // Load person node
        if (userName != null && !AuthenticationUtil.SYSTEM_USER_NAME.equals(userName)) {
            ServiceRegistry services = getServiceRegistry();
            PersonService personService = services.getPersonService();
            if (personService.personExists(userName)) {
                return personService.getPerson(userName);
            }
        }
        return null;
    }

    /**
     * Get service registry
     *
     * @return Service registry
     */
    protected ServiceRegistry getServiceRegistry() {
        ProcessEngineConfigurationImpl config = Context.getProcessEngineConfiguration();
        // Check engine configuration
        if (config != null) {
            ServiceRegistry registry = (ServiceRegistry) config.getBeans().get(FlowableConstants.SERVICE_REGISTRY_BEAN_KEY);
            if (registry == null) {
                throw new RuntimeException(
                        "ServiceRegistry not present in Flowable ProcessEngineConfiguration beans");
            }
            return registry;
        }
        throw new IllegalStateException("No ProcessEngineConfiguration found in active context");
    }

    protected RepoScriptRecordsService getRepoScriptRecordsService() {
        if (repoScriptRecordsService == null) {
            repoScriptRecordsService =
                (RepoScriptRecordsService) getServiceRegistry().getService(RepoScriptRecordsService.QNAME);
        }
        if (repoScriptRecordsService == null) {
            throw new RuntimeException("RepoScriptRecordsService bean not found!");
        }
        return repoScriptRecordsService;
    }

    public void setRunAs(Expression runAs) {
        this.runAs = runAs;
    }
}
