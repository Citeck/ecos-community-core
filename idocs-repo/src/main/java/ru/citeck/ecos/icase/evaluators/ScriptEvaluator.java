package ru.citeck.ecos.icase.evaluators;

import lombok.Data;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.action.ActionConditionUtils;
import ru.citeck.ecos.records.RecordsUtils;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.evaluator.RecordEvaluator;
import ru.citeck.ecos.records2.evaluator.RecordEvaluatorService;
import ru.citeck.ecos.service.AlfrescoServices;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

@Component
public class ScriptEvaluator implements RecordEvaluator<Object, RecordMeta, ScriptEvaluator.Config> {

    public static final String TYPE = "evaluate-script";

    private RecordEvaluatorService recordEvaluatorService;
    private AuthenticationService authenticationService;
    private PersonService personService;
    private SearchService searchService;
    private NamespaceService namespaceService;
    private SysAdminParams sysAdminParams;
    private ScriptService scriptService;
    private NodeService nodeService;

    private String companyHomePath;

    @Autowired
    public ScriptEvaluator(ServiceRegistry serviceRegistry,
                           RecordEvaluatorService recordEvaluatorService,
                           @Value("/${spaces.company_home.childname}") String companyHomePath) {
        this.authenticationService = serviceRegistry.getAuthenticationService();
        this.personService = serviceRegistry.getPersonService();
        this.searchService = serviceRegistry.getSearchService();
        this.namespaceService = serviceRegistry.getNamespaceService();
        this.sysAdminParams = serviceRegistry.getSysAdminParams();
        this.scriptService = (ScriptService) serviceRegistry.getService(AlfrescoServices.SCRIPT_SERVICE);
        this.nodeService = serviceRegistry.getNodeService();
        this.recordEvaluatorService = recordEvaluatorService;
        this.companyHomePath = companyHomePath;
    }

    @PostConstruct
    public void init() {
        recordEvaluatorService.register(this);
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public Object getMetaToRequest(Config config) {
        return null;
    }

    @Override
    public boolean evaluate(RecordMeta meta, Config config) {
        EntityRef recordRef = meta.getId();
        NodeRef nodeRef = RecordsUtils.toNodeRef(recordRef);

        if (!nodeService.exists(nodeRef)) {
            return false;
        }

        // get the references we need to build the default scripting data-model
        String userName = authenticationService.getCurrentUserName();
        NodeRef personRef = personService.getPerson(userName);
        NodeRef homeSpaceRef = (NodeRef) nodeService.getProperty(personRef, ContentModel.PROP_HOMEFOLDER);

        // the default scripting model provides access to well known objects and searching
        // facilities - it also provides basic create/update/delete/copy/move services
        Map<String, Object> model = scriptService.buildDefaultModel(
                personRef, getCompanyHome(), homeSpaceRef, null, nodeRef, null);

        // Add the action to the default model
        model.put("webApplicationContextUrl", UrlUtil.getAlfrescoUrl(sysAdminParams));

        // add context variables
        Map<String, Object> variables = ActionConditionUtils.getTransactionVariables();
        for (Map.Entry<String, Object> variable : variables.entrySet()) {
            if (!model.containsKey(variable.getKey())) {
                model.put(variable.getKey(), variable.getValue());
            } else {
                throw new AlfrescoRuntimeException(String.format(
                        "Error occurred during reading context variables. " +
                                "Variable \"%s\" is already defined and you can't override it.", variable.getKey()));
            }
        }

        Object result = scriptService.executeScriptString(config.script, model);

        return Boolean.TRUE.equals(result);
    }

    private NodeRef getCompanyHome() {
        NodeRef companyHomeRef;

        List<NodeRef> refs = searchService.selectNodes(
                nodeService.getRootNode(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE),
                companyHomePath,
                null,
                namespaceService,
                false);
        if (refs.size() != 1) {
            throw new IllegalStateException("Invalid company home path: " + companyHomePath + " - found: " + refs.size());
        }
        companyHomeRef = refs.get(0);

        return companyHomeRef;
    }

    @Data
    public static class Config {
        private String script;
    }

}
