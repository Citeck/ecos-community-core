package ru.citeck.ecos.node;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.repo.jscript.ScriptNode;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.lang3.StringUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef;
import ru.citeck.ecos.node.etype.EcosTypeRootService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.utils.AlfrescoScopableProcessorExtension;
import ru.citeck.ecos.utils.JavaScriptImplUtils;
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class EcosTypeServiceJS extends AlfrescoScopableProcessorExtension {

    private EcosTypeService ecosTypeService;
    private EcosTypeRootService ecosTypeRootService;

    public ScriptNode getRootForType(String type, boolean createIfNotExists) {
        NodeRef rootForType = ecosTypeRootService.getRootForType(getTypeRef(type), createIfNotExists);
        if (rootForType == null) {
            return null;
        }
        return JavaScriptImplUtils.wrapNode(rootForType, this);
    }

    public ScriptNode getRootForType(String type) {
        return getRootForType(type, false);
    }

    public Scriptable getAttsIdListByType(String type) {
        return Context.getCurrentContext().newArray(
            this.getScope(),
            getAttsIdListByTypeImpl(type).toArray()
        );
    }

    private List<String> getAttsIdListByTypeImpl(String type) {

        TypeDef typeDef = ecosTypeService.getTypeDef(getTypeRef(type));
        if (typeDef == null) {
            return Collections.emptyList();
        }

        List<AttributeDef> attributes = typeDef.getModel().getAttributes();

        return attributes.stream()
            .map(AttributeDef::getId)
            .collect(Collectors.toList());
    }

    private RecordRef getTypeRef(String type) {
        if (StringUtils.isBlank(type)) {
            return RecordRef.EMPTY;
        }
        if (!type.startsWith("emodel/type@")) {
            type = "emodel/type@" + type;
        }
        return RecordRef.valueOf(type);
    }

    public void setEcosTypeService(EcosTypeService ecosTypeService) {
        this.ecosTypeService = ecosTypeService;
    }

    public void setEcosTypeRootService(EcosTypeRootService ecosTypeRootService) {
        this.ecosTypeRootService = ecosTypeRootService;
    }
}
