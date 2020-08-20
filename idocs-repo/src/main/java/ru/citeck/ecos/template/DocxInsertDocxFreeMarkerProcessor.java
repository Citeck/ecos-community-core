package ru.citeck.ecos.template;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.repository.NodeRef;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.P;

import java.util.ArrayList;
import java.util.List;

/**
 * Extended DOCX template processor with support of FreeMarker tags.
 * To insert whole docx document into template.
 * <p>
 * Supported placeholder - <b>[[ecosInsertDocxContentNode("nodeRef")]]</b>, at the beginning of a paragraph.
 * <pre>
 * 1. process: insert placeholders
 * 2. postProcess: insert content instead of placeholders
 * </pre>
 */
@Slf4j
public class DocxInsertDocxFreeMarkerProcessor extends DocxFreeMarkerProcessor {
    private static final String placeholder = "[[ecosInsertDocxContentNode(\"";

    protected void postProcess(WordprocessingMLPackage wpMLPackage) {
        List<Object> objectList = new ArrayList<>(wpMLPackage.getMainDocumentPart().getContent());
        for (Object o : objectList) {
            if (o instanceof P) {
                String pStr = o.toString();
                if (pStr.startsWith(placeholder)) {
                    String nodeRefStr = pStr.replace(placeholder, "").replace("\")]]", "");
                    int i = wpMLPackage.getMainDocumentPart().getContent().indexOf(o);
                    wpMLPackage.getMainDocumentPart().getContent().remove(i);
                    NodeRef nodeRef = new NodeRef(nodeRefStr);
                    try {
                        WordprocessingMLPackage extPackage = getWordTemplate(nodeRef);
                        List<Object> extObjects = extPackage.getMainDocumentPart().getContent();
                        for (Object ext : extObjects) {
                            wpMLPackage.getMainDocumentPart().getContent().add(i, ext);
                            i++;
                        }
                    } catch (Exception e) {
                        log.warn(e.getLocalizedMessage(), e);
                    }
                }
            }
        }
    }
}
