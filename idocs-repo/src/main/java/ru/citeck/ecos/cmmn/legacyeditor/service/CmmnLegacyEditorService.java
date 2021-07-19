package ru.citeck.ecos.cmmn.legacyeditor.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.cmmn.CMMNUtils;
import ru.citeck.ecos.cmmn.legacyeditor.dao.CmmnLegacyEditorTemplateDao;
import ru.citeck.ecos.cmmn.model.Definitions;
import ru.citeck.ecos.cmmn.service.CaseExportService;
import ru.citeck.ecos.cmmn.service.CaseXmlService;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.content.dao.xml.XmlContentDAO;
import ru.citeck.ecos.icase.element.CaseElementService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.records3.record.atts.schema.annotation.AttName;
import ru.citeck.ecos.records3.record.request.RequestContext;

import javax.xml.namespace.QName;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor(onConstructor_={@Autowired})
public class CmmnLegacyEditorService {

    private final RecordsService recordsService;
    private final CaseXmlService caseXmlService;
    private final CmmnLegacyEditorTemplateDao templateDao;
    private final CaseExportService caseExportService;
    private final RecordsServiceFactory recordsServiceFactory;
    private final CaseElementService caseElementService;

    private XmlContentDAO<Definitions> cmmnXmlContentDao;

    @NotNull
    public NodeRef getTempCaseWithTemplate(@NotNull RecordRef templateRef) {

        TemplateInfo templateInfo = readTemplateInfo(templateRef);
        validateDefinition(templateInfo.definition, templateRef);

        NodeRef newTempCase = templateDao.createTempCase(templateRef, templateInfo.ecosTypeRef);
        caseXmlService.copyTemplateToCase(templateInfo.definition, newTempCase);

        return newTempCase;
    }

    public void deleteTempCase(@NotNull NodeRef tempCaseNode) {
        templateDao.deleteTempCaseNode(tempCaseNode);
    }

    public void saveTemplate(@NotNull NodeRef tempCaseNode) {

        RecordRef originalTemplateRef = templateDao.getOriginalTemplateRef(tempCaseNode);

        if (RecordRef.isEmpty(originalTemplateRef)) {
            throw new RuntimeException(
                "Incorrect original template ref: " + originalTemplateRef + " template nodeRef: " + tempCaseNode);
        }

        NodeRef cmmnTemplateNode = templateDao.createTemplateNode(tempCaseNode);
        caseElementService.copyCaseToTemplate(tempCaseNode, cmmnTemplateNode);

        Definitions newDefinition = caseExportService.exportCaseDefinition(cmmnTemplateNode);
        Definitions definitionToSave = readTemplateInfo(originalTemplateRef).definition;

        definitionToSave.getCase().get(0).setCasePlanModel(newDefinition.getCase().get(0).getCasePlanModel());
        definitionToSave.getCase().get(0).setCaseRoles(newDefinition.getCase().get(0).getCaseRoles());
        definitionToSave.getOtherAttributes().put(CMMNUtils.QNAME_PROC_DEF_ID, originalTemplateRef.getId());

        byte[] bytes = caseExportService.writeToBytes(definitionToSave);
        String cmmnTemplate = "data:text/xml;base64," + Base64.getEncoder().encodeToString(bytes);

        ObjectData contentRecData = ObjectData.create();
        contentRecData.set("url", DataValue.createStr(cmmnTemplate));

        RequestContext.doWithCtxJ(recordsServiceFactory, b -> {}, ctx -> {
            recordsService.mutate(
                originalTemplateRef,
                "_content",
                Collections.singletonList(contentRecData)
            );

            if (!ctx.getErrors().isEmpty()) {
                throw new RuntimeException("Template export error! " + ctx.getErrors());
            }

            return null;
        });

        templateDao.deleteTempCaseNode(tempCaseNode);
    }

    private void validateDefinition(Definitions definition, RecordRef templateRef) {

        Map<QName, String> otherAttributes = definition.getCase().get(0).getOtherAttributes();

        // Template validating with "elementTypes" field in <case ... /> tag
        // can be replaced with other validity check
        String elementsType = "";
        for (Map.Entry<QName, String> entry : otherAttributes.entrySet()) {
            if ("elementTypes".equals(entry.getKey().getLocalPart())) {
                elementsType = entry.getValue();
                break;
            }
        }
        if (elementsType.isEmpty()) {
            throw new RuntimeException("elementTypes is not found in template '" + templateRef + "'");
        }
    }

    private TemplateInfo readTemplateInfo(RecordRef templateRef) {

        TemplateAtts templateAtts = recordsService.getAtts(templateRef, TemplateAtts.class);

        String templateBase64Content = templateAtts.data;
        String incorrectTemplateMsg = "Incorrect template: " + templateRef + ". ";
        if (StringUtils.isBlank(templateBase64Content)) {
            throw new RuntimeException(incorrectTemplateMsg + "Content can't be received.");
        }

        byte[] bytes = Base64.getDecoder().decode(templateBase64Content);
        Definitions definitions = cmmnXmlContentDao.read(bytes);

        if (definitions == null) {
            throw new RuntimeException(incorrectTemplateMsg + "definitions is null");
        }

        return new TemplateInfo(definitions, templateAtts.ecosType);
    }

    @Autowired
    @Qualifier("caseTemplateContentDAO")
    public void setCmmnXmlContentDao(XmlContentDAO<Definitions> cmmnXmlContentDao) {
        this.cmmnXmlContentDao = cmmnXmlContentDao;
    }

    @Data
    @AllArgsConstructor
    public static class TemplateInfo {
        private Definitions definition;
        private RecordRef ecosTypeRef;
    }

    @Data
    public static class TemplateAtts {
        @AttName("data?str")
        private String data;
        private RecordRef ecosType;
    }
}
