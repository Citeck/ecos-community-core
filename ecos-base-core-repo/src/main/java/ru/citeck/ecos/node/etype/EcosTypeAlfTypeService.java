package ru.citeck.ecos.node.etype;

import org.alfresco.model.ContentModel;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.lib.type.dto.TypeDef;
import ru.citeck.ecos.model.lib.type.service.TypeDefService;
import ru.citeck.ecos.records2.RecordRef;

@Service
public class EcosTypeAlfTypeService {

    private final QName DEFAULT_TYPE = ContentModel.TYPE_CMOBJECT;

    private final TypeDefService typeDefService;

    private final NamespaceService namespaceService;

    @Autowired
    public EcosTypeAlfTypeService(TypeDefService typeDefService, NamespaceService namespaceService) {
        this.typeDefService = typeDefService;
        this.namespaceService = namespaceService;
    }

    @Nullable
    public QName getAlfTypeToCreate(RecordRef typeRef) {

        if (RecordRef.isEmpty(typeRef)) {
            return DEFAULT_TYPE;
        }

        ObjectData typeProps = typeDefService.getResolvedProperties(typeRef);
        String alfType = typeProps.get("alfType").asText();
        if (StringUtils.isNotBlank(alfType)) {
            return QName.resolveToQName(namespaceService, alfType);
        }
        return DEFAULT_TYPE;
    }

    @Nullable
    public String getAlfTypeToSearch(RecordRef typeRef) {

        TypeDef typeDef = typeDefService.getTypeDef(typeRef);
        if (typeDef == null) {
            return null;
        }

        String alfType = typeDef.getProperties().get("alfType").asText();

        return StringUtils.isNotBlank(alfType) ? alfType : null;
    }
}
