package ru.citeck.ecos.content;

import lombok.Data;
import org.alfresco.service.cmr.dictionary.ClassDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import ru.citeck.ecos.model.ClassificationModel;
import ru.citeck.ecos.model.EcosTypeModel;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;

import java.io.Serializable;
import java.util.*;

/**
 * TypeKind config registry helps to search configs by EcoS type and kind
 *
 * @param <T> type of parsed content data
 * @author Pavel Simonov
 */
public class TypeKindContentDAO<T> extends RepoContentDAOImpl<T> {

    private QName typeField = ClassificationModel.PROP_DOCUMENT_APPLIES_TO_TYPE;
    private QName kindField = ClassificationModel.PROP_DOCUMENT_APPLIES_TO_KIND;
    private QName classField = null;

    @Autowired
    @Qualifier("ecosTypeService")
    private EcosTypeService ecosTypeService;

    @Autowired
    private RecordsService recordsService;

    /**
     * Get configs by EcoS type kind
     * Keys to search in order of priority:
     * 1) EcoS type and EcoS kind
     * 2) EcoS type and EcoS kind is empty
     */
    public List<ContentData<T>> getContentDataByTypeKind(NodeRef type, NodeRef kind) {

        List<ContentData<T>> configs = Collections.emptyList();

        if (type != null) {

            Map<QName, Serializable> keys = new HashMap<>(2);
            keys.put(typeField, type);
            keys.put(kindField, kind);

            configs = getContentData(keys);
            if (configs.isEmpty() && kind != null) {
                return getContentDataByTypeKind(type, null);
            }
        }

        return configs;
    }

    /**
     * Get configs by EcoS type kind or class of node
     * Keys to search in order of priority:
     * 1) EcoS type and EcoS kind
     * 2) EcoS type and EcoS kind is empty
     * 3) Alfresco type
     * 4) Alfresco aspect
     */
    public List<ContentData<T>> getContentDataByNodeTKC(NodeRef nodeRef) {

        List<ContentData<T>> configs = Collections.emptyList();

        //  ecos type
        RecordRef ecosTypeRef = ecosTypeService.getEcosType(nodeRef);
        while (RecordRef.isEmpty(ecosTypeRef)) {
            EcosTypeDto dto = recordsService.getMeta(ecosTypeRef, EcosTypeDto.class);
            ecosTypeRef = dto.getParentRef();
        }
        if (RecordRef.isNotEmpty(ecosTypeRef)) {
            Map<QName, Serializable> keys = Collections.singletonMap(EcosTypeModel.PROP_TYPE, ecosTypeRef);
            configs = getContentData(keys);
        }

        NodeRef type = (NodeRef) nodeService.getProperty(nodeRef, ClassificationModel.PROP_DOCUMENT_TYPE);
        //case type/kind
        if (configs.isEmpty() && type != null) {
            NodeRef kind = (NodeRef) nodeService.getProperty(nodeRef, ClassificationModel.PROP_DOCUMENT_KIND);
            configs = getContentDataByTypeKind(type, kind);
        }
        //  alfresco type
        if (configs.isEmpty()) {
            QName nodeType = nodeService.getType(nodeRef);
            configs = getContentDataByClassName(nodeType, true);
        }
        //  aspects
        if (configs.isEmpty()) {
            Set<QName> aspects = nodeService.getAspects(nodeRef);
            for (QName aspect : aspects) {
                configs = getContentDataByClassName(aspect, false);
                if (!configs.isEmpty()) break;
            }
        }
        return configs;
    }

    /**
     * Get config by alfresco class
     *
     * @param className alfresco type or aspect name
     */
    public List<ContentData<T>> getContentDataByClassName(QName className, boolean includeParents) {

        if (classField == null) {
            return Collections.emptyList();
        }

        Map<QName, Serializable> keys = new HashMap<>(1);
        keys.put(classField, className);
        List<ContentData<T>> configs = getContentData(keys);

        if (configs.isEmpty() && includeParents) {
            ClassDefinition classDef = dictionaryService.getClass(className);
            while (classDef != null && configs.isEmpty()) {
                classDef = classDef.getParentClassDefinition();
                if (classDef != null) {
                    keys.put(classField, classDef.getName());
                    configs = getContentData(keys);
                }
            }
        }

        return configs;
    }

    public void setTypeField(QName typeField) {
        this.typeField = typeField;
    }

    public void setKindField(QName kindField) {
        this.kindField = kindField;
    }

    public void setClassField(QName classField) {
        this.classField = classField;
    }

    @Data
    public class EcosTypeDto {
        private RecordRef parentRef;
    }
}
