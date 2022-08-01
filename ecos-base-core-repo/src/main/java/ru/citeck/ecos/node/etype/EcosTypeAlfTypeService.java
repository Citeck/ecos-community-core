package ru.citeck.ecos.node.etype;

import org.alfresco.model.ContentModel;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records.source.alf.meta.AlfNodeRecord;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef;

@Service
public class EcosTypeAlfTypeService {

    public static final String PROP_ALF_TYPE = "alfType";

    private final QName DEFAULT_TYPE = ContentModel.TYPE_CMOBJECT;

    private final EcosTypeService ecosTypeService;
    private final RecordsService recordsService;

    private final NamespaceService namespaceService;

    @Autowired
    public EcosTypeAlfTypeService(EcosTypeService ecosTypeService,
                                  NamespaceService namespaceService,
                                  RecordsService recordsService
    ) {
        this.recordsService = recordsService;
        this.ecosTypeService = ecosTypeService;
        this.namespaceService = namespaceService;
    }

    @Nullable
    public QName getAlfTypeToCreate(RecordRef typeRef, ObjectData attributes) {

        String alfType;

        if (RecordRef.isNotEmpty(typeRef)) {
            alfType = getExactAlfTypeFromProps(typeRef);
            if (StringUtils.isNotBlank(alfType)) {
                return QName.resolveToQName(namespaceService, alfType);
            }
        }

        String type = attributes.get(AlfNodeRecord.ATTR_TYPE, "");
        if (type.isEmpty()) {
            type = attributes.get(AlfNodeRecord.ATTR_TYPE_UPPER, "");
        }
        if (!type.isEmpty()) {
            return QName.resolveToQName(namespaceService, type);
        }

        alfType = recordsService.getAtt(typeRef, "inhAttributes.alfType?str").asText();
        if (StringUtils.isNotBlank(alfType)) {
            return QName.resolveToQName(namespaceService, alfType);
        }
        return DEFAULT_TYPE;
    }

    @Nullable
    public String getAlfTypeToSearch(RecordRef typeRef) {
        return getExactAlfTypeFromProps(typeRef);
    }

    private String getExactAlfTypeFromProps(@Nullable RecordRef typeRef) {

        TypeDef typeDef = ecosTypeService.getTypeDef(typeRef);
        if (typeDef == null) {
            return null;
        }
        ObjectData properties = typeDef.getProperties();
        String alfType = properties.get(PROP_ALF_TYPE).asText();
        return StringUtils.isNotBlank(alfType) ? alfType : null;
    }
}
