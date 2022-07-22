package ru.citeck.ecos.node;

import ecos.com.google.common.cache.CacheBuilder;
import ecos.com.google.common.cache.CacheLoader;
import ecos.com.google.common.cache.LoadingCache;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import ru.citeck.ecos.model.ClassificationModel;
import ru.citeck.ecos.model.EcosTypeModel;
import ru.citeck.ecos.records.type.TypesManager;
import ru.citeck.ecos.records2.RecordRef;

import javax.annotation.PostConstruct;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Configuration
public class EcosTypeConfiguration {

    @Autowired
    private EcosTypeService ecosTypeService;

    @Autowired
    private NodeService nodeService;

    @Autowired
    private TypesManager typesManager;

    private LoadingCache<Pair<NodeRef, NodeRef>, Optional<String>> typeByTK;

    @PostConstruct
    public void init() {
        ecosTypeService.register(ContentModel.TYPE_BASE, this::evalDefaultEcosType);
        ecosTypeService.register(ContentModel.TYPE_CMOBJECT, this::evalDefaultEcosType);

        typeByTK = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.SECONDS)
            .maximumSize(200)
            .build(CacheLoader.from(this::getTypeImpl));
    }

    private RecordRef evalDefaultEcosType(AlfNodeInfo info) {

        Map<QName, Serializable> props = info.getProperties();

        String ecosTypeId = (String) props.get(EcosTypeModel.PROP_TYPE);

        if (StringUtils.isBlank(ecosTypeId)) {

            NodeRef type = (NodeRef) props.get(ClassificationModel.PROP_DOCUMENT_TYPE);
            NodeRef kind = (NodeRef) props.get(ClassificationModel.PROP_DOCUMENT_KIND);

            ecosTypeId = typeByTK.getUnchecked(new Pair<>(type, kind)).orElse(null);
        }

        if (StringUtils.isBlank(ecosTypeId)) {
            ecosTypeId = typesManager.getEcosTypeByAlfType(info.getType()).getId();
        }

        return RecordRef.create("emodel", "type", ecosTypeId);
    }

    private Optional<String> getTypeImpl(@Nullable Pair<NodeRef, NodeRef> typeKind) {

        if (typeKind == null) {
            return Optional.empty();
        }

        NodeRef type = typeKind.getFirst();
        NodeRef kind = typeKind.getSecond();

        if (type == null) {
            return Optional.empty();
        }

        String ecosTypeId;

        if (kind != null) {
            ecosTypeId = (String) nodeService.getProperty(kind, EcosTypeModel.PROP_TYPE);
        } else {
            ecosTypeId = (String) nodeService.getProperty(type, EcosTypeModel.PROP_TYPE);
        }

        if (StringUtils.isBlank(ecosTypeId)) {
            ecosTypeId = type.getId();
            if (kind != null) {
                ecosTypeId = ecosTypeId + "/" + kind.getId();
            }
        }

        if (StringUtils.isBlank(ecosTypeId)) {
            return Optional.empty();
        }

        return Optional.of(ecosTypeId);
    }
}
