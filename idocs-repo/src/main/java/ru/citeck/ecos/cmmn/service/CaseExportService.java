package ru.citeck.ecos.cmmn.service;

import lombok.SneakyThrows;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import ru.citeck.ecos.cmmn.CMMNUtils;
import ru.citeck.ecos.cmmn.CmmnExportImportException;
import ru.citeck.ecos.cmmn.model.Case;
import ru.citeck.ecos.cmmn.model.Definitions;
import ru.citeck.ecos.cmmn.model.ObjectFactory;
import ru.citeck.ecos.cmmn.service.util.CasePlanModelExport;
import ru.citeck.ecos.cmmn.service.util.CaseRolesExport;
import ru.citeck.ecos.content.dao.xml.XmlContentDAO;
import ru.citeck.ecos.icase.activity.service.CaseActivityService;
import ru.citeck.ecos.model.ICaseModel;
import ru.citeck.ecos.model.ICaseTemplateModel;
import ru.citeck.ecos.utils.AlfActivityUtils;
import ru.citeck.ecos.utils.RepoUtils;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static ru.citeck.ecos.model.ICaseTemplateModel.ASSOC_ELEMENT_CONFIG;

/**
 * Export-service for Cases in CMMN standard version 1.1 beta
 * 2016-06-16
 *
 * @author deathNC
 * @author Maxim Strizhov (maxim.strizhov@citeck.ru)
 * @author Pavel Simonov (pavel.simonov@citeck.ru)
 */
public class CaseExportService {

    private final Logger log = LoggerFactory.getLogger(CaseExportService.class);

    private NodeService nodeService;
    private DictionaryService dictionaryService;
    private ObjectFactory objectFactory;
    private CaseActivityService caseActivityService;

    @Autowired
    private CMMNUtils utils;
    @Autowired
    @Qualifier("caseTemplateContentDAO")
    private XmlContentDAO<Definitions> configDAO;
    @Autowired
    private AlfActivityUtils alfActivityUtils;

    public void init() {
        objectFactory = new ObjectFactory();
    }

    /**
     * Returns marshaled case xml data as byte array
     */
    public byte[] exportCase(NodeRef caseNodeRef) {

        Definitions definitions = exportCaseDefinition(caseNodeRef);
        try {
            return writeToBytes(definitions);
        } catch (Throwable t) {
            log.error("Cannot export node: " + caseNodeRef, t);
            throw new CmmnExportImportException("Cannot export node: " + caseNodeRef, t);
        }
    }

    @SneakyThrows
    public byte[] writeToBytes(Definitions definitions) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        configDAO.write(definitions, out);
        return out.toByteArray();
    }

    public Definitions exportCaseDefinition(NodeRef caseNodeRef) {
        try {
            Map<QName, Serializable> sourcePropertyMap = nodeService.getProperties(caseNodeRef);

            Case caseItem = new Case();

            for (Map.Entry<javax.xml.namespace.QName, QName> mapping : CMMNUtils.CASE_ATTRIBUTES_MAPPING.entrySet()) {
                Serializable value = sourcePropertyMap.get(mapping.getValue());
                if (value != null) {
                    String valueStr = utils.convertValueForCmmn(mapping.getValue(), value);
                    caseItem.getOtherAttributes().put(mapping.getKey(), valueStr);
                }
            }
            caseItem.getOtherAttributes().put(CMMNUtils.QNAME_ELEMENT_TYPES, getElementTypesForCase(caseNodeRef));

            caseItem.setId(utils.convertNodeRefToId(caseNodeRef));
            caseItem.setName(sourcePropertyMap.get(ContentModel.PROP_NAME).toString());

            caseItem.setCaseRoles(new CaseRolesExport(nodeService, dictionaryService, utils).getRoles(caseNodeRef));
            CasePlanModelExport casePlanModelExport = new CasePlanModelExport(nodeService, caseActivityService,
                this, dictionaryService, utils, alfActivityUtils);
            caseItem.setCasePlanModel(casePlanModelExport.getCasePlanModel(caseNodeRef, caseItem.getCaseRoles()));

            Definitions definitions = new Definitions();
            definitions.getCase().add(caseItem);
            definitions.setTargetNamespace(CMMNUtils.NAMESPACE);

            return definitions;

        } catch (Throwable t) {
            log.error("Cannot export node: " + caseNodeRef, t);
            throw new CmmnExportImportException("Cannot export node: " + caseNodeRef, t);
        }
    }

    void exportCaseToFile(NodeRef nodeRef, String fileName) {
        byte[] xmlData = exportCase(nodeRef);
        try {
            Files.write(Paths.get(fileName), xmlData);
        } catch (Exception e) {
            log.error("Cannot save xml-data to disk", e);
        }
    }

    private String getElementTypesForCase(NodeRef caseRef) {
        List<NodeRef> types = getCaseElementTypes(caseRef);
        String[] typesArr = new String[types.size()];
        for (int i = 0; i < typesArr.length; i++) {
            List<NodeRef> elementConfig = RepoUtils.getTargetAssoc(types.get(i), ASSOC_ELEMENT_CONFIG, nodeService);
            if (elementConfig != null && !elementConfig.isEmpty()) {
                typesArr[i] = (String) nodeService.getProperty(elementConfig.get(0), ContentModel.PROP_NAME);
            }
        }
        return StringUtils.join(typesArr, ",");
    }

    private List<NodeRef> getCaseElementTypes(NodeRef templateRef) {
        return RepoUtils.getChildrenByAssoc(templateRef, ICaseTemplateModel.ASSOC_ELEMENT_TYPES, nodeService);
    }

    public NodeRef getElementTypeByConfig(NodeRef templateRef, QName configType) {
        List<NodeRef> elementTypes = getCaseElementTypes(templateRef);
        for (NodeRef elementType : elementTypes) {
            List<NodeRef> elementConfig = RepoUtils.getTargetAssoc(elementType, ASSOC_ELEMENT_CONFIG, nodeService);
            if (elementConfig != null && !elementConfig.isEmpty()) {
                QName configQName = (QName) nodeService.getProperty(elementConfig.get(0), ICaseModel.PROP_ELEMENT_TYPE);
                if (configQName.equals(configType)) {
                    return  elementType;
                }
            }
        }
        return null;
    }

    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setCaseActivityService(CaseActivityService caseActivityService) {
        this.caseActivityService = caseActivityService;
    }

    public void setDictionaryService(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }
}
