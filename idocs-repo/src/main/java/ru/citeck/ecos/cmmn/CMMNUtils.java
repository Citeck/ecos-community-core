package ru.citeck.ecos.cmmn;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.transaction.TransactionalResourceHelper;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.dictionary.PropertyDefinition;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.extensions.surf.util.ParameterCheck;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.cmmn.model.*;
import ru.citeck.ecos.content.converter.ContentValueConverter;
import ru.citeck.ecos.model.*;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import java.io.Serializable;
import java.util.*;

/**
 * Provides Marshaller and Unmarshaller for mappings in CMMN standard v1.1
 * 2016-06-22
 *
 * @author deathNC
 * @author Maxim Strizhov (maxim.strizhov@citeck.ru)
 */
@Component
public class CMMNUtils implements ContentValueConverter {

    private static final String TRANSACTION_ID_BY_NODEREF = CMMNUtils.class.getName() + ".id-storage";
    private static final String TRANSACTION_ID_COUNTER = CMMNUtils.class.getName() + ".id-counter";

    public static final Logger log = LoggerFactory.getLogger(CMMNUtils.class);

    private static final String CASE_ROLES_ROOT_PATH = "app:company_home/app:dictionary/cm:dataLists/cm:case-role";

    public static final String NAMESPACE = "http://www.citeck.ru/ecos/case/cmmn/1.0";
    public static final String NAMESPACE_ECOS = "http://www.citeck.ru/ecos/cmmn/1.0";

    public static final QName QNAME_PROC_DEF_ID = new QName(NAMESPACE_ECOS, "processDefId");

    public static final QName QNAME_NODE_TYPE = new QName(NAMESPACE, "nodeType");
    public static final QName QNAME_SOURCE_ID = new QName(NAMESPACE, "sourceId");

    public static final QName QNAME_TITLE = new QName(NAMESPACE, "title");
    public static final QName QNAME_DESCRIPTION = new QName(NAMESPACE, "description");
    public static final QName QNAME_NEW_ID = new QName(NAMESPACE, "newId");
    public static final QName QNAME_ORIGINAL_EVENT = new QName(NAMESPACE, "originalEvent");
    public static final QName QNAME_IS_RESTART_EVENT = new QName(NAMESPACE, "isRestartEvent");
    public static final QName QNAME_COMPLETNESS_LEVELS = new QName(NAMESPACE, "completnessLevels");
    public static final QName QNAME_START_COMPLETNESS_LEVELS = new QName(NAMESPACE, "startCompletnessLevels");
    public static final QName QNAME_STOP_COMPLETNESS_LEVELS = new QName(NAMESPACE, "stopCompletnessLevels");
    public static final QName QNAME_ROLE_ASSIGNEES = new QName(NAMESPACE, "roleAssignees");
    public static final QName QNAME_ELEMENT_TYPES = new QName(NAMESPACE, "elementTypes");
    public static final QName QNAME_REFERENSE_ROLE = new QName(NAMESPACE, "referenceRoleAssoc");

    public static final QName QNAME_CASE_STATUS = new QName(NAMESPACE, "caseStatus");
    public static final QName QNAME_ACTION_CASE_STATUS = new QName(NAMESPACE, "actionCaseStatus");

    public static final QName QNAME_ENTRY_SENTRY = new QName(NAMESPACE, "entrySentry");
    public static final QName QNAME_EXIT_SENTRY = new QName(NAMESPACE, "exitSentry");

    public static final QName QNAME_CASE_ECOS_TYPE = new QName(NAMESPACE, "caseEcosType");
    public static final QName QNAME_CASE_ECOS_KIND = new QName(NAMESPACE, "caseEcosKind");
    public static final QName QNAME_CASE_TYPE = new QName(NAMESPACE, "caseType");
    public static final QName QNAME_CASE_CONDITION = new QName(NAMESPACE, "condition");

    private static final QName QNAME_UE_ADDITIONAL_DATA_TYPE = new QName(NAMESPACE, "additionalDataType");
    private static final QName QNAME_UE_CONFIRMATION_MESSAGE = new QName(NAMESPACE, "confirmationMessage");
    private static final QName QNAME_UE_SUCCESS_MESSAGE = new QName(NAMESPACE, "successMessage");
    private static final QName QNAME_UE_SUCCESS_MESSAGE_SPAN_CLASS = new QName(NAMESPACE, "successMessageSpanClass");

    private static final QName QNAME_CONFIRMERS = new QName(NAMESPACE, "confirmers");
    private static final QName QNAME_PERFORMER = new QName(NAMESPACE, "performer");
    private static final QName QNAME_PERFORMERS = new QName(NAMESPACE, "performers");
    private static final QName QNAME_PERFORMERS_ROLES = new QName(NAMESPACE, "performersRoles");
    private static final QName QNAME_CONTROLLER = new QName(NAMESPACE, "controller");
    private static final QName QNAME_TASK_AUTHORITIES = new QName(NAMESPACE, "taskAuthorities");
    private static final QName QNAME_TASK_ROLES = new QName(NAMESPACE, "taskRoles");

    public static final QName QNAME_ROLE_VARNAME = new QName(NAMESPACE, "roleVarName");
    private static final QName QNAME_ROLE_REFERENSE = new QName(NAMESPACE, "isReferenceRole");
    private static final QName QNAME_ROLE_SCRIPT = new QName(NAMESPACE, "roleScript");

    public static final Map<QName, org.alfresco.service.namespace.QName> ROLES_ATTRIBUTES_MAPPING;
    public static final Map<QName, org.alfresco.service.namespace.QName> CASE_ATTRIBUTES_MAPPING;
    public static final Map<QName, org.alfresco.service.namespace.QName> ROLES_ASSOCS_MAPPING;
    public static final Map<QName, org.alfresco.service.namespace.QName> STATUS_ASSOCS_MAPPING;
    public static final Map<QName, org.alfresco.service.namespace.QName> EVENT_PROPS_MAPPING;

    private NodeRef caseRolesRoot;

    private ServiceRegistry serviceRegistry;
    private DictionaryService dictionaryService;
    private NodeService nodeService;

    static {
        Map<QName, org.alfresco.service.namespace.QName> mapping = new HashMap<>();
        mapping.put(QNAME_ROLE_VARNAME, ICaseRoleModel.PROP_VARNAME);
        mapping.put(QNAME_ROLE_REFERENSE, ICaseRoleModel.PROP_IS_REFERENCE_ROLE);
        mapping.put(QNAME_ROLE_SCRIPT, ICaseRoleModel.PROP_SCRIPT);
        ROLES_ATTRIBUTES_MAPPING = Collections.unmodifiableMap(mapping);

        mapping = new HashMap<>();
        mapping.put(QNAME_CASE_TYPE, ICaseModel.PROP_CASE_TYPE);
        mapping.put(QNAME_CASE_CONDITION, ICaseModel.PROP_CONDITION);
        mapping.put(QNAME_CASE_ECOS_TYPE, ICaseModel.PROP_CASE_ECOS_TYPE);
        mapping.put(QNAME_CASE_ECOS_KIND, ICaseModel.PROP_CASE_ECOS_KIND);
        CASE_ATTRIBUTES_MAPPING = Collections.unmodifiableMap(mapping);

        mapping = new HashMap<>();
        mapping.put(QNAME_CONFIRMERS, ICaseTaskModel.ASSOC_CONFIRMERS);
        mapping.put(QNAME_PERFORMER, ICaseTaskModel.ASSOC_PERFORMER);
        mapping.put(QNAME_PERFORMERS, ICaseTaskModel.ASSOC_PERFORMERS);
        mapping.put(QNAME_CONTROLLER, ICaseTaskModel.ASSOC_CONTROLLER);
        mapping.put(QNAME_TASK_AUTHORITIES, ICaseTaskModel.ASSOC_AUTHORITIES);
        mapping.put(QNAME_TASK_ROLES, ICaseTaskModel.ASSOC_ROLES);
        mapping.put(QNAME_PERFORMERS_ROLES, CasePerformModel.ASSOC_PERFORMERS_ROLES);
        mapping.put(QNAME_REFERENSE_ROLE, ICaseRoleModel.ASSOC_REFERENCE_ROLE);
        ROLES_ASSOCS_MAPPING = Collections.unmodifiableMap(mapping);

        mapping = new HashMap<>();
        mapping.put(QNAME_UE_ADDITIONAL_DATA_TYPE, EventModel.PROP_ADDITIONAL_DATA_TYPE);
        mapping.put(QNAME_UE_CONFIRMATION_MESSAGE, EventModel.PROP_CONFIRMATION_MESSAGE);
        mapping.put(QNAME_UE_SUCCESS_MESSAGE, EventModel.PROP_SUCCESS_MESSAGE);
        mapping.put(QNAME_UE_SUCCESS_MESSAGE_SPAN_CLASS, EventModel.PROP_SUCCESS_MESSAGE_SPAN_CLASS);
        EVENT_PROPS_MAPPING = Collections.unmodifiableMap(mapping);

        mapping = new HashMap<>();
        mapping.put(QNAME_CASE_STATUS, StagesModel.ASSOC_CASE_STATUS);
        mapping.put(QNAME_ACTION_CASE_STATUS, ActionModel.SetCaseStatus.ASSOC_STATUS);
        STATUS_ASSOCS_MAPPING = Collections.unmodifiableMap(mapping);
    }

    public String extractIdFromNodeRef(NodeRef nodeRef) {
        return nodeRef.toString().replaceAll("workspace://SpacesStore/", "workspace-SpacesStore-");
    }

    public NodeRef extractNodeRefFromCmmnId(String id) {
        return new NodeRef(convertCmmnIdToNodeRefString(id));
    }

    public String convertCmmnIdToNodeRefString(String id) {
        return id.replaceAll("workspace-SpacesStore-", "workspace://SpacesStore/");
    }

    public String convertNodeRefToId(NodeRef nodeRef) {
        ParameterCheck.mandatory("nodeRef", nodeRef);
        Map<NodeRef, String> idByNodeRef = TransactionalResourceHelper.getMap(TRANSACTION_ID_BY_NODEREF);
        String id = idByNodeRef.get(nodeRef);
        if (id == null) {
            id = getNextDocumentId();
            idByNodeRef.put(nodeRef, id);
        }
        return id;
    }

    public String getNextDocumentId() {
        return String.format("id-%d", TransactionalResourceHelper.incrementCount(TRANSACTION_ID_COUNTER));
    }

    public Serializable convertValueForRepo(org.alfresco.service.namespace.QName propertyName, String value) {

        if (StringUtils.isEmpty(value)) {
            return null;
        }

        PropertyDefinition property = dictionaryService.getProperty(propertyName);
        org.alfresco.service.namespace.QName dataType = property.getDataType().getName();

        if (dataType.equals(DataTypeDefinition.CATEGORY) || dataType.equals(DataTypeDefinition.NODE_REF)) {
            if (!NodeRef.isNodeRef(value)) {
                value = convertCmmnIdToNodeRefString(value);
                if (!NodeRef.isNodeRef(value)) {
                    log.error(String.format("Property '%s' has type '%s' but value isn't a NodeRef. value = '%s'",
                            dataType, propertyName.getPrefixString(), value));
                    return null;
                }
            }
            NodeRef ref = new NodeRef(value);
            if (!nodeService.exists(ref)) {
                log.error(String.format("NodeRef '%s' doesn't exists", value));
                return null;
            }
            return ref;
        } else if (dataType.equals(DataTypeDefinition.QNAME)) {
            return org.alfresco.service.namespace.QName.createQName(value);
        } else if (dataType.equals(DataTypeDefinition.TEXT)) {
            return value;
        } else if (dataType.equals(DataTypeDefinition.BOOLEAN)) {
            return Boolean.valueOf(value);
        }
        log.error(String.format("Type '%s' isn't supported by CaseImportService. Property = '%s' value = '%s'",
                dataType, propertyName.toPrefixString(), value));
        return null;
    }

    public String convertValueForCmmn(org.alfresco.service.namespace.QName propertyName, Serializable value) {

        if (value == null) {
            return null;
        }

        PropertyDefinition property = dictionaryService.getProperty(propertyName);
        org.alfresco.service.namespace.QName dataType = property.getDataType().getName();

        if (dataType.equals(DataTypeDefinition.CATEGORY) || dataType.equals(DataTypeDefinition.NODE_REF)) {
            return extractIdFromNodeRef((NodeRef) value);
        }

        return value.toString();
    }

    @Override
    public String convertToConfigValue(org.alfresco.service.namespace.QName propName, Serializable value) {
        return convertValueForCmmn(propName, value);
    }

    @Override
    public Serializable convertToRepoValue(org.alfresco.service.namespace.QName propName, String value) {
        return convertValueForRepo(propName, value);
    }

    public static String elementsToString(Iterable<? extends TCmmnElement> elements) {
        StringBuilder sb = new StringBuilder();
        for (TCmmnElement element : elements) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(element.getId());
        }
        return sb.toString();
    }

    public <T> List<T> stringToElements(String str, Map<String, T> mapping) {
        if (StringUtils.isBlank(str)) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<>();
        for (String elementStr : str.split(",")) {
            if (mapping.containsKey(elementStr)) {
                result.add(mapping.get(elementStr));
            } else {
                log.error("Element " + elementStr + " not found. str: " + str + " mapping: " + mapping);
            }
        }
        return result;
    }

    public List<Sentry> criterionToSentries(List<? extends TCriterion> criterion) {
        List<Sentry> result = new ArrayList<>();
        for (TCriterion c : criterion) {
            Object sentry = c.getSentryRef();
            if (sentry instanceof Sentry) {
                result.add((Sentry) sentry);
            }
        }
        return result;
    }

    public boolean isTask(JAXBElement<? extends TPlanItemDefinition> element) {
        return element.getValue().getClass().equals(TTask.class);
    }

    public boolean isProcessTask(JAXBElement<? extends TPlanItemDefinition> element) {
        return element.getValue().getClass().equals(TProcessTask.class);
    }

    public boolean isStage(JAXBElement<? extends TPlanItemDefinition> element) {
        return element.getValue().getClass().equals(Stage.class);
    }

    public boolean isTimer(JAXBElement<? extends TPlanItemDefinition> element) {
        return element.getValue().getClass().equals(TTimerEventListener.class);
    }

    public boolean isUserEventListener(JAXBElement<? extends TPlanItemDefinition> element) {
        return element.getValue().getClass().equals(TUserEventListener.class);
    }

    public QName convertToXMLQName(org.alfresco.service.namespace.QName alfrescoQName) {
        return new QName(alfrescoQName.getNamespaceURI(), alfrescoQName.getLocalName().replace(':', '.'));
    }

    public org.alfresco.service.namespace.QName convertFromXMLQName(QName qName) {
        return org.alfresco.service.namespace.QName.createQName(qName.getNamespaceURI(), qName.getLocalPart().replace('.', ':'));
    }

    private NodeRef getByPath(String path) {
        NamespaceService namespaceService = serviceRegistry.getNamespaceService();
        SearchService searchService = serviceRegistry.getSearchService();

        NodeRef storeRoot = nodeService.getRootNode(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
        List<NodeRef> nodeRefs = searchService.selectNodes(storeRoot, path, null, namespaceService, false);
        NodeRef result = nodeRefs != null && !nodeRefs.isEmpty() ? nodeRefs.get(0) : null;

        if (result == null) {
            log.warn("Node doesn't found! Path: " + path);
        }
        return result;
    }

    public NodeRef getCaseRolesRoot() {
        if (caseRolesRoot == null) {
            caseRolesRoot = getByPath(CASE_ROLES_ROOT_PATH);
        }
        return caseRolesRoot;
    }

    public NodeRef getCaseRoleById(String id) {
        if (id == null) {
            return null;
        }
        NodeRef root = getCaseRolesRoot();
        if (root == null) {
            return null;
        }
        List<ChildAssociationRef> roles = nodeService.getChildAssocs(root, ContentModel.ASSOC_CONTAINS, RegexQNamePattern.MATCH_ALL);
        for (ChildAssociationRef roleAssoc : roles) {
            String roleId = (String) nodeService.getProperty(roleAssoc.getChildRef(), ICaseRoleModel.PROP_VARNAME);
            if (Objects.equals(id, roleId)) {
                return roleAssoc.getChildRef();
            }
        }
        return null;
    }

    @Autowired
    public void setServiceRegistry(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
        this.dictionaryService = serviceRegistry.getDictionaryService();
        this.nodeService = serviceRegistry.getNodeService();
    }
}
