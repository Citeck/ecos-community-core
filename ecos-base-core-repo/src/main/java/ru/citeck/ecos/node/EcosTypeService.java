package ru.citeck.ecos.node;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.namespace.QName;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.model.ClassificationModel;
import ru.citeck.ecos.model.lib.num.dto.NumTemplateDef;
import ru.citeck.ecos.model.lib.type.dto.DocLibDef;
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils;
import ru.citeck.ecos.records.type.TypesManager;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
import ru.citeck.ecos.records2.predicate.PredicateService;
import ru.citeck.ecos.records2.predicate.model.Predicates;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.utils.DictUtils;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef;

import javax.swing.text.html.parser.Entity;
import java.util.*;
import java.util.function.Function;

@Service("ecosTypeService")
@Slf4j
public class EcosTypeService {

    public static final QName QNAME = QName.createQName("", "ecosTypeService");
    private static final RecordRef DEFAULT_TYPE = RecordRef.create("emodel", "type", "base");

    private final RecordsService recordsService;
    private final DictUtils dictUtils;

    private final EvaluatorsByAlfNode<RecordRef> evaluators;

    private TypesManager typesManager;

    @Autowired
    public EcosTypeService(ServiceRegistry serviceRegistry,
                           RecordsService recordsService,
                           DictUtils dictUtils) {

        evaluators = new EvaluatorsByAlfNode<>(serviceRegistry, node -> DEFAULT_TYPE);
        this.recordsService = recordsService;
        this.dictUtils = dictUtils;
    }

    public void register(QName nodeType, Function<AlfNodeInfo, RecordRef> evaluator) {
        evaluators.register(nodeType, evaluator);
    }

    @NotNull
    public List<RecordRef> expandTypeWithChildren(@Nullable EntityRef typeRef) {
        if (EntityRef.isEmpty(typeRef) || typesManager == null) {
            return Collections.singletonList(RecordRef.valueOf(typeRef));
        }
        List<RecordRef> result = new ArrayList<>();
        forEachDesc(typeRef, typeDto -> {
            result.add(TypeUtils.getTypeRef(typeDto.getId()));
            return false;
        });
        return result;
    }

    @NotNull
    public DocLibDef getDocLib(RecordRef typeRef) {
        TypeDef typeDef = getTypeDef(typeRef);
        if (typeDef == null) {
            return DocLibDef.EMPTY;
        }
        return typeDef.getDocLib();
    }

    @NotNull
    public ObjectData getResolvedProperties(RecordRef typeRef) {
        return recordsService.getAttribute(typeRef, "inhProperties?json").asObjectData();
    }

    @Nullable
    public TypeDef getTypeDef(@Nullable EntityRef typeRef) {
        if (typesManager == null || EntityRef.isEmpty(typeRef)) {
            return null;
        }
        return typesManager.getType(typeRef);
    }

    @NotNull
    public RecordRef getEcosType(NodeRef nodeRef) {
        RecordRef result = evaluators.eval(nodeRef);
        return result != null ? result : RecordRef.EMPTY;
    }

    @NotNull
    public RecordRef getEcosType(AlfNodeInfo nodeInfo) {
        RecordRef result = evaluators.eval(nodeInfo);
        return result != null ? result : RecordRef.EMPTY;
    }

    @Nullable
    public RecordRef getEcosType(String alfrescoType) {
        PropertyDefinition propDef = dictUtils.getPropDef(alfrescoType, ClassificationModel.PROP_DOCUMENT_TYPE);
        if (propDef == null) {
            return null;
        }

        String value = propDef.getDefaultValue();
        if (!StringUtils.isNotBlank(value) || !NodeRef.isNodeRef(value)) {
            return null;
        }

        NodeRef typeNodeRef = new NodeRef(value);
        return RecordRef.create("emodel", "type", typeNodeRef.getId());
    }

    public List<RecordRef> getDescendantTypes(RecordRef typeRef) {
        List<RecordRef> result = new ArrayList<>();
        forEachDesc(typeRef, type -> {
            result.add(RecordRef.create("emodel", "type", type.getId()));
            return false;
        });
        return result;
    }

    public <T> T getEcosTypeConfig(RecordRef configRef, Class<T> configClass) {
        EcosTypeConfig typeConfig = recordsService.getMeta(configRef, EcosTypeConfig.class);
        return typeConfig.getData().getAs(configClass);
    }

    public <T> T getEcosTypeConfig(NodeRef documentRef, Class<T> configClass) {
        RecordRef ecosType = getEcosType(documentRef);
        return getEcosTypeConfig(ecosType, configClass);
    }

    @Nullable
    public Long getNumberForDocument(@NotNull RecordRef docRef) {
        return getNumberForDocument(docRef, getNumTemplateByRecord(docRef));
    }

    @Nullable
    public Long getNumberForDocument(@NotNull RecordRef docRef, @Nullable RecordRef numTemplateRef) {

        if (EntityRef.isEmpty(numTemplateRef)) {
            return null;
        }

        if (typesManager == null) {
            throw new IllegalStateException("typesManager is null");
        }

        NumTemplateDef numTemplate = typesManager.getNumTemplate(numTemplateRef);
        if (numTemplate == null) {
            throw new IllegalStateException("Number template is not found for ref: '" + numTemplateRef + "'");
        }

        ObjectData model = recordsService.getAttributes(docRef, numTemplate.getModelAttributes()).getAttributes();

        return typesManager.getNextNumber(numTemplateRef, model);
    }

    @Nullable
    public RecordRef getNumTemplateByTypeRef(@Nullable RecordRef typeRef) {
        if (typeRef == null || EntityRef.isEmpty(typeRef)) {
            return null;
        }
        TypeDef typeDef = getTypeDef(typeRef);
        return typeDef != null ? RecordRef.valueOf(typeDef.getNumTemplateRef()) : null;
    }

    @Nullable
    private RecordRef getNumTemplateByRecord(RecordRef recordRef) {
        if (EntityRef.isEmpty(recordRef)) {
            return null;
        }
        RecordRef typeRef = recordsService.getAttribute(recordRef, "_type?id").getAs(RecordRef.class);
        return getNumTemplateByTypeRef(typeRef);
    }

    public void forEachDesc(EntityRef typeRef, Function<TypeDef, Boolean> action) {

        if (EntityRef.isEmpty(typeRef) || typesManager == null) {
            return;
        }
        forEachDesc(Collections.singletonList(typeRef), action);
    }

    public void forEachAscRef(RecordRef typeRef, Function<RecordRef, Boolean> action) {
        forEachAsc(typeRef, dto -> {
            RecordRef ref = RecordRef.create("emodel", "type", dto.getId());
            return action.apply(ref);
        });
    }

    public List<RecordRef> getChildren(EntityRef typeRef) {

        RecordsQuery query = new RecordsQuery();
        query.setSourceId("emodel/type");
        query.setQuery(Predicates.eq("parentRef", typeRef.toString()));
        query.setLanguage(PredicateService.LANGUAGE_PREDICATE);

        return recordsService.queryRecords(query).getRecords();
    }

    private void forEachDesc(List<? extends EntityRef> types, Function<TypeDef, Boolean> action) {

        for (EntityRef type : types) {

            if (EntityRef.isEmpty(type)) {
                continue;
            }

            TypeDef typeDto = typesManager.getType(type);
            if (typeDto != null && action.apply(typeDto)) {
                return;
            }

            forEachDesc(getChildren(type), action);
        }
    }

    public void forEachAsc(RecordRef typeRef, Function<TypeDef, Boolean> action) {

        if (EntityRef.isEmpty(typeRef) || typesManager == null) {
            return;
        }

        TypeDef typeDto = typesManager.getType(typeRef);

        if (typeDto == null) {
            return;
        }

        while (typeDto != null && !action.apply(typeDto)) {
            typeDto = EntityRef.isNotEmpty(typeDto.getParentRef()) ?
                typesManager.getType(typeDto.getParentRef()) : null;
        }
    }

    @Autowired(required = false)
    public void setTypesManager(TypesManager typesManager) {
        this.typesManager = typesManager;
    }

    @Data
    private static final class EcosTypeConfig {
        @MetaAtt("config")
        ObjectData data;
    }
}
