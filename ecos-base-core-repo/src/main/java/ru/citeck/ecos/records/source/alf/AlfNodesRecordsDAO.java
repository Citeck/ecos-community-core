package ru.citeck.ecos.records.source.alf;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.*;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.GUID;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.action.group.ActionResults;
import ru.citeck.ecos.action.group.GroupActionConfig;
import ru.citeck.ecos.action.group.GroupActionService;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.context.lib.auth.AuthContext;
import ru.citeck.ecos.domain.model.alf.service.AlfAutoModelService;
import ru.citeck.ecos.graphql.AlfGqlContext;
import ru.citeck.ecos.icase.CaseStatusService;
import ru.citeck.ecos.model.EcosModel;
import ru.citeck.ecos.model.EcosTypeModel;
import ru.citeck.ecos.model.InvariantsModel;
import ru.citeck.ecos.model.lib.attributes.computed.ComputedAttsService;
import ru.citeck.ecos.model.lib.status.constants.StatusConstants;
import ru.citeck.ecos.model.lib.type.service.utils.TypeUtils;
import ru.citeck.ecos.node.AlfNodeInfo;
import ru.citeck.ecos.node.AlfNodeInfoImpl;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.node.etype.EcosTypeAlfTypeService;
import ru.citeck.ecos.node.etype.EcosTypeChildAssocService;
import ru.citeck.ecos.node.etype.EcosTypeRootService;
import ru.citeck.ecos.records.source.alf.file.AlfNodeContentFileHelper;
import ru.citeck.ecos.records.source.alf.meta.AlfNodeRecord;
import ru.citeck.ecos.records.source.alf.search.AlfNodesSearch;
import ru.citeck.ecos.records.source.dao.RecordsActionExecutor;
import ru.citeck.ecos.records.type.TypeDto;
import ru.citeck.ecos.records.type.TypesManager;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.EmptyValue;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.records2.graphql.meta.value.field.EmptyMetaField;
import ru.citeck.ecos.records2.meta.RecordsTemplateService;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.mutation.RecordsMutation;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.request.query.SortBy;
import ru.citeck.ecos.records2.source.dao.MutableRecordsDao;
import ru.citeck.ecos.records2.source.dao.RecordsQueryDao;
import ru.citeck.ecos.records2.source.dao.local.LocalRecordsDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsMetaDao;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsQueryWithMetaDao;
import ru.citeck.ecos.records3.RecordsProperties;
import ru.citeck.ecos.security.EcosPermissionService;
import ru.citeck.ecos.utils.AuthorityUtils;
import ru.citeck.ecos.utils.NodeUtils;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static ru.citeck.ecos.model.ClassificationModel.PROP_DOCUMENT_KIND;
import static ru.citeck.ecos.model.ClassificationModel.PROP_DOCUMENT_TYPE;

@Component
@Slf4j
public class AlfNodesRecordsDAO extends LocalRecordsDao
    implements RecordsQueryDao,
    LocalRecordsMetaDao<MetaValue>,
    LocalRecordsQueryWithMetaDao<Object>,
    MutableRecordsDao, RecordsActionExecutor {

    public static final String ID = "";
    private static final String ADD_CMD_PREFIX = "att_add_";
    private static final String REMOVE_CMD_PREFIX = "att_rem_";
    private static final String TYPE_ATTRIBUTE_NAME = "_type";
    private static final String ETYPE_ATTRIBUTE_NAME = "_etype";
    private static final String SLASH_DELIMITER = "/";
    private static final String WORKSPACE_PREFIX = "workspace://SpacesStore/";
    private static final String STATE_ATTRIBUTE_NAME = "_state";
    private static final String CONTENT_ATTRIBUTE_NAME = "_content";
    private static final String CM_CONTENT_ATTRIBUTE_NAME = "cm:content";

    private static final String[] CONTENT_PROPS_WITH_NAME = { "originalName", "name", "filename" };

    private final Map<String, AlfNodesSearch> searchByLanguage = new ConcurrentHashMap<>();

    private AuthorityUtils authorityUtils;
    private RecordsProperties recordsProperties;
    private NodeUtils nodeUtils;
    private NodeService nodeService;
    private SearchService searchService;
    private EcosTypeService ecosTypeService;
    private EcosTypeRootService ecosTypeRootService;
    private NamespaceService namespaceService;
    private DictionaryService dictionaryService;
    private GroupActionService groupActionService;
    private AlfNodeContentFileHelper contentFileHelper;
    private EcosPermissionService ecosPermissionService;
    private TypesManager typeInfoProvider;
    private ComputedAttsService computedAttsService;
    private ServiceRegistry serviceRegistry;
    private RecordsTemplateService recordsTemplateService;
    private AlfAutoModelService alfAutoModelService;
    private EcosTypeAlfTypeService ecosTypeAlfTypeService;
    private EcosTypeChildAssocService ecosTypeChildAssocService;
    @Nullable
    private CaseStatusService caseStatusService;

    private final Map<QName, NodeRef> defaultParentByType = new ConcurrentHashMap<>();

    public AlfNodesRecordsDAO() {
        setId(ID);
    }

    @Override
    public RecordsMutResult mutateImpl(RecordsMutation mutation) {

        RecordsMutResult result = new RecordsMutResult();

        for (RecordMeta record : mutation.getRecords()) {
            RecordMeta resRec = processSingleRecord(record);
            result.addRecord(resRec);
        }

        return result;
    }

    private DataValue fixNodeRef(DataValue dataValue) {
        if (dataValue.isArray()) {
            DataValue resultArr = DataValue.createArr();
            dataValue.forEach(value -> resultArr.add(fixNodeRef(value)));
            return resultArr;
        } else if (dataValue.isTextual()) {
            String textValue = dataValue.asText();
            if (authorityUtils.isAuthorityRef(textValue)) {
                NodeRef nodeRef = authorityUtils.getNodeRef(textValue);
                return DataValue.createStr(String.valueOf(nodeRef));
            } else if (textValue.startsWith(AlfNodeRecord.NODE_REF_SOURCE_ID_PREFIX)) {
                return DataValue.createStr(textValue.replaceFirst(AlfNodeRecord.NODE_REF_SOURCE_ID_PREFIX, ""));
            }
        }
        return dataValue;
    }

    private RecordMeta processSingleRecord(RecordMeta record) {
        return processSingleRecord(record, true);
    }

    private RecordMeta processSingleRecord(RecordMeta record, boolean firstCall) {

        ObjectData initialAtts = record.getAtts().deepCopy();
        for (String field : initialAtts.fieldNamesList()) {
            if (RecordConstants.ATT_DISP.equals(field) || "_name".equals(field)) {
                initialAtts.set("cm:title", initialAtts.get(field));
                initialAtts.remove(field);
            }
        }
        initialAtts.forEachJ((k, v) -> {
            DataValue fixed = fixNodeRef(v);
            if (fixed != v) {
                initialAtts.set(k, fixed);
            }
        });

        ObjectData attributes = initialAtts.deepCopy();

        RecordMeta resultRecord;
        Map<QName, Serializable> props = new HashMap<>();
        Map<QName, DataValue> contentProps = new HashMap<>();
        Map<QName, Set<NodeRef>> assocs = new HashMap<>();
        Map<QName, DataValue> childAssocEformFiles = new HashMap<>();
        Map<QName, DataValue> attachmentAssocEformFiles = new HashMap<>();
        Map<String, String> attsToIgnore = new HashMap<>();

        String newCaseStatus = "";
        if (attributes.has(StatusConstants.ATT_STATUS)) {
            newCaseStatus = attributes.get(StatusConstants.ATT_STATUS).asText();
            attributes.remove(StatusConstants.ATT_STATUS);
        }

        NodeRef nodeRef = null;
        if (record.getId().getId().startsWith("workspace://SpacesStore/")) {
            nodeRef = new NodeRef(record.getId().getId());
        }

        // if we get "att_add_someAtt" and "someAtt", then ignore "att_add_*"
        attributes.forEachJ((name, value) -> {

            if (name.startsWith(ADD_CMD_PREFIX) || name.startsWith(REMOVE_CMD_PREFIX)) {

                String attrNameWithoutPrefix;
                if (name.startsWith(ADD_CMD_PREFIX)) {
                    attrNameWithoutPrefix = name.replaceFirst(ADD_CMD_PREFIX, StringUtils.EMPTY);
                } else {
                    attrNameWithoutPrefix = name.replaceFirst(REMOVE_CMD_PREFIX, StringUtils.EMPTY);
                }

                if (attributes.has(attrNameWithoutPrefix) && value.isNotNull()) {
                    String actualName = extractActualAttName(name);
                    attsToIgnore.put(name, actualName);
                }
            }
        });

        handleContentAttribute(attributes);
        RecordRef ecosTypeRef = handleETypeAttribute(attributes, props);
        if (RecordRef.isEmpty(ecosTypeRef) && record.getId().getId().startsWith("workspace")) {
            ecosTypeRef = RecordRef.valueOf(
                recordsService.getAtt(record.getId(), RecordConstants.ATT_TYPE + "?id").asText());
        }

        TypeDto typeDto = ecosTypeRef != null ? typeInfoProvider.getType(ecosTypeRef) : null;
        Map<String, String> propsMapping = Collections.emptyMap();

        if (ecosTypeRef != null && alfAutoModelService != null) {
            propsMapping = alfAutoModelService.getPropsMapping(
                ecosTypeRef,
                attributes.fieldNamesList(),
                true
            );
        }

        DataValue parentValue = attributes.get(RecordConstants.ATT_PARENT);
        if (parentValue.isTextual()) {
            RecordRef parentRef = RecordRef.valueOf(parentValue.asText());
            if (StringUtils.isNotBlank(parentRef.getAppName())
                    && !recordsProperties.getAppName().equals(parentRef.getAppName())) {

                attributes.set(EcosModel.PROP_REMOTE_PARENT_REF.toPrefixString(namespaceService), parentValue);
                attributes.remove(RecordConstants.ATT_PARENT);
            }
        }

        AlfNodeInfo nodeInfo = nodeRef != null ? new AlfNodeInfoImpl(nodeRef, serviceRegistry) : null;

        Iterator<String> names = attributes.fieldNames();
        while (names.hasNext()) {

            String name = names.next();
            DataValue fieldValue = attributes.get(name);
            DataValue fieldRawValue = attributes.get(name);

            if (attsToIgnore.containsKey(name)) {
                log.warn("Found att " + attsToIgnore.get(name) + ", att " + name + " will be ignored");
                continue;
            }

            if (STATE_ATTRIBUTE_NAME.equals(name)) {
                String strValue = fieldValue.asText();
                props.put(InvariantsModel.PROP_IS_DRAFT, "draft".equals(strValue));
                continue;
            }

            String addOrRemoveCmd = null;
            if (record.getId() != RecordRef.EMPTY && name.startsWith(ADD_CMD_PREFIX)) {
                addOrRemoveCmd = ADD_CMD_PREFIX;
                name = name.substring(ADD_CMD_PREFIX.length());
                if (!name.contains(":")) {
                    log.warn("Attribute doesn't exist: " + name);
                    continue;
                }
            } else if (record.getId() != RecordRef.EMPTY && name.startsWith(REMOVE_CMD_PREFIX)) {
                addOrRemoveCmd = REMOVE_CMD_PREFIX;
                name = name.substring(REMOVE_CMD_PREFIX.length());
                if (!name.contains(":")) {
                    log.warn("Attribute doesn't exist: " + name);
                    continue;
                }
            }

            if (ecosPermissionService.isAttProtected(nodeInfo, name)) {
                log.warn("You can't change '" + name +
                    "' attribute of '" + nodeRef +
                    "' because it is protected! Value: " + fieldRawValue);
                continue;
            }

            name = propsMapping.getOrDefault(name, name);

            if (name.equals("_caseStatus")) {
                name = "icase:caseStatusAssoc";
            }

            QName fieldName = QName.resolveToQName(namespaceService, name);
            if (fieldName == null) {
                continue;
            }

            PropertyDefinition propDef = dictionaryService.getProperty(fieldName);

            if (propDef != null) {

                QName typeName = propDef.getDataType().getName();
                if (addOrRemoveCmd != null) {
                    log.warn("Attribute action " + addOrRemoveCmd + " is not supported for node properties." +
                        " Atttribute: " + name);
                    continue;
                }

                if (DataTypeDefinition.CONTENT.equals(typeName)) {
                    contentProps.put(fieldName, fieldValue);
                } else {

                    Object converted = fieldValue.asJavaObj();

                    if (converted != null) {
                        if (!DataTypeDefinition.TEXT.equals(typeName)
                                && converted instanceof String
                                && ((String) converted).isEmpty()) {
                            converted = null;
                        }
                        if (!(converted instanceof Serializable)) {
                            converted = null;
                        }
                    }

                    props.put(fieldName, (Serializable) converted);
                }
            } else {
                AssociationDefinition assocDef = dictionaryService.getAssociation(fieldName);
                if (assocDef != null) {

                    if (contentFileHelper.isFileFromEformFormat(fieldValue)) {
                        if (addOrRemoveCmd != null) {
                            log.warn("Attribute action " + addOrRemoveCmd + " is not supported for fileFromEformFormat." +
                                " Atttribute: " + name);
                            continue;
                        }
                        if (assocDef instanceof ChildAssociationDefinition) {
                            childAssocEformFiles.put(fieldName, fieldValue);
                        } else {
                            attachmentAssocEformFiles.put(fieldName, fieldValue);
                        }
                    } else {
                        Stream<String> refsStream = null;
                        if (fieldValue.isTextual()) {
                            refsStream = Arrays.stream(fieldValue.asText().split(","));
                        } else if (fieldValue.isArray()) {
                            refsStream = StreamSupport.stream(fieldValue.spliterator(), false)
                                .map(DataValue::asText);
                        } else if (fieldValue.isNull()) {
                            refsStream = Stream.empty();
                        }
                        if (refsStream != null) {
                            Set<NodeRef> targetRefs = refsStream
                                .filter(NodeRef::isNodeRef)
                                .map(NodeRef::new)
                                .collect(Collectors.toSet());

                            if (!targetRefs.isEmpty() && addOrRemoveCmd != null) {
                                Set<NodeRef> existedAssocTargets = assocs.get(fieldName);
                                if (existedAssocTargets == null) {
                                    existedAssocTargets = new HashSet<>(nodeUtils.getAssocTargets(nodeRef, fieldName));
                                }
                                if (ADD_CMD_PREFIX.equals(addOrRemoveCmd)) {
                                    existedAssocTargets.addAll(targetRefs);
                                }

                                if (REMOVE_CMD_PREFIX.equals(addOrRemoveCmd)) {
                                    existedAssocTargets.removeAll(targetRefs);
                                }
                                targetRefs = existedAssocTargets;
                            }
                            assocs.put(fieldName, targetRefs);
                        }
                    }
                }
            }
        }

        boolean isNewNode = false;

        if (record.getId() == RecordRef.EMPTY) {

            isNewNode = true;

            if (!props.containsKey(InvariantsModel.PROP_IS_DRAFT)) {
                props.put(InvariantsModel.PROP_IS_DRAFT, false);
            }

            QName type = ecosTypeAlfTypeService.getAlfTypeToCreate(ecosTypeRef, record.getAtts());
            NodeRef parent = getParent(record, type, ecosTypeRef);
            QName parentAssoc = ecosTypeChildAssocService.getChildAssoc(parent, ecosTypeRef, record.getAtts());

            String name = (String) props.get(ContentModel.PROP_NAME);

            if (StringUtils.isBlank(name)) {

                DataValue content = contentProps.get(ContentModel.PROP_CONTENT);
                if (content != null && content.isArray() && content.size() > 0) {
                    content = content.get(0);
                }

                if (content != null && content.isObject()) {
                    for (String attWithName : CONTENT_PROPS_WITH_NAME) {
                        DataValue contentName = content.get(attWithName);
                        if (contentName.isTextual() && StringUtils.isNotBlank(contentName.asText())) {
                            name = contentName.asText();
                            break;
                        }
                    }
                }
                if (StringUtils.isBlank(name) && typeDto != null && typeDto.getName() != null) {
                    name = typeDto.getName().get(Locale.ENGLISH);
                }
                if (StringUtils.isBlank(name)) {
                    name = GUID.generate();
                }
            }

            props.put(ContentModel.PROP_NAME, name);

            nodeRef = nodeUtils.createNode(parent, type, parentAssoc, props);

            DataValue aspectsData = ecosTypeService.getResolvedProperties(ecosTypeRef).get("alfAspects");
            if (StringUtils.isNotBlank(aspectsData.asText())) {
                String aspects = aspectsData.asText();
                List<String> aspectsList = Arrays.stream(aspects.split(","))
                    .filter(StringUtils::isNotBlank)
                    .map(String::trim)
                    .collect(Collectors.toList());
                for (String aspect : aspectsList) {
                    QName aspectQName = QName.resolveToQName(namespaceService, aspect);
                    if (aspectQName != null && !nodeService.hasAspect(nodeRef, aspectQName)) {
                        nodeService.addAspect(nodeRef, aspectQName, new HashMap<>());
                    }
                }
            }

            resultRecord = new RecordMeta(RecordRef.valueOf(nodeRef.toString()));

        } else {

            Map<QName, Serializable> currentProps = nodeService.getProperties(nodeRef);

            List<QName> toRemove = new ArrayList<>();
            for (Map.Entry<QName, Serializable> keyValue : props.entrySet()) {
                if (keyValue.getValue() == null && currentProps.get(keyValue.getKey()) == null) {
                    toRemove.add(keyValue.getKey());
                }
            }
            toRemove.forEach(props::remove);

            if (props.size() > 0) {
                nodeService.addProperties(nodeRef, props);
            }
            resultRecord = new RecordMeta(record.getId());
        }

        final NodeRef finalNodeRef = nodeRef;

        contentProps.forEach((name, value) -> contentFileHelper.processPropFileContent(finalNodeRef, name, value));
        assocs.forEach((name, value) -> nodeUtils.setAssocs(finalNodeRef, value, name, true));
        childAssocEformFiles.forEach((qName, jsonNodes) -> contentFileHelper.processAssocFilesContent(
            qName, jsonNodes, finalNodeRef, true));
        attachmentAssocEformFiles.forEach((qName, jsonNodes) -> contentFileHelper.processAssocFilesContent(
            qName, jsonNodes, finalNodeRef, false));

        if (!newCaseStatus.isEmpty() && caseStatusService != null) {
            caseStatusService.setStatus(finalNodeRef, newCaseStatus);
        }

        final boolean isNewNodeConst = isNewNode;
        AuthContext.runAsSystemJ(() -> {
            Objects.requireNonNull(finalNodeRef);
            if (firstCall) {
                updateComputedAtts(finalNodeRef, isNewNodeConst);
            }
            return null;
        });

        return resultRecord;
    }

    public void updateComputedAtts(NodeRef nodeRef, boolean isNewRecord) {

        RecordRef typeRef = ecosTypeService.getEcosType(nodeRef);
        RecordRef recordRef = RecordRef.create("", nodeRef.toString());

        MetaValue metaValue = createMetaValue(recordRef);
        metaValue.init(AlfGqlContext.getCurrent(), EmptyMetaField.INSTANCE);
        ObjectData attsToStore = computedAttsService.computeAttsToStore(metaValue, isNewRecord, typeRef);

        if (attsToStore.size() > 0) {
            processSingleRecord(new RecordMeta(recordRef, attsToStore), false);
        }

        updateNodeDispName(recordRef);
    }

    private RecordRef handleETypeAttribute(ObjectData attributes, Map<QName, Serializable> props) {

        RecordRef etype = RecordRef.EMPTY;

        DataValue attributeFieldValue = attributes.get(TYPE_ATTRIBUTE_NAME);
        if (attributeFieldValue.isNull()) {
            attributeFieldValue = attributes.get(ETYPE_ATTRIBUTE_NAME);
        }

        if (!attributeFieldValue.isNull()) {

            String attrValue = attributeFieldValue.asText();
            if (!StringUtils.isBlank(attrValue)) {

                etype = RecordRef.valueOf(attrValue);
                String typeId = etype.getId();

                int slashIndex = typeId.indexOf(SLASH_DELIMITER);

                if (slashIndex != -1) {

                    String firstPartOfTypeId = typeId.substring(0, slashIndex);
                    NodeRef typeRef = new NodeRef(WORKSPACE_PREFIX + firstPartOfTypeId);

                    if (nodeService.exists(typeRef)) {

                        props.put(PROP_DOCUMENT_TYPE, typeRef.toString());

                        String secondPartOfRecordId = typeId.substring(slashIndex + 1);
                        NodeRef kindRef = new NodeRef(WORKSPACE_PREFIX + secondPartOfRecordId);

                        if (nodeService.exists(kindRef)) {
                            props.put(PROP_DOCUMENT_KIND, kindRef.toString());
                        }
                    }

                } else {

                    NodeRef typeRef = new NodeRef(WORKSPACE_PREFIX + typeId);
                    if (nodeService.exists(typeRef)) {
                        props.put(PROP_DOCUMENT_TYPE, WORKSPACE_PREFIX + typeId);
                    }
                }
            }
        } else {

            String tkType = attributes.get("tk:type").asText();
            String tkKind = attributes.get("tk:kind").asText();

            if (!tkType.startsWith(WORKSPACE_PREFIX) && tkKind.startsWith(WORKSPACE_PREFIX)) {
                NodeRef tkKindRef = new NodeRef(tkKind);
                if (nodeService.exists(tkKindRef)) {
                    ChildAssociationRef assoc = nodeService.getPrimaryParent(tkKindRef);
                    if (ContentModel.ASSOC_SUBCATEGORIES.equals(assoc.getTypeQName())) {
                        tkType = assoc.getParentRef().toString();
                    }
                }
            }

            if (tkType.startsWith(WORKSPACE_PREFIX)) {
                String ecosTypeId = tkType.replaceFirst(WORKSPACE_PREFIX, "");
                if (!ecosTypeId.isEmpty()) {
                    if (tkKind.startsWith(WORKSPACE_PREFIX)) {
                        ecosTypeId = ecosTypeId + "/" + tkKind.replaceFirst(WORKSPACE_PREFIX, "");
                    }
                    etype = TypeUtils.getTypeRef(ecosTypeId);
                }
            }
        }

        if (RecordRef.isEmpty(etype)) {
            String typeRefFromOptions = attributes.get("/_formOptions/typeRef").asText();
            if (StringUtils.isNotBlank(typeRefFromOptions)) {
                etype = RecordRef.valueOf(typeRefFromOptions);
            }
        }

        if (RecordRef.isNotEmpty(etype)) {
            props.put(EcosTypeModel.PROP_TYPE, etype.getId());
        }

        attributes.remove(TYPE_ATTRIBUTE_NAME);
        attributes.remove(ETYPE_ATTRIBUTE_NAME);

        return etype;
    }

    private void handleContentAttribute(ObjectData attributes) {

        DataValue attributeFieldValue = attributes.get(CONTENT_ATTRIBUTE_NAME);
        if (!attributeFieldValue.isNull()) {
            attributes.remove(CONTENT_ATTRIBUTE_NAME);
            attributes.set(CM_CONTENT_ATTRIBUTE_NAME, attributeFieldValue);
        }
    }

    private String extractActualAttName(String name) {
        if (name.startsWith(ADD_CMD_PREFIX)) {
            return name.substring(ADD_CMD_PREFIX.length());
        } else if (name.startsWith(REMOVE_CMD_PREFIX)) {
            return name.substring(REMOVE_CMD_PREFIX.length());
        } else {
            return name;
        }
    }

    public boolean updateNodeDispName(RecordRef recordRef) {

        if (RecordRef.isEmpty(recordRef) || !NodeRef.isNodeRef(recordRef.getId())) {
            return false;
        }
        NodeRef nodeRef = new NodeRef(recordRef.getId());
        RecordRef ecosType = ecosTypeService.getEcosType(nodeRef);

        if (RecordRef.isEmpty(ecosType)) {
            return false;
        }

        TypeDto typeDto = typeInfoProvider.getType(ecosType);
        if (typeDto == null) {
            return false;
        }

        Map<Locale, String> dispName = typeDto.getInhDispNameTemplate().toMutableMap();
        if (dispName.isEmpty()) {
            return false;
        }

        DataValue resolvedTemplate = recordsTemplateService.resolve(DataValue.create(dispName), recordRef);

        if (resolvedTemplate != null && !resolvedTemplate.isNull()) {
            dispName = resolvedTemplate.asMap(Locale.class, String.class);
        }

        MLText mlText = new MLText();
        mlText.putAll(dispName);

        nodeService.setProperty(nodeRef, ContentModel.PROP_TITLE, mlText);

        Map<QName, Serializable> props = nodeService.getProperties(nodeRef);

        String name = (String) props.get(ContentModel.PROP_NAME);

        if (name == null) {

            if (dispName.isEmpty() && typeDto.getName() != null) {
                dispName = typeDto.getName().getValues();
            }

            String newName;
            if (dispName.containsKey(Locale.ENGLISH)) {
                newName = dispName.get(Locale.ENGLISH);
            } else {
                newName = typeDto.getId();
            }

            nodeService.setProperty(nodeRef, ContentModel.PROP_NAME, getValidNameForNode(nodeRef, newName));
        }
        return true;
    }

    private String getValidNameForNode(NodeRef nodeRef, String name) {
        ChildAssociationRef parentAssoc = nodeService.getPrimaryParent(nodeRef);
        return nodeUtils.getValidChildName(parentAssoc.getParentRef(), parentAssoc.getTypeQName(), name);
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion deletion) {
        for (RecordRef recordRef : deletion.getRecords()) {
            nodeService.deleteNode(new NodeRef(recordRef.getId()));
        }
        return new RecordsDelResult();
    }

    private NodeRef getParent(RecordMeta record, QName type, RecordRef ecosType) {

        String parent = record.getAttribute(RecordConstants.ATT_PARENT, "");
        if (parent.contains("@")) {
            RecordRef parentRef = RecordRef.valueOf(parent);
            if (!parentRef.getAppName().isEmpty() &&
                !parentRef.getAppName().equals(recordsProperties.getAppName())) {

                parent = "";
            }
        }
        if (!parent.isEmpty()) {

            NodeRef parentRef = nodeUtils.getNodeRefOrNull(parent);
            if (parentRef != null) {
                return parentRef;
            }
            if (authorityUtils.isAuthorityRef(parent)) {
                NodeRef authorityRef = authorityUtils.getNodeRef(parent);
                if (authorityRef == null) {
                    throw new RuntimeException("Incorrect authority: " + parent);
                }
                return authorityRef;
            }
            return getByPath(parent);
        }

        if (RecordRef.isNotEmpty(ecosType)) {
            return ecosTypeRootService.getRootForType(ecosType, true);
        }

        NodeRef parentRef = defaultParentByType.get(type);
        if (parentRef != null) {
            return parentRef;
        }

        ClassDefinition typeDef = dictionaryService.getType(type);
        typeDef = typeDef.getParentClassDefinition();

        while (typeDef != null) {
            parentRef = defaultParentByType.get(typeDef.getName());
            if (parentRef != null) {
                return parentRef;
            }
            typeDef = typeDef.getParentClassDefinition();
        }

        return new NodeRef("workspace://SpacesStore/attachments-root");
    }

    private NodeRef getByPath(String path) {

        NodeRef root = nodeService.getRootNode(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);

        List<NodeRef> results = AuthenticationUtil.runAsSystem(() ->
            searchService.selectNodes(root, path, null, namespaceService, false)
        );
        if (results.isEmpty()) {
            throw new IllegalArgumentException("Node not found by path: " + path);
        }
        return results.get(0);
    }

    @Override
    public RecordsQueryResult<Object> queryLocalRecords(RecordsQuery recordsQuery, MetaField metaField) {

        RecordsQueryResult<RecordRef> records = queryRecords(recordsQuery);

        RecordsQueryResult<Object> result = new RecordsQueryResult<>();
        result.merge(records);
        result.setHasMore(records.getHasMore());
        result.setTotalCount(records.getTotalCount());
        result.setRecords((List) getLocalRecordsMeta(records.getRecords(), metaField));

        if (recordsQuery.isDebug()) {
            result.setDebugInfo(getClass(), "query", recordsQuery.getQuery());
            result.setDebugInfo(getClass(), "language", recordsQuery.getLanguage());
        }

        return result;
    }

    @Override
    public RecordsQueryResult<RecordRef> queryRecords(RecordsQuery query) {

        query = new RecordsQuery(query);

        if (query.getLanguage().isEmpty()) {
            query.setLanguage(SearchService.LANGUAGE_FTS_ALFRESCO);
        }

        query.setSortBy(query.getSortBy().stream().map(s -> {
            if (RecordConstants.ATT_MODIFIED.equals(s.getAttribute())) {
                return new SortBy("cm:modified", s.isAscending());
            }
            if (RecordConstants.ATT_CREATED.equals(s.getAttribute())) {
                return new SortBy("cm:created", s.isAscending());
            }
            return s;
        }).collect(Collectors.toList()));

        AlfNodesSearch alfNodesSearch = needNodesSearch(query.getLanguage());

        Long afterIdValue = null;
        Date afterCreated = null;
        if (query.isAfterIdMode()) {

            RecordRef afterId = query.getAfterId();

            AlfNodesSearch.AfterIdType afterIdType = alfNodesSearch.getAfterIdType();

            if (afterId != RecordRef.EMPTY) {
                if (!ID.equals(afterId.getSourceId())) {
                    return new RecordsQueryResult<>();
                }
                NodeRef afterIdNodeRef = new NodeRef(afterId.getId());

                if (afterIdType == null) {
                    throw new IllegalArgumentException("Page parameter afterId is not supported " +
                        "by language " + query.getLanguage() + ". query: " + query);
                }
                switch (afterIdType) {
                    case DB_ID:
                        afterIdValue = (Long) nodeService.getProperty(afterIdNodeRef, ContentModel.PROP_NODE_DBID);
                        break;
                    case CREATED:
                        afterCreated = (Date) nodeService.getProperty(afterIdNodeRef, ContentModel.PROP_CREATED);
                        break;
                }
            } else {
                switch (afterIdType) {
                    case DB_ID:
                        afterIdValue = 0L;
                        break;
                    case CREATED:
                        afterCreated = new Date(0);
                        break;
                }
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Query records with query: " + query +
                " afterIdValue: " + afterIdValue + " afterCreated: " + afterCreated);
        }
        return alfNodesSearch.queryRecords(query, afterIdValue, afterCreated);
    }

    @Override
    public List<String> getSupportedLanguages() {
        return new ArrayList<>(searchByLanguage.keySet());
    }

    @Override
    public List<MetaValue> getLocalRecordsMeta(List<RecordRef> list, MetaField metaField) {
        return list.stream()
            .map(this::createMetaValue)
            .collect(Collectors.toList());
    }

    private MetaValue createMetaValue(RecordRef recordRef) {
        if (recordRef == RecordRef.EMPTY) {
            return new EmptyAlfNode();
        }
        String id = recordRef.getId();
        if (NodeRef.isNodeRef(id) && nodeService.exists(new NodeRef(id))) {
            return new AlfNodeRecord(recordRef);
        }
        return EmptyValue.INSTANCE;
    }

    public ActionResults<RecordRef> executeAction(List<RecordRef> records, GroupActionConfig config) {
        return groupActionService.execute(records, config);
    }

    private AlfNodesSearch needNodesSearch(String language) {

        AlfNodesSearch alfNodesSearch = searchByLanguage.get(language);

        if (alfNodesSearch == null) {
            throw new IllegalArgumentException("Language '" + language + "' is not supported!");
        }

        return alfNodesSearch;
    }

    @Autowired
    public void setGroupActionService(GroupActionService groupActionService) {
        this.groupActionService = groupActionService;
    }

    @Autowired
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.dictionaryService = serviceRegistry.getDictionaryService();
        this.namespaceService = serviceRegistry.getNamespaceService();
        this.searchService = serviceRegistry.getSearchService();
        this.nodeService = serviceRegistry.getNodeService();
        this.serviceRegistry = serviceRegistry;
    }

    @Autowired
    public void setComputedAttsService(ComputedAttsService computedAttsService) {
        this.computedAttsService = computedAttsService;
    }

    @Autowired
    public void setTypeInfoProvider(TypesManager typeInfoProvider) {
        this.typeInfoProvider = typeInfoProvider;
    }

    @Autowired
    public void setRecordsTemplateService(RecordsTemplateService recordsTemplateService) {
        this.recordsTemplateService = recordsTemplateService;
    }

    @Autowired
    public void setEcosPermissionService(EcosPermissionService ecosPermissionService) {
        this.ecosPermissionService = ecosPermissionService;
    }

    @Autowired
    public void setEcosTypeService(EcosTypeService ecosTypeService) {
        this.ecosTypeService = ecosTypeService;
    }

    @Autowired
    public void setNodeUtils(NodeUtils nodeUtils) {
        this.nodeUtils = nodeUtils;
    }

    @Autowired(required = false)
    public void setAlfAutoModelService(AlfAutoModelService alfAutoModelService) {
        this.alfAutoModelService = alfAutoModelService;
    }

    public void register(AlfNodesSearch alfNodesSearch) {
        searchByLanguage.put(alfNodesSearch.getLanguage(), alfNodesSearch);
    }

    public void registerDefaultParentByType(Map<QName, NodeRef> defaultParentByType) {
        this.defaultParentByType.putAll(defaultParentByType);
    }

    @Autowired
    public void setContentFileHelper(AlfNodeContentFileHelper contentFileHelper) {
        this.contentFileHelper = contentFileHelper;
    }

    @Autowired
    public void setEcosTypeRootService(EcosTypeRootService ecosTypeRootService) {
        this.ecosTypeRootService = ecosTypeRootService;
    }

    @Autowired
    public void setEcosTypeAlfTypeService(EcosTypeAlfTypeService ecosTypeAlfTypeService) {
        this.ecosTypeAlfTypeService = ecosTypeAlfTypeService;
    }

    @Autowired
    public void setEcosTypeChildAssocService(EcosTypeChildAssocService ecosTypeChildAssocService) {
        this.ecosTypeChildAssocService = ecosTypeChildAssocService;
    }

    @Autowired
    public void setAuthorityUtils(AuthorityUtils authorityUtils) {
        this.authorityUtils = authorityUtils;
    }

    @Autowired
    public void setRecordsProperties(RecordsProperties recordsProperties) {
        this.recordsProperties = recordsProperties;
    }

    @Autowired(required = false)
    public void setCaseStatusService(@Nullable CaseStatusService caseStatusService) {
        this.caseStatusService = caseStatusService;
    }
}
