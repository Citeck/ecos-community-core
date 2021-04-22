package ru.citeck.ecos.records.source.alf.meta;

import lombok.Data;
import lombok.Getter;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.node.MLPropertyInterceptor;
import org.alfresco.service.cmr.repository.MLText;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.cmr.version.Version;
import org.alfresco.service.cmr.version.VersionService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.extensions.surf.util.I18NUtil;
import ru.citeck.ecos.action.ActionModule;
import ru.citeck.ecos.action.node.NodeActionsService;
import ru.citeck.ecos.attr.prov.VirtualScriptAttributes;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.document.sum.DocSumService;
import ru.citeck.ecos.domain.model.alf.service.AlfAutoModelService;
import ru.citeck.ecos.graphql.AlfGqlContext;
import ru.citeck.ecos.graphql.node.Attribute;
import ru.citeck.ecos.graphql.node.GqlAlfNode;
import ru.citeck.ecos.graphql.node.GqlQName;
import ru.citeck.ecos.model.EcosModel;
import ru.citeck.ecos.model.lib.status.constants.StatusConstants;
import ru.citeck.ecos.model.lib.attributes.dto.AttributeDef;
import ru.citeck.ecos.model.lib.attributes.dto.AttributeType;
import ru.citeck.ecos.model.lib.status.dto.StatusDef;
import ru.citeck.ecos.model.lib.status.service.StatusService;
import ru.citeck.ecos.model.lib.type.dto.TypeModelDef;
import ru.citeck.ecos.node.AlfNodeContentPathRegistry;
import ru.citeck.ecos.node.AlfNodeInfo;
import ru.citeck.ecos.node.DisplayNameService;
import ru.citeck.ecos.node.EcosTypeService;
import ru.citeck.ecos.records.RecordsUtils;
import ru.citeck.ecos.records.meta.MetaUtils;
import ru.citeck.ecos.records.source.alf.AlfNodeMetaEdge;
import ru.citeck.ecos.records.source.alf.file.FileRepresentation;
import ru.citeck.ecos.records.source.common.MLTextValue;
import ru.citeck.ecos.records.type.TypeDto;
import ru.citeck.ecos.records2.*;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;
import ru.citeck.ecos.records2.graphql.meta.value.MetaEdge;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.graphql.meta.value.MetaValue;
import ru.citeck.ecos.state.ItemsUpdateState;
import ru.citeck.ecos.utils.NewUIUtils;
import ru.citeck.ecos.utils.NodeUtils;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AlfNodeRecord implements MetaValue {

    public static final String ATTR_DOC_SUM = "docSum";
    public static final String ATTR_TYPE = "type";
    public static final String ATTR_TYPE_UPPER = "TYPE";

    private static final String ATTR_UI_TYPE = "uiType";
    private static final String ATTR_ASPECTS = "attr:aspects";
    private static final String ATTR_IS_DOCUMENT = "attr:isDocument";
    private static final String ATTR_IS_CONTAINER = "attr:isContainer";
    private static final String ATTR_PARENT = "attr:parent";
    private static final String ATTR_PERMISSIONS = "permissions";
    private static final String ATTR_PENDING_UPDATE = "pendingUpdate";
    private static final String ATTR_VERSION = "version";
    private static final String ATTR_CASE_STATUS = "caseStatus";
    private static final String ATTR_CM_MODIFIED = "cm:modified";
    private static final String CASE_STATUS_NAME_SCHEMA = "icase:caseStatusAssoc.cm:name";
    private static final String CASE_STATUS_ID_SCHEMA = "icase:caseStatusAssoc?str";
    private static final String CASE_STATUS_DISP_SCHEMA = "icase:caseStatusAssoc?disp";
    private static final String CASE_STATUS_PROP_SCHEMA = "icase:caseStatusAssoc-prop";
    private static final String ASSOC_SRC_ATTR_PREFIX = "assoc_src_";
    private static final String CONTENT_ATTRIBUTE_NAME = "_content";
    private static final String CM_CONTENT_ATTRIBUTE_NAME = "cm:content";
    private static final String PEOPLE_SOURCE_ID = "people";
    private static final String VIRTUAL_SCRIPT_ATTS_ID = "virtualScriptAttributesProvider";
    private static final String DEFAULT_VERSION_LABEL = "1.0";
    private static final String ATTR_DICT = "dict";

    private static final Set<String> attributesAsRecord;

    static {
        attributesAsRecord = Collections.newSetFromMap(new ConcurrentHashMap<>());
        attributesAsRecord.add("wfm:documentEcosType");
    }

    private NodeRef nodeRef;
    private final RecordRef recordRef;
    private GqlAlfNode node;
    private AlfGqlContext context;
    private Map<String, AttributeDef> attributesDef = new HashMap<>();

    private Map<String, String> attributesMapping = Collections.emptyMap();

    @Getter(lazy = true)
    private final Permissions permissions = new Permissions();

    public AlfNodeRecord(RecordRef recordRef) {
        this.recordRef = recordRef;
    }

    @Override
    public <T extends QueryContext> void init(T context, MetaField field) {

        this.context = (AlfGqlContext) context;
        this.nodeRef = RecordsUtils.toNodeRef(recordRef);
        this.node = this.context.getNode(nodeRef).orElse(null);

        RecordRef typeRef = getRecordType();

        if (RecordRef.isNotEmpty(typeRef)) {

            AlfGqlContext alfContext = (AlfGqlContext) context;
            AlfAutoModelService autoModelService = alfContext.getService(AlfAutoModelService.QNAME);
            attributesMapping = autoModelService.getPropsMapping(typeRef);

            EcosTypeService typeService = alfContext.getService(EcosTypeService.QNAME);
            TypeDto typeDef = typeService.getTypeDef(typeRef);
            if (typeDef != null) {
                TypeModelDef model = typeDef.getModel();
                if (model != null) {
                    List<AttributeDef> attributes = model.getAttributes();
                    attributes.forEach(att -> attributesDef.put(att.getId(), att));
                }
            }
        }
    }

    @Override
    public String getId() {
        if (recordRef.getAppName().isEmpty() && recordRef.getSourceId().isEmpty()) {
            return "alfresco/@" + recordRef.toString();
        }
        return recordRef.toString();
    }

    @Override
    public String getString() {
        return getId();
    }

    @Override
    public String getDisplayName() {
        DisplayNameService displayNameService = context.getService(DisplayNameService.QNAME);
        return displayNameService.getDisplayName(new NodeInfo());
    }

    @Override
    public boolean has(String name) {

        if (!context.getEcosPermissionService().isAttVisible(new NodeInfo(), name)) {
            return false;
        }

        name = attributesMapping.getOrDefault(name, name);

        if (RecordConstants.ATT_DOC_NUM.equals(name)) {
            name = EcosModel.PROP_DOC_NUM.toPrefixString(context.getNamespaceService());
        }

        if (StringUtils.isNotEmpty(name) && name.startsWith(ASSOC_SRC_ATTR_PREFIX)) {
            List<MetaValue> sourceAssocs = getSourceAssocs(node.nodeRef(), name, null);
            return CollectionUtils.isNotEmpty(sourceAssocs);
        }

        if ("_content".equals(name)) {
            AlfNodeContentPathRegistry contentPath = context.getService(AlfNodeContentPathRegistry.QNAME);
            String path = contentPath.getContentPath(new NodeInfo());
            if (path == null) {
                path = "cm:content";
            }
            RecordsService recordsService = context.getRecordsService();
            if (recordsService == null) {
                return false;
            }
            if (path.indexOf('.') == -1) {
                if ("_content".equals(path)) {
                    return false;
                }
                return has(path);
            }
            String query = AlfNodeUtils.resolveHasContentPathQuery(path);
            return Boolean.TRUE.toString().equals(recordsService.getAttribute(recordRef, query).asText());
        }

        Attribute nodeAtt = node.attribute(name);

        if (Attribute.Type.UNKNOWN.equals(nodeAtt.type())) {
            return false;
        }

        List<?> values = nodeAtt.getValues();

        return values != null && !values.isEmpty();
    }

    @Override
    public RecordRef getRecordType() {
        NodeRef nodeRef = new NodeRef(node.nodeRef());
        EcosTypeService ecosTypeService = context.getService(EcosTypeService.QNAME);
        return ecosTypeService.getEcosType(nodeRef);
    }

    @Override
    public List<? extends MetaValue> getAttribute(String name, MetaField field) {

        if (node == null) {
            return Collections.emptyList();
        }

        if (!context.getEcosPermissionService().isAttVisible(new NodeInfo(), name)) {
            return Collections.emptyList();
        }

        AttributeDef attDef = attributesDef.get(name);
        AttributeType attType = null;
        if (attDef != null) {
            attType = attDef.getType();
        }

        name = attributesMapping.getOrDefault(name, name);

        List<? extends MetaValue> attribute = null;

        if (StringUtils.equals(name, CONTENT_ATTRIBUTE_NAME)) {

            name = CM_CONTENT_ATTRIBUTE_NAME;

        } else if (StringUtils.equals(RecordConstants.ATT_MODIFIED, name)) {

            name = ATTR_CM_MODIFIED;

        } else if (RecordConstants.ATT_DOC_NUM.equals(name)) {

            name = EcosModel.PROP_DOC_NUM.toPrefixString(context.getNamespaceService());
        }

        switch (name) {

            case ATTR_UI_TYPE:

                NewUIUtils utils = context.getService(NewUIUtils.QNAME);
                attribute = Collections.singletonList(new AlfNodeAttValue(utils.getUITypeForRecordAndUser(recordRef)));
                break;

            case RecordConstants.ATT_MODIFIER: {

                NodeRef nodeRef = new NodeRef(node.nodeRef());
                String propertyValue = (String) context.getNodeService().getProperty(nodeRef,
                    ContentModel.PROP_MODIFIER);
                if (propertyValue != null) {
                    RecordRef recordRef = RecordRef.create(PEOPLE_SOURCE_ID, propertyValue);
                    MetaValue metaValue = toMetaValue(recordRef, field);
                    return Collections.singletonList(metaValue);
                }
                return null;
            }

            case ATTR_TYPE:
            case ATTR_TYPE_UPPER:

                attribute = MetaUtils.toMetaValues(node.type(), context, field);
                break;

            case ATTR_ASPECTS:

                attribute = node.aspects()
                    .stream()
                    .map(o -> toMetaValue(null, o, field))
                    .collect(Collectors.toList());
                break;

            case ATTR_IS_CONTAINER:

                attribute = MetaUtils.toMetaValues(node.isContainer(), context, field);
                break;

            case ATTR_IS_DOCUMENT:

                attribute = MetaUtils.toMetaValues(node.isDocument(), context, field);
                break;

            case ATTR_PARENT:
            case RecordConstants.ATT_PARENT:

                GqlAlfNode parent = node.getParent();
                if (parent != null) {
                    MetaValue parentValue = new AlfNodeRecord(RecordRef.valueOf(parent.nodeRef()));
                    parentValue.init(context, field);
                    attribute = Collections.singletonList(parentValue);
                }
                break;

            case RecordConstants.ATT_FORM_KEY:

                attribute = MetaUtils.toMetaValues(getFormAndDashboardKeys(true), context, field);
                break;

            case ATTR_PERMISSIONS:

                return Collections.singletonList(getPermissions());

            case "previewInfo":

                AlfNodeContentPathRegistry contentPath = context.getService(AlfNodeContentPathRegistry.QNAME);
                String path = contentPath.getContentPath(new NodeInfo());
                RecordsService recordsService = context.getRecordsService();
                if (recordsService == null) {
                    return null;
                }
                DataValue previewInfo = recordsService.getAttribute(recordRef, path + ".previewInfo?json");
                return MetaUtils.toMetaValues(previewInfo, context, field);

            case ATTR_PENDING_UPDATE:

                ItemsUpdateState service = context.getService("ecos.itemsUpdateState");
                boolean pendingUpdate = service.isPendingUpdate(new NodeRef(node.nodeRef()));
                attribute = Collections.singletonList(toMetaValue(null, pendingUpdate, field));
                break;

            case ATTR_VERSION:

                VersionService versionService = context.getServiceRegistry().getVersionService();
                Version currentVersion = versionService.getCurrentVersion(new NodeRef(node.nodeRef()));
                String versionLabel = currentVersion != null && StringUtils.isNotBlank(currentVersion.getVersionLabel())
                    ? currentVersion.getVersionLabel() : DEFAULT_VERSION_LABEL;
                attribute = Collections.singletonList(toMetaValue(null, versionLabel, field));
                break;

            case RecordConstants.ATT_ACTIONS:

                NodeActionsService nodeActionsService = context.getService("nodeActionsService");
                List<ActionModule> actions = nodeActionsService.getNodeActions(nodeRef);
                attribute = MetaUtils.toMetaValues(actions, context, field);
                break;

            case ATTR_DOC_SUM:

                DocSumService docSumService = context.getService("docSumService");
                attribute = MetaUtils.toMetaValues(docSumService.getSum(nodeRef), context, field);
                break;

            case StatusConstants.ATT_STATUS: {

                StatusMetaValue statusMeta = getCaseStatusMeta(context);
                if (statusMeta != null) {
                    return Collections.singletonList(statusMeta);
                }
                return Collections.emptyList();
            }
            case ATTR_CASE_STATUS:

                String caseStatusName = getCaseStatusName(context.getRecordsService());
                if (StringUtils.isNotBlank(caseStatusName)) {
                    MetaValue statusMeta = context.getServiceFactory().getMetaValuesConverter()
                        .toMetaValue(RecordRef.create("", "status", caseStatusName));
                    statusMeta.init(context, field);

                    attribute = Collections.singletonList(statusMeta);
                }

                break;

            case ATTR_DICT:
                QName fullName = context.getNodeService().getType(nodeRef);
                String shortName = fullName.toPrefixString();
                String formKey = "alf_" + shortName;
                MetaValue dictMeta = new DictRecord(fullName, shortName, formKey);
                dictMeta.init(context, field);
                attribute = Collections.singletonList(dictMeta);
                break;

            default:

                if (name.contains(ASSOC_SRC_ATTR_PREFIX)) {
                    attribute = getSourceAssocs(node.nodeRef(), name, field);
                    break;
                }

                Attribute nodeAtt = node.attribute(name);
                if (nodeAtt == null) {
                    return Collections.emptyList();
                }

                if (Attribute.Type.UNKNOWN.equals(nodeAtt.type())) {
                    Optional<QName> attQname = context.getQName(name).map(GqlQName::getQName);
                    if (attQname.isPresent()) {
                        VirtualScriptAttributes attributes = context.getService(VIRTUAL_SCRIPT_ATTS_ID);
                        if (attributes != null && attributes.provides(attQname.get())) {
                            Object value = attributes.getAttribute(new NodeRef(node.nodeRef()), attQname.get());
                            attribute = MetaUtils.toMetaValues(value, context, field);
                        }
                    }
                }

                if (attribute == null) {
                    List<?> values = nodeAtt.getValues();
                    if (attType != null && !AttributeType.TEXT.equals(attType)) {
                        values = values.stream().filter(val ->
                            val != null && (!(val instanceof String) || !((String) val).isEmpty())
                        ).collect(Collectors.toList());
                    }
                    attribute = values.stream()
                        .map(v -> toMetaValue(nodeAtt, v, field))
                        .collect(Collectors.toList());
                }
        }

        return attribute != null ? attribute : Collections.emptyList();
    }

    @Nullable
    private StatusMetaValue getCaseStatusMeta(AlfGqlContext context) {
        RecordsService recordsService = context.getRecordsService();
        if (recordsService == null) {
            return null;
        }

        StatusMetaDto caseStatusMeta = recordsService.getMeta(recordRef, StatusMetaDto.class);

        String statusEcosId = caseStatusMeta.getEcosId();
        StatusMetaValue statusMeta;
        if (StringUtils.isBlank(statusEcosId)) {
            statusMeta = getStatusMetaValue(caseStatusMeta);
        } else {
            statusMeta = getEcosStatusMetaValue(context, caseStatusMeta);
        }

        return statusMeta;
    }

    @NotNull
    private StatusMetaValue getStatusMetaValue(StatusMetaDto caseStatusMeta) {
        String statusId = caseStatusMeta.getNodeRef();
        return new StatusMetaValue(
            caseStatusMeta.getId(),
            caseStatusMeta.getName(),
            statusId != null ? new NodeRef(statusId) : null
        );
    }

    private StatusMetaValue getEcosStatusMetaValue(AlfGqlContext context, StatusMetaDto statusMetaDto) {
        StatusService statusService = context.getStatusService();
        if (statusService == null) {
            return null;
        }

        String ecosStatusId = statusMetaDto.getEcosId();
        Map<String, StatusDef> statuses = statusService.getStatusesByDocument(recordRef);
        StatusDef statusDef = statuses.get(ecosStatusId);
        if (statusDef == null) {
            return null;
        }

        String statusName = statusDef.getName().getClosestValue(I18NUtil.getLocale());
        return new StatusMetaValue(
            statusMetaDto.getEcosId(),
            statusName,
            new NodeRef("et-status://virtual/" + ecosStatusId)
        );
    }

    private String getCaseStatusName(RecordsService recordsService) {
        if (recordsService == null) {
            return null;
        }

        DataValue caseStatusNode = recordsService.getAttribute(recordRef, CASE_STATUS_NAME_SCHEMA);
        if (caseStatusNode.isNull()) {
            return null;
        }

        return caseStatusNode.asText();
    }

    @Override
    public Object getAs(String type) {
        if (node != null) {
            return FileRepresentation.fromAlfNode(node, context);
        }
        return null;
    }

    private List<KeyWithDisp> getFormAndDashboardKeys(boolean withAlfType) {

        List<KeyWithDisp> keys = new ArrayList<>();

        NodeRef type = getNodeRefFromProp("tk:type");

        if (type != null) {
            String typeTitle = getNodeRefDisplayName(type);

            NodeRef kind = getNodeRefFromProp("tk:kind");
            if (kind != null) {

                String kindTitle = getNodeRefDisplayName(kind);

                String value = String.format("type_%s/%s", type.getId(), kind.getId());
                String disp = String.format("%s - %s", typeTitle, kindTitle);

                keys.add(new KeyWithDisp(value, disp));
            }

            keys.add(new KeyWithDisp(String.format("type_%s", type.getId()), typeTitle));
        }

        if (withAlfType) {
            String alfTypeKey = "alf_" + node.type();
            String alfTypeTitle = node.typeQName().map(GqlQName::classTitle).orElse(alfTypeKey);
            alfTypeTitle = "A: " + alfTypeTitle;
            keys.add(new KeyWithDisp(alfTypeKey, alfTypeTitle));
        }

        return keys;
    }

    private String getNodeRefDisplayName(NodeRef nodeRef) {
        if (nodeRef == null) {
            return "null";
        }
        RecordRef ref = RecordRef.create("", nodeRef.toString());
        DataValue value = context.getRecordsService().getAttribute(ref, ".disp");
        return value.asText();
    }

    private NodeRef getNodeRefFromProp(String propName) {
        Attribute att = node.attribute(propName);
        String value = null;
        if (att != null) {
            value = att.value().orElse(null);
        }
        return value != null && NodeRef.isNodeRef(value) ? new NodeRef(value) : null;
    }

    private List<MetaValue> getSourceAssocs(String nodeRefStr, String attrName, MetaField field) {
        if (StringUtils.isBlank(attrName) || !NodeRef.isNodeRef(nodeRefStr)) {
            return Collections.emptyList();
        }
        String attrQNameValue = attrName.replace(ASSOC_SRC_ATTR_PREFIX, StringUtils.EMPTY);
        QName attr = QName.resolveToQName(context.getNamespaceService(), attrQNameValue);
        NodeUtils nodeUtils = context.getService(NodeUtils.QNAME);
        List<NodeRef> nodeRefs = nodeUtils.getAssocSources(new NodeRef(nodeRefStr), attr);
        return nodeRefs.stream()
            .map(nodeRef -> {
                MetaValue record = new AlfNodeRecord(RecordRef.valueOf(nodeRef.toString()));
                record.init(context, field);
                return record;
            })
            .collect(Collectors.toList());
    }

    @Override
    public MetaEdge getEdge(String name, MetaField field) {
        if (name.equals(StatusConstants.ATT_STATUS)) {
            return new EcosStatusEdge(recordRef, context, this);
        }
        return getAlfNodeMetaEdge(name);
    }

    @NotNull
    private AlfNodeMetaEdge getAlfNodeMetaEdge(String name) {
        QName type = null;
        if (node != null) {
            type = node.getType();
        }
        String ecosModelName = name;
        name = attributesMapping.getOrDefault(name, name);
        return new AlfNodeMetaEdge(context, type, name, ecosModelName, this);
    }

    private MetaValue toMetaValue(Attribute att, Object value, MetaField field) {
        MetaValue metaValue;
        if (context.getNodeUtils().isNodeRef(value)) {
            metaValue = new AlfNodeRecord(RecordRef.valueOf(value.toString()));
        } else if (value instanceof MLText) {
            metaValue = new MLTextValue((MLText) value);
        } else if (att != null && value instanceof String && attributesAsRecord.contains(att.name())) {
            RecordRef recordRef = RecordRef.valueOf((String) value);
            metaValue = context.getServiceFactory().getMetaValuesConverter().toMetaValue(recordRef);
        } else {
            if (att != null) {
                metaValue = new AlfNodeAttValue(att, value);
            } else {
                metaValue = new AlfNodeAttValue(value);
            }
        }
        metaValue.init(context, field);
        return metaValue;
    }

    private MetaValue toMetaValue(RecordRef recordRef, MetaField field) {
        MetaValue value = context.getServiceFactory()
            .getMetaValuesConverter()
            .toMetaValue(recordRef);
        value.init(context, field);
        return value;
    }

    public static void addAttAsRecord(String att) {
        attributesAsRecord.add(att);
    }

    public class Permissions implements MetaValue {

        @Override
        public String getString() {
            return null;
        }

        @Override
        public boolean has(String permission) {
            if (nodeRef == null) {
                return false;
            }
            PermissionService permissionService = context.getServiceRegistry().getPermissionService();
            AccessStatus accessStatus = permissionService.hasPermission(nodeRef, permission);
            return AccessStatus.ALLOWED.equals(accessStatus);
        }
    }

    public class NodeInfo implements AlfNodeInfo {

        @Override
        public QName getType() {
            return node.getType();
        }

        @Override
        public NodeRef getNodeRef() {
            return new NodeRef(node.nodeRef());
        }

        @Override
        public Map<QName, Serializable> getProperties() {

            Map<QName, Serializable> props = node.getProperties();

            if (MLPropertyInterceptor.isMLAware()) {
                return props;
            }
            Map<QName, Serializable> result = new HashMap<>();

            for (Map.Entry<QName, Serializable> entry : props.entrySet()) {
                Serializable value = entry.getValue();
                if (value instanceof MLText) {
                    result.put(entry.getKey(), ((MLText) value).getClosestValue(I18NUtil.getLocale()));
                } else {
                    result.put(entry.getKey(), value);
                }
            }

            return result;
        }
    }

    public static class KeyWithDisp implements MetaValue {

        String value;
        String disp;

        public KeyWithDisp(String value, String disp) {
            this.value = value;
            this.disp = disp;
        }

        @Override
        public String getString() {
            return value;
        }

        @Override
        public String getDisplayName() {
            return disp;
        }
    }

    @Data
    public static class StatusMetaDto {
        @MetaAtt("icase:caseStatusAssoc.cm:name")
        private String id;
        @MetaAtt("icase:caseStatusAssoc-prop")
        private String ecosId;
        @MetaAtt("icase:caseStatusAssoc?disp")
        private String name;
        @MetaAtt("icase:caseStatusAssoc?id")
        private String nodeRef;
    }
}

