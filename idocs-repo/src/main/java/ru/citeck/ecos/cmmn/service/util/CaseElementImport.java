package ru.citeck.ecos.cmmn.service.util;

import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.lang.StringUtils;
import ru.citeck.ecos.cmmn.CMMNUtils;
import ru.citeck.ecos.cmmn.model.Case;
import ru.citeck.ecos.icase.CaseConstants;
import ru.citeck.ecos.icase.element.CaseElementService;
import ru.citeck.ecos.icase.element.config.ElementConfigDto;

import java.util.Optional;

public class CaseElementImport {

    private final CaseElementService caseElementService;

    public CaseElementImport(CaseElementService caseElementService) {
        this.caseElementService = caseElementService;
    }

    public void importCaseElementTypes(NodeRef caseRef, Case caseItem) {
        String elementsStr = caseItem.getOtherAttributes().get(CMMNUtils.QNAME_ELEMENT_TYPES);

        if (StringUtils.isNotBlank(elementsStr)) {

            String[] elements = elementsStr.split(",");

            for (String element : elements) {
                Optional<ElementConfigDto> config = caseElementService.getConfig(element);
                config.ifPresent(configDto -> {
                    NodeRef elementRef = configDto.getNodeRef();
                    caseElementService.addElement(elementRef, caseRef, CaseConstants.ELEMENT_TYPES);
                });
            }
        }
    }
}
