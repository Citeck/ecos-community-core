package ru.citeck.ecos.action.v2;

import com.fasterxml.jackson.databind.node.TextNode;
import ecos.guava30.com.google.common.cache.CacheBuilder;
import ecos.guava30.com.google.common.cache.CacheLoader;
import ecos.guava30.com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.MLPropertyInterceptor;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.MLText;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.I18NUtil;
import ru.citeck.ecos.action.ActionModule;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.CDLModel;
import ru.citeck.ecos.model.DmsModel;
import ru.citeck.ecos.search.ftsquery.FTSQuery;
import ru.citeck.ecos.template.CardTemplateService;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
//@Component
public class CardTemplateActionsProvider implements NodeActionsV2Provider {

    private static final String URL = "/share/proxy/alfresco/citeck/print/metadata-printpdf" +
                                                                            "?nodeRef=%s" +
                                                                            "&templateType=%s" +
                                                                            "&print=true" +
                                                                            "&format=%s";

    private NodeService nodeService;
    private SearchService searchService;
    private CardTemplateService templateService;

    private LoadingCache<String, MLText> titleByTemplateType;

    private CardTemplateActionsProvider() {
        titleByTemplateType = CacheBuilder.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build(CacheLoader.from(this::getTemplateTypeTitleAsSystem));
    }

    private MLText getTemplateTypeTitleAsSystem(String type) {
        return AuthenticationUtil.runAsSystem(() -> getTemplateTypeTitleImpl(type));
    }

    private MLText getTemplateTypeTitleImpl(String type) {

        MLPropertyInterceptor.setMLAware(true);
        try {

            //For some reasons search by TYPE AND =cm:name doesn't work in transactional mode
            //so we search all types and filter by name after that
            Map<QName, Serializable> typeProps;
            typeProps = FTSQuery.create()
                                .type(CDLModel.TYPE_CARD_TEMPLATE_TYPE).and()
                                .exact(ContentModel.PROP_NAME, type)
                                .transactional()
                                .query(searchService)
                                .stream()
                                .map(nodeService::getProperties)
                                .filter(props -> Objects.equals(props.get(ContentModel.PROP_NAME), type))
                                .findFirst()
                                .orElse(null);

            if (typeProps == null) {
                return new MLText(type);
            }

            Serializable rawTitle = typeProps.get(ContentModel.PROP_TITLE);
            if (rawTitle instanceof MLText) {
                return (MLText) rawTitle;
            } else if (rawTitle == null) {
                return new MLText(type);
            } else {
                return new MLText(rawTitle.toString());
            }
        } finally {
            MLPropertyInterceptor.setMLAware(false);
        }
    }

    @Override
    public List<ActionModule> getActions(NodeRef nodeRef) {

        if (!nodeService.exists(nodeRef)) {
            return Collections.emptyList();
        }

        QName type = nodeService.getType(nodeRef);

        List<NodeRef> templatesForDocType = templateService.getTemplatesForDocType(type);

        List<ActionModule> result = new ArrayList<>();

        for (NodeRef template : templatesForDocType) {

            Map<QName, Serializable> props = nodeService.getProperties(template);
            String templateType = (String) props.get(DmsModel.PROP_TEMPLATE_TYPE);

            if (templateType == null || templateType.isEmpty())  {
                continue;
            }
        }

        return result;
    }

    private ActionModule createDownloadAction(NodeRef nodeRef, String templateType, String format) {

        ActionModule action = new ActionModule();

        action.setName(templateType);

        try {

            MLText mlTitle = titleByTemplateType.get(templateType);
            String title = mlTitle.getClosestValue(I18NUtil.getLocale());
            title += " (" + format + ")";
            action.setName(title);

        } catch (ExecutionException e) {
            log.warn("Title can't be received: ", e);
            action.setName(templateType);
        }

        action.setKey("card-template." + templateType + "." + format);
        action.setType("download");

        String url = String.format(URL, nodeRef.toString(), templateType, format);

        ObjectData config = ObjectData.create();
        config.set("url", TextNode.valueOf(url));
        config.set("filename", TextNode.valueOf("template." + format));

        action.setConfig(config);

        return action;
    }

    @Override
    public String getScope() {
        return "card-template";
    }

    @Autowired
    public void setTemplateService(CardTemplateService templateService) {
        this.templateService = templateService;
    }

    @Autowired
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.nodeService = serviceRegistry.getNodeService();
        this.searchService = serviceRegistry.getSearchService();
    }
}
