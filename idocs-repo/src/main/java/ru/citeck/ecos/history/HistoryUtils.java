package ru.citeck.ecos.history;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.dictionary.constraint.ListOfValuesConstraint;
import org.alfresco.repo.transaction.AlfrescoTransactionSupport;
import org.alfresco.service.cmr.dictionary.*;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.extensions.surf.util.I18NUtil;
import ru.citeck.ecos.model.ClassificationModel;
import ru.citeck.ecos.model.HistoryModel;
import ru.citeck.ecos.model.IdocsModel;
import ru.citeck.ecos.providers.ApplicationContextProvider;
import ru.citeck.ecos.utils.TransactionUtils;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by andrey.kozlov on 20.12.2016.
 */
public class HistoryUtils {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd.MM.yyyy");

    public static final Serializable NODE_CREATED = "node.created";
    public static final Serializable NODE_UPDATED = "node.updated";
    public static final Serializable ASSOC_ADDED = "assoc.added";
    public static final Serializable ASSOC_REMOVED = "assoc.removed";
    public static final Serializable ASSOC_UPDATED = "assoc.updated";
    public static final Serializable CHILD_ASSOC_ADDED = "child.assoc.added";
    public static final Serializable CHILD_ASSOC_REMOVED = "child.assoc.removed";

    public static Map<QName, Serializable> eventProperties(Serializable name, Serializable assocDocument, Serializable propertyName, Serializable propertyValue, Serializable taskComment, Serializable propTargetNodeType, Serializable propTargetNodeKind) {
        Map<QName, Serializable> eventProperties = new HashMap<>(7);
        eventProperties.put(HistoryModel.PROP_NAME, name);
        eventProperties.put(HistoryModel.ASSOC_DOCUMENT, assocDocument);
        eventProperties.put(HistoryModel.PROP_PROPERTY_NAME, propertyName);
        if (propertyValue != null) {
            eventProperties.put(HistoryModel.PROP_PROPERTY_VALUE, propertyValue);
        }
        eventProperties.put(HistoryModel.PROP_TASK_COMMENT, taskComment);
        if (propertyValue != null) {
            eventProperties.put(HistoryModel.PROP_TARGET_NODE_TYPE, propTargetNodeType);
        }
        if (propertyValue != null) {
            eventProperties.put(HistoryModel.PROP_TARGET_NODE_KIND, propTargetNodeKind);
        }
        return eventProperties;
    }


    public static String getKeyValue(QName qName, DictionaryService dictionaryService) {
        String modelName = dictionaryService.getProperty(qName).getModel().getName().getPrefixString().replace(":", "_");
        String propName = dictionaryService.getProperty(qName).getName().getPrefixString().replace(":", "_");
        return I18NUtil.getMessage(modelName + ".property." + propName + ".title");
    }

    public static Object getKeyValue(QName qName, Object constraint, DictionaryService dictionaryService, NodeService nodeService) {
        if (DataTypeDefinition.BOOLEAN.equals(dictionaryService.getProperty(qName).getDataType().getName())) {
            if (constraint == null || constraint.equals(false)) {
                return I18NUtil.getMessage("label.no");
            } else {
                return I18NUtil.getMessage("label.yes");
            }
        }
        if (constraint == null) {
            return "—";
        }
        if (DataTypeDefinition.DATE.equals(dictionaryService.getProperty(qName).getDataType().getName())) {
            return new SimpleDateFormat("dd/MM/yyyy").format(constraint);
        }
        if (DataTypeDefinition.MLTEXT.equals(dictionaryService.getProperty(qName).getDataType().getName())) {
            if (constraint instanceof MLText) {
                Locale locale = I18NUtil.getLocale();
                String value = ((MLText) constraint).getClosestValue(locale);
                if (StringUtils.isNotEmpty(value)) {
                    return value;
                }
            }
        }
        if (ClassificationModel.PROP_DOCUMENT_KIND.equals(qName)) {
            return nodeService.getProperty((NodeRef) constraint, ContentModel.PROP_NAME);
        }
        if (IdocsModel.PROP_DOCUMENT_STATUS.equals(qName)) {
            String keyString = "listconstraint.idocs_constraint_documentStatus." + constraint;
            String valueString = I18NUtil.getMessage(keyString);
            if (!Objects.equals(keyString, valueString)) {
                return valueString;
            }
        }

        PropertyDefinition propDef = dictionaryService.getProperty(qName);
        String constListKey = null;

        if (propDef != null) {

            List<ConstraintDefinition> constraints = propDef.getConstraints();

            if (constraints != null && !constraints.isEmpty()) {

                for (ConstraintDefinition constrDef : constraints) {

                    Constraint constr = constrDef.getConstraint();
                    if (ListOfValuesConstraint.class.isAssignableFrom(constr.getClass())) {
                        constListKey = constr.getShortName().replace(":", "_");
                        break;
                    }
                }
            }
        }
        Object constraintValue = null;
        if (constListKey != null) {
            constraintValue = I18NUtil.getMessage("listconstraint." + constListKey + "." + constraint);
        }
        if (constraintValue == null) {
            constraintValue = constraint;
        }
        return constraintValue;
    }

    private static String getCustomChangeValue(NodeRef nodeRef, NodeService nodeService) {
        if (!nodeService.exists(nodeRef)) { return ""; }
        if (ContentModel.TYPE_PERSON.equals(nodeService.getType(nodeRef))) {
            return nodeService.getProperty(nodeRef, ContentModel.PROP_LASTNAME)
                    + " " + nodeService.getProperty(nodeRef, ContentModel.PROP_FIRSTNAME);
        } else {
            /* Single title */
            QName titleQName = getHistoryEventTitleMapperService().getTitleQName(nodeService.getType(nodeRef));
            if (titleQName != null && nodeService.getProperty(nodeRef, titleQName) != null) {
                Serializable value = nodeService.getProperty(nodeRef, titleQName);
                return transformValueToString(value, nodeService);
            }
            /* List title */
            List<QName> titlesQName = getHistoryEventTitleMapperService().getTitleQNames(nodeService.getType(nodeRef));
            if (CollectionUtils.isNotEmpty(titlesQName)) {
                List<String> paramValues = new ArrayList<>(titlesQName.size());
                for (QName qName : titlesQName) {
                    paramValues.add(getValueByQName(nodeRef, qName, nodeService));
                }
                return String.join("; ", paramValues);
            }
            /* Default title */
            return String.valueOf(nodeService.getProperty(nodeRef, ContentModel.PROP_TITLE) != null
                    ? nodeService.getProperty(nodeRef, ContentModel.PROP_TITLE)
                    : nodeService.getProperty(nodeRef, ContentModel.PROP_NAME));
        }
    }

    private static String getValueByQName(NodeRef nodeRef, QName title, NodeService nodeService) {
        /* Property */
        Serializable value = nodeService.getProperty(nodeRef, title);
        if (value != null) {
            return transformValueToString(value, nodeService);
        }
        /* Target association */
        AssociationRef associationRef = getTargetAssociation(nodeRef, title, nodeService);
        if (associationRef != null) {
            NodeRef targetNodeRef = associationRef.getTargetRef();
            if (targetNodeRef != null) {
                return (String) nodeService.getProperty(targetNodeRef, ContentModel.PROP_TITLE);
            } else {
                return "null";
            }
        } else {
            return "null";
        }

    }

    private static AssociationRef getTargetAssociation(NodeRef nodeRef, QName title, NodeService nodeService) {
        if (title == null) {
            return null;
        }
        List<AssociationRef> associationRefs = nodeService.getTargetAssocs(nodeRef, title);
        return CollectionUtils.isNotEmpty(associationRefs) ? associationRefs.get(0) : null;
    }

    private static String transformValueToString(Serializable value, NodeService nodeService) {
        if (value instanceof Date) {
            return DATE_FORMAT.format((Date) value);
        }
        if (value instanceof NodeRef) {
            if (nodeService.exists((NodeRef) value)) {
                return (String) nodeService.getProperty((NodeRef) value, ContentModel.PROP_TITLE);
            } else {
                return "null";
            }
        } else {
            return value.toString();
        }
    }

    private static String getAssocKeyValue(QName qName, DictionaryService dictionaryService) {
        String modelName = dictionaryService.getAssociation(qName).getModel().getName().getPrefixString().replace(":", "_");
        String propName = dictionaryService.getAssociation(qName).getName().getPrefixString().replace(":", "_");
        return I18NUtil.getMessage(modelName + ".association." + propName + ".title");
    }

    private static String getAssocKeyValueForSourceAndTarget(QName qName, boolean forSourceNode, DictionaryService dictionaryService) {
        String assocPeerPrefix = forSourceNode ? "source" : "target";
        String modelName = dictionaryService.getAssociation(qName).getModel().getName().getPrefixString().replace(":", "_");
        String propName = dictionaryService.getAssociation(qName).getName().getPrefixString().replace(":", "_");
        return I18NUtil.getMessage(modelName + ".association." + propName + "." + assocPeerPrefix + ".title");
    }

    public static void addResourceTransaction(Serializable resourceKey, Serializable nodeAssocRef) {
        List<Serializable> listAssocRef = new ArrayList<>();
        listAssocRef.add(nodeAssocRef);
        if (AlfrescoTransactionSupport.getResource(resourceKey) != null) {
            List<AssociationRef> associationRefList = AlfrescoTransactionSupport.getResource(resourceKey);
            listAssocRef.addAll(associationRefList);
        }
        AlfrescoTransactionSupport.bindResource(resourceKey, listAssocRef);
    }

    public static void addUpdateResourseToTransaction(final Serializable resourceKey, final HistoryService historyService, final DictionaryService dictionaryService, final NodeService nodeService) {
        TransactionUtils.doBeforeCommit("HistoryUtils.addUpdateResourseToTransaction", () -> {
            List<AssociationRef> added = new ArrayList<>();
            List<AssociationRef> removed = new ArrayList<>();

            if (AlfrescoTransactionSupport.getResource(ASSOC_ADDED) != null) {
                added.addAll(AlfrescoTransactionSupport.getResource(ASSOC_ADDED));
            }
            if (AlfrescoTransactionSupport.getResource(ASSOC_REMOVED) != null) {
                removed.addAll(AlfrescoTransactionSupport.getResource(ASSOC_REMOVED));
            }
            if (!added.isEmpty() && !removed.isEmpty()) {
                Iterator<AssociationRef> iterAdded = added.iterator();
                while (iterAdded.hasNext()) {
                    AssociationRef associationRefAdded = iterAdded.next();
                    Iterator<AssociationRef> iterRemoved = removed.iterator();
                    while (iterRemoved.hasNext()) {
                        AssociationRef associationRefRemoved = iterRemoved.next();
                        if (associationRefAdded.getTypeQName().equals(associationRefRemoved.getTypeQName())
                                && !isEqualsAssocs(associationRefAdded, associationRefRemoved, nodeService)) {
                            historyService.persistEvent(
                                    HistoryModel.TYPE_BASIC_EVENT,
                                    HistoryUtils.eventProperties(
                                            ASSOC_UPDATED,
                                            associationRefAdded.getSourceRef(),
                                            associationRefAdded.getTypeQName(),
                                            associationRefAdded.getTargetRef().toString(),
                                            getAssocComment(associationRefAdded, associationRefRemoved, dictionaryService, nodeService),
                                            null,
                                            null
                                    )
                            );
                            iterAdded.remove();
                            iterRemoved.remove();
                            break;
                        }
                    }
                }
                AlfrescoTransactionSupport.bindResource(ASSOC_ADDED, added);
                AlfrescoTransactionSupport.bindResource(ASSOC_REMOVED, removed);
            } else if (!added.isEmpty() || !removed.isEmpty()) {
                if (resourceKey.equals(ASSOC_ADDED)) {
                    Iterator<AssociationRef> iter = added.iterator();
                    while (iter.hasNext()) {
                        AssociationRef associationRefAdded = iter.next();
                        historyService.persistEvent(
                                HistoryModel.TYPE_BASIC_EVENT,
                                HistoryUtils.eventProperties(
                                        ASSOC_UPDATED,
                                        associationRefAdded.getSourceRef(),
                                        associationRefAdded.getTypeQName(),
                                        associationRefAdded.getTargetRef().toString(),
                                        getAssocComment(associationRefAdded, null, dictionaryService, nodeService),
                                        null,
                                        null
                                )
                        );
                        iter.remove();
                    }
                    AlfrescoTransactionSupport.bindResource(resourceKey, added);
                } else {
                    Iterator<AssociationRef> iter = removed.iterator();
                    while (iter.hasNext()) {
                        AssociationRef associationRefRemoved = iter.next();
                        historyService.persistEvent(
                                HistoryModel.TYPE_BASIC_EVENT,
                                HistoryUtils.eventProperties(
                                        ASSOC_UPDATED,
                                        associationRefRemoved.getSourceRef(),
                                        associationRefRemoved.getTypeQName(),
                                        null,
                                        getAssocComment(null, associationRefRemoved, dictionaryService, nodeService),
                                        null,
                                        null
                                )
                        );
                        iter.remove();
                    }
                    AlfrescoTransactionSupport.bindResource(resourceKey, removed);
                }
            }
        });
    }

    public static void addUpdateChildAsscosResourseToTransaction(final Serializable resourceKey, final HistoryService historyService, final DictionaryService dictionaryService, final NodeService nodeService, final String nodeRefName) {
        TransactionUtils.doBeforeCommit("HistoryUtils.addUpdateChildAsscosResourseToTransaction", () -> {

            List<ChildAssociationRef> added = new ArrayList<>();
            List<ChildAssociationRef> removed = new ArrayList<>();

            if (AlfrescoTransactionSupport.getResource(CHILD_ASSOC_ADDED) != null) {
                added.addAll(AlfrescoTransactionSupport.getResource(CHILD_ASSOC_ADDED));
            }
            if (AlfrescoTransactionSupport.getResource(CHILD_ASSOC_REMOVED) != null) {
                removed.addAll(AlfrescoTransactionSupport.getResource(CHILD_ASSOC_REMOVED));
            }
            if (!added.isEmpty() && !removed.isEmpty()) {
                Iterator<ChildAssociationRef> iterAdded = added.iterator();
                while (iterAdded.hasNext()) {
                    ChildAssociationRef childAssociationRefAdded = iterAdded.next();
                    Iterator<ChildAssociationRef> iterRemoved = removed.iterator();
                    while (iterRemoved.hasNext()) {
                        ChildAssociationRef childAssociationRefRemoved = iterRemoved.next();
                        if (childAssociationRefAdded.getTypeQName().equals(childAssociationRefRemoved.getTypeQName())
                                && !isEqualsChildAssocs(childAssociationRefAdded, childAssociationRefRemoved, nodeService)) {
                            historyService.persistEvent(
                                    HistoryModel.TYPE_BASIC_EVENT,
                                    HistoryUtils.eventProperties(
                                            ASSOC_UPDATED,
                                            childAssociationRefAdded.getParentRef(),
                                            childAssociationRefAdded.getTypeQName(),
                                            childAssociationRefAdded.getChildRef().toString(),
                                            getChildAssocComment(childAssociationRefAdded, childAssociationRefRemoved, dictionaryService, nodeService, ""),
                                            nodeService.getProperty(childAssociationRefAdded.getChildRef(), ClassificationModel.PROP_DOCUMENT_TYPE),
                                            nodeService.getProperty(childAssociationRefAdded.getChildRef(), ClassificationModel.PROP_DOCUMENT_KIND)
                                    )
                            );
                            iterAdded.remove();
                            iterRemoved.remove();break;
                        }
                    }
                }
                AlfrescoTransactionSupport.bindResource(CHILD_ASSOC_ADDED, added);
                AlfrescoTransactionSupport.bindResource(CHILD_ASSOC_REMOVED, removed);
            } else if (!added.isEmpty() || !removed.isEmpty()) {
                if (resourceKey.equals(CHILD_ASSOC_ADDED)) {
                    Iterator<ChildAssociationRef> iter = added.iterator();
                    while (iter.hasNext()) {
                        ChildAssociationRef childAssociationRefAdded = iter.next();
                        historyService.persistEvent(
                                HistoryModel.TYPE_BASIC_EVENT,
                                HistoryUtils.eventProperties(
                                        ASSOC_UPDATED,
                                        childAssociationRefAdded.getParentRef(),
                                        childAssociationRefAdded.getTypeQName(),
                                        childAssociationRefAdded.getChildRef().toString(),
                                        getChildAssocComment(childAssociationRefAdded, null, dictionaryService, nodeService, ""),
                                        nodeService.getProperty(childAssociationRefAdded.getChildRef(), ClassificationModel.PROP_DOCUMENT_TYPE),
                                        nodeService.getProperty(childAssociationRefAdded.getChildRef(), ClassificationModel.PROP_DOCUMENT_KIND)
                                )
                        );
                        iter.remove();
                    }
                    AlfrescoTransactionSupport.bindResource(resourceKey, added);
                } else {
                    Iterator<ChildAssociationRef> iter = removed.iterator();
                    while (iter.hasNext()) {
                        ChildAssociationRef childAssociationRefRemoved = iter.next();
                        if (!nodeService.exists(childAssociationRefRemoved.getParentRef())) {
                            continue;
                        }
                        historyService.persistEvent(
                                HistoryModel.TYPE_BASIC_EVENT,
                                HistoryUtils.eventProperties(
                                        ASSOC_UPDATED,
                                        childAssociationRefRemoved.getParentRef(),
                                        childAssociationRefRemoved.getTypeQName(),
                                        null,
                                        getChildAssocComment(null, childAssociationRefRemoved, dictionaryService, nodeService, nodeRefName),
                                        nodeService.getProperty(childAssociationRefRemoved.getParentRef(), ClassificationModel.PROP_DOCUMENT_TYPE),
                                        nodeService.getProperty(childAssociationRefRemoved.getParentRef(), ClassificationModel.PROP_DOCUMENT_KIND)
                                )
                        );
                        iter.remove();
                    }
                    AlfrescoTransactionSupport.bindResource(resourceKey, removed);
                }
            }
        });
    }

    public static String getAssocComment(AssociationRef added, AssociationRef removed, DictionaryService dictionaryService, NodeService nodeService) {
        if (added != null && removed != null) {
            return HistoryUtils.getAssocKeyValue(added.getTypeQName(), dictionaryService)
                    + ": "
                    + HistoryUtils.getCustomChangeValue(removed.getTargetRef(), nodeService)
                    + " -> "
                    + HistoryUtils.getCustomChangeValue(added.getTargetRef(), nodeService);
        } else if (added != null) {
            return HistoryUtils.getAssocKeyValue(added.getTypeQName(), dictionaryService)
                    + ": — -> "
                    + HistoryUtils.getCustomChangeValue(added.getTargetRef(), nodeService);
        } else if (removed != null) {
            return HistoryUtils.getAssocKeyValue(removed.getTypeQName(), dictionaryService)
                    + ": "
                    + HistoryUtils.getCustomChangeValue(removed.getTargetRef(), nodeService)
                    + " -> —";
        } else {
            return "Something went wrong... Contact the administrator.";
        }
    }

    public static String getAssocCommentForSourceAndTarget (AssociationRef added,
                                                            AssociationRef removed,
                                                            boolean forSourceNode,
                                                            DictionaryService dictionaryService,
                                                            NodeService nodeService) {
        if (added != null && removed != null) {
            return HistoryUtils.getAssocKeyValueForSourceAndTarget(added.getTypeQName(), forSourceNode, dictionaryService)
                    + ": "
                    + HistoryUtils.getCustomChangeValue(forSourceNode ? removed.getTargetRef() : removed.getSourceRef(),
                    nodeService)
                    + " -> "
                    + HistoryUtils.getCustomChangeValue(forSourceNode ? added.getTargetRef() : added.getSourceRef(),
                    nodeService);
        } else if (added != null) {
            return HistoryUtils.getAssocKeyValueForSourceAndTarget(added.getTypeQName(), forSourceNode, dictionaryService)
                    + ": — -> "
                    + HistoryUtils.getCustomChangeValue(forSourceNode ? added.getTargetRef() : added.getSourceRef(),
                    nodeService);
        } else if (removed != null) {
            return HistoryUtils.getAssocKeyValueForSourceAndTarget(removed.getTypeQName(), forSourceNode, dictionaryService)
                    + ": "
                    + HistoryUtils.getCustomChangeValue(forSourceNode ? removed.getTargetRef() : removed.getSourceRef(),
                    nodeService)
                    + " -> —";
        } else {
            return "Something went wrong... Contact the administrator.";
        }
    }

    public static String getChildAssocComment(ChildAssociationRef added, ChildAssociationRef removed, DictionaryService dictionaryService, NodeService nodeService, String nodeRefName) {
        if (added != null && removed != null) {
            return HistoryUtils.getAssocKeyValue(added.getTypeQName(), dictionaryService)
                    + ": "
                    + HistoryUtils.getCustomChangeValue(removed.getChildRef(), nodeService)
                    + " -> "
                    + HistoryUtils.getCustomChangeValue(added.getChildRef(), nodeService);
        } else if (added != null) {
            return HistoryUtils.getAssocKeyValue(added.getTypeQName(), dictionaryService)
                    + ": — -> "
                    + HistoryUtils.getCustomChangeValue(added.getChildRef(), nodeService);
        } else if (removed != null) {
            String deletedName = nodeRefName.isEmpty() ? HistoryUtils.getCustomChangeValue(removed.getChildRef(), nodeService) : nodeRefName;
            return HistoryUtils.getAssocKeyValue(removed.getTypeQName(), dictionaryService)
                    + ": "
                    + deletedName
                    + " -> —";
        } else {
            return "Something went wrong... Contact the administrator.";
        }
    }

    private static boolean isEqualsAssocs(AssociationRef added, AssociationRef removed, NodeService nodeService) {
        return HistoryUtils.getCustomChangeValue(removed.getTargetRef(), nodeService).equals(HistoryUtils.getCustomChangeValue(added.getTargetRef(), nodeService));
    }

    private static boolean isEqualsChildAssocs(ChildAssociationRef added, ChildAssociationRef removed, NodeService nodeService) {
        return HistoryUtils.getCustomChangeValue(removed.getChildRef(), nodeService).equals(HistoryUtils.getCustomChangeValue(added.getChildRef(), nodeService));
    }

    private static HistoryEventTitleMapperService getHistoryEventTitleMapperService() {
        return ApplicationContextProvider.getBean(HistoryEventTitleMapperService.class);
    }

}
