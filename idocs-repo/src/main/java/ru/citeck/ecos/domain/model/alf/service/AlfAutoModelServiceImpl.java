package ru.citeck.ecos.domain.model.alf.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.alfresco.repo.dictionary.*;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.namespace.NamespaceException;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.commons.utils.StringUtils;
import ru.citeck.ecos.domain.model.alf.dao.AlfAutoModelsDao;
import ru.citeck.ecos.domain.model.alf.dao.TypeModelInfo;
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef;
import ru.citeck.ecos.model.lib.attributes.dto.AttributeType;
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef;
import ru.citeck.ecos.model.lib.type.service.TypeRefService;
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.webapp.api.entity.EntityRef;
import ru.citeck.ecos.webapp.lib.model.type.dto.TypeDef;

import java.util.*;
import java.util.stream.Collectors;

@Service(value = "alfAutoModelService")
public class AlfAutoModelServiceImpl implements AlfAutoModelService {

    private static final String ASPECT_LOCAL_NAME = "type-aspect";

    private final DictionaryDAO dictionaryDao;
    private final AlfAutoModelsDao alfAutoModelsDao;
    private final NamespaceService namespaceService;
    private final EcosTypeService ecosTypeService;
    private final TypeRefService typeRefService;

    @Autowired
    public AlfAutoModelServiceImpl(@Qualifier("dictionaryDAO")
                                   DictionaryDAO dictionaryDao,
                                   AlfAutoModelsDao alfAutoModelsDao,
                                   NamespaceService namespaceService,
                                   EcosTypeService ecosTypeService,
                                   TypeRefService typeRefService) {

        this.alfAutoModelsDao = alfAutoModelsDao;
        this.namespaceService = namespaceService;
        this.ecosTypeService = ecosTypeService;
        this.dictionaryDao = dictionaryDao;
        this.typeRefService = typeRefService;
    }

    @Override
    public Map<String, String> getPropsMapping(RecordRef typeRef) {

        if (EntityRef.isEmpty(typeRef)) {
            return Collections.emptyMap();
        }

        List<AttributeDef> fullAttributes = new ArrayList<>();
        TypeDef typeDef = ecosTypeService.getTypeDef(typeRef);
        if (typeDef != null) {
            TypeModelDef model = typeDef.getModel();
            fullAttributes.addAll(model.getAttributes());
        }

        return getPropsMapping(typeRef, fullAttributes
            .stream()
            .map(AttributeDef::getId)
            .collect(Collectors.toList()), false);
    }

    @Override
    public Map<String, String> getPropsMapping(RecordRef typeRef, Collection<String> attributes, boolean isWriteMode) {

        if (EntityRef.isEmpty(typeRef)) {
            return Collections.emptyMap();
        }
        Set<String> attsSet = new HashSet<>(attributes);

        Map<RecordRef, List<AttributeDef>> attsByType = new HashMap<>();

        typeRefService.forEachAsc(typeRef, ref -> {

            TypeDef typeDef = ecosTypeService.getTypeDef(ref);
            if (typeDef == null) {
                return null;
            }

            List<AttributeDef> attributeDefs = typeDef.getModel()
                .getAttributes()
                .stream()
                .filter(att -> StringUtils.isNotBlank(att.getId()) && !att.getId().contains("."))
                .filter(att -> attsSet.contains(att.getId()))
                .filter(att -> !isRegisteredAtt(att.getId()))
                .collect(Collectors.toList());

            if (!attributeDefs.isEmpty()) {
                attsByType.put(TypeUtils.getTypeRef(typeDef.getId()), attributeDefs);
            }
            return null;
        });

        if (attsByType.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<RecordRef, List<AttributeDef>> attributesToGenerate = new HashMap<>();
        Map<String, String> resultMapping = new HashMap<>();

        attsByType.forEach((attsTypeRef, attributeDefs) -> {

            QName modelQName = alfAutoModelsDao.getModelQNameByType(attsTypeRef);

            if (modelQName != null || isWriteMode) {

                for (AttributeDef att : attributeDefs) {

                    PropertyDefinition property = null;
                    if (modelQName != null) {
                        QName attQName = QName.createQName(modelQName.getNamespaceURI(), att.getId());
                        property = dictionaryDao.getProperty(attQName);
                    }
                    if (property != null) {
                        if (isWriteMode) {
                            String propType = property.getDataType().getName().toPrefixString(namespaceService);
                            if (!getModelPropType(att).equals(propType)
                                    || property.isMultiValued() != att.getMultiple()) {
                                attributesToGenerate.computeIfAbsent(attsTypeRef, t -> new ArrayList<>()).add(att);
                            } else {
                                resultMapping.put(att.getId(), modelQName.getLocalName() + ":" + att.getId());
                            }
                        } else {
                            resultMapping.put(att.getId(), modelQName.getLocalName() + ":" + att.getId());
                        }
                    } else {
                        attributesToGenerate.computeIfAbsent(attsTypeRef, t -> new ArrayList<>()).add(att);
                    }
                }
            }
        });

        if (!isWriteMode || attributesToGenerate.isEmpty()) {
            return resultMapping;
        }

        attributesToGenerate.forEach((attsTypeRef, attributeDefs) -> {

            TypeModelInfo modelInfo = alfAutoModelsDao.getOrCreateModelByTypeRef(attsTypeRef);
            ModelDef modelDef = new ModelDef(modelInfo, false);

            M2Aspect aspect = getOrCreateAspect(modelDef, modelInfo.getModelPrefix() + ":" + ASPECT_LOCAL_NAME);
            for (AttributeDef att : attributeDefs) {
                updateOrCreateProp(aspect, att, modelDef);
            }

            if (modelDef.wasChanged) {
                dictionaryDao.putModel(modelDef.info.getModel());
                alfAutoModelsDao.save(modelInfo.withModel(modelDef.info.getModel()));
            }

            for (AttributeDef att : attributeDefs) {
                resultMapping.put(att.getId(), modelDef.info.getModelPrefix() + ":" + att.getId());
            }
        });

        return resultMapping;
    }

    private boolean isRegisteredAtt(String attribute) {

        if (attribute.charAt(0) == '{') {
            return true;
        }

        int twoDotsIdx = attribute.indexOf(':');
        if (twoDotsIdx <= 0 || twoDotsIdx == attribute.length() - 1) {
            return false;
        }

        String prefix = attribute.substring(0, twoDotsIdx);

        try {

            String namespaceUri = namespaceService.getNamespaceURI(prefix);
            QName qname = QName.createQName(namespaceUri, attribute.substring(twoDotsIdx + 1));

            return dictionaryDao.getProperty(qname) != null || dictionaryDao.getAssociation(qname) != null;

        } catch (NamespaceException e) {
            return false;
        }
    }

    private void updateOrCreateProp(M2Aspect aspect, AttributeDef propDef, ModelDef modelDef) {

        String name = modelDef.info.getModelPrefix() + ":" + propDef.getId();

        M2Property property = aspect.getProperty(name);

        if (property == null) {

            property = aspect.createProperty(name);
            property.setIndexed(true);
            property.setType(getModelPropType(propDef));

            if (AttributeType.TEXT.equals(propDef.getType())) {
                property.setIndexTokenisationMode(IndexTokenisationMode.BOTH);
            } else {
                property.setIndexTokenisationMode(IndexTokenisationMode.FALSE);
            }
            property.setStoredInIndex(false);
            property.setMultiValued(propDef.getMultiple());

            modelDef.wasChanged = true;

        } else {

            String propType = getModelPropType(propDef);
            if (!propType.equals(property.getType())) {
                property.setType(propType);
                modelDef.wasChanged = true;
            }

            if (property.isMultiValued() != propDef.getMultiple()) {
                property.setMultiValued(propDef.getMultiple());
                modelDef.wasChanged = true;
            }
        }
    }

    private String getModelPropType(AttributeDef attributeDef) {

        switch (attributeDef.getType()) {
            case TEXT:
            case ASSOC:
                return "d:text";
            case MLTEXT:
                return "d:mltext";
            case BOOLEAN:
                return "d:boolean";
            case NUMBER:
                return "d:double";
            case DATE:
                return "d:date";
            case DATETIME:
                return "d:datetime";
            case CONTENT:
                return "d:content";
        }
        return "d:text";
    }

    private M2Aspect getOrCreateAspect(ModelDef modelDef, String name) {
        M2Aspect aspect = modelDef.info.getModel().getAspect(name);
        if (aspect == null) {
            aspect = modelDef.info.getModel().createAspect(name);
            modelDef.wasChanged = true;
        }
        return aspect;
    }

    @Data
    @AllArgsConstructor
    private static class ModelDef {
        private TypeModelInfo info;
        private boolean wasChanged;
    }
}
