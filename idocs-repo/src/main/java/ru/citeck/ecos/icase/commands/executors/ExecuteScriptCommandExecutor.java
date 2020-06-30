package ru.citeck.ecos.icase.commands.executors;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.admin.SysAdminParams;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.ScriptService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.AuthenticationService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.util.UrlUtil;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.action.ActionConditionUtils;
import ru.citeck.ecos.commands.CommandExecutor;
import ru.citeck.ecos.commands.CommandsService;
import ru.citeck.ecos.icase.commands.dto.ExecuteScriptCommand;
import ru.citeck.ecos.records.RecordsUtils;
import ru.citeck.ecos.service.AlfrescoServices;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

@Component
public class ExecuteScriptCommandExecutor implements CommandExecutor<ExecuteScriptCommand> {

    public static final String TYPE = "execute-script";

    private CommandsService commandsService;

    private AuthenticationService authenticationService;
    private NamespaceService namespaceService;
    private SysAdminParams sysAdminParams;
    private PersonService personService;
    private ScriptService scriptService;
    private SearchService searchService;
    private NodeService nodeService;

    private String companyHomePath;

    @Autowired
    public ExecuteScriptCommandExecutor(CommandsService commandsService,
                                        ServiceRegistry serviceRegistry,
                                        @Value("/${spaces.company_home.childname}") String companyHomePath) {
        this.commandsService = commandsService;
        this.authenticationService = serviceRegistry.getAuthenticationService();
        this.namespaceService = serviceRegistry.getNamespaceService();
        this.sysAdminParams = serviceRegistry.getSysAdminParams();
        this.personService = serviceRegistry.getPersonService();
        this.scriptService = (ScriptService) serviceRegistry.getService(AlfrescoServices.SCRIPT_SERVICE);
        this.searchService = serviceRegistry.getSearchService();
        this.nodeService = serviceRegistry.getNodeService();
        this.companyHomePath = companyHomePath;
    }

    @PostConstruct
    public void init() {
        commandsService.addExecutor(this);
    }

    @Nullable
    @Override
    public Object execute(ExecuteScriptCommand command) {
        NodeRef caseRef = RecordsUtils.toNodeRef(command.getCaseRef());
        String script = command.getScript();

        Map<String, Object> model = buildDefaultModel(caseRef);
        addContextVariables(model);

        scriptService.executeScriptString(script, model);
        return null;
    }

    private Map<String, Object> buildDefaultModel(NodeRef caseRef) {
        String userName = authenticationService.getCurrentUserName();
        NodeRef personRef = personService.getPerson(userName);
        NodeRef homeSpaceRef = (NodeRef) nodeService.getProperty(personRef, ContentModel.PROP_HOMEFOLDER);

        Map<String, Object> model = scriptService.buildDefaultModel(
                personRef, getCompanyHome(), homeSpaceRef, null, caseRef, null);
        model.put("webApplicationContextUrl", UrlUtil.getAlfrescoUrl(sysAdminParams));

        return model;
    }

    private void addContextVariables(Map<String, Object> model) {
        Map<String, Object> variables = ActionConditionUtils.getTransactionVariables();
        for (Map.Entry<String, Object> variable : variables.entrySet()) {
            if (model.containsKey(variable.getKey())) {
                throw new AlfrescoRuntimeException(String.format("Error occurred during reading context variables. " +
                        "Variable \"%s\" is already defined and you can't override it.", variable.getKey()));
            }
            model.put(variable.getKey(), variable.getValue());
        }
    }

    private NodeRef getCompanyHome() {
        List<NodeRef> refs = searchService.selectNodes(
                nodeService.getRootNode(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE),
                companyHomePath,
                null,
                namespaceService,
                false);
        if (refs.size() != 1) {
            throw new IllegalStateException("Invalid company home path: " + companyHomePath + " - found: " + refs.size());
        }

        return refs.get(0);
    }
}
