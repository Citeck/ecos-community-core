package ru.citeck.ecos.records.source.alf.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.log4j.Log4j;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.service.cmr.dictionary.AssociationDefinition;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.GUID;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.ClassificationModel;
import ru.citeck.ecos.model.EcosTypeModel;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.utils.RepoUtils;

import javax.xml.bind.DatatypeConverter;
import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static ru.citeck.ecos.records.source.alf.file.FileRepresentation.*;

/**
 * @author Roman Makarskiy
 */
@Log4j
@Component
public class AlfNodeContentFileHelper {

    private static final String WORKSPACE_PREFIX = "workspace://SpacesStore/";

    private static NodeRef attachmentRoot = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, "attachments-root");

    private final NodeService nodeService;
    private final ContentService contentService;
    private final MimetypeService mimetypeService;
    private final DictionaryService dictionaryService;

    @Autowired
    public AlfNodeContentFileHelper(NodeService nodeService, ContentService contentService,
                                    MimetypeService mimetypeService, DictionaryService dictionaryService) {
        this.nodeService = nodeService;
        this.contentService = contentService;
        this.mimetypeService = mimetypeService;
        this.dictionaryService = dictionaryService;
    }

    public void processPropFileContent(NodeRef nodeRef, QName prop, DataValue jsonNode) {
        ContentWriter writer = contentService.getWriter(nodeRef, prop, true);

        if (jsonNode.isObject() && jsonNode.get("/data/nodeRef").isTextual()) {
            DataValue asList = Json.getMapper().convert(Collections.singletonList(jsonNode), DataValue.class);
            if (asList == null) {
                log.error("Json node is null after conversion. Node: " + jsonNode);
                return;
            }
            jsonNode = asList;
        }

        if (jsonNode.isTextual()) {
            writer.putContent(jsonNode.asText());
        } else if (jsonNode.isObject()) {
            DataValue mimetypeProp = jsonNode.get(MODEL_MIME_TYPE);
            String mimetype = mimetypeProp.isTextual() ? mimetypeProp.asText() : MimetypeMap.MIMETYPE_BINARY;
            if (MimetypeMap.MIMETYPE_BINARY.equals(mimetype)) {
                DataValue filename = jsonNode.get(MODEL_FILE_NAME);
                if (filename.isTextual()) {
                    mimetype = mimetypeService.guessMimetype(filename.asText());
                }
            }
            writer.setMimetype(mimetype);

            DataValue encoding = jsonNode.get(MODEL_ENCODING);
            if (encoding.isTextual()) {
                writer.setEncoding(encoding.asText());
            } else {
                writer.setEncoding(StandardCharsets.UTF_8.name());
            }

            DataValue base64Content = jsonNode.get(MODEL_BASE64_CONTENT);
            if (base64Content.isTextual()) {
                byte[] content = DatatypeConverter.parseBase64Binary(base64Content.asText());
                writer.putContent(new ByteArrayInputStream(content));
                return;
            }

            DataValue content = jsonNode.get(MODEL_CONTENT);
            if (content.isTextual()) {
                writer.putContent(content.asText());
            }
        } else if (isFileFromEformFormat(jsonNode)) {
            if (jsonNode.size() == 0) {
                nodeService.removeProperty(nodeRef, prop);
            } else {
                if (jsonNode.size() > 1) {
                    log.warn(String.format("Only one file can be written to the content property <%s>. " +
                            "Current files count: <%s>. The first file will be written.", prop, jsonNode.size()));
                }

                String tempRefStr = jsonNode.get(0).get(MODEL_DATA).get(MODEL_NODE_REF).asText();
                if (StringUtils.isBlank(tempRefStr) || !NodeRef.isNodeRef(tempRefStr)) {
                    throw new AlfrescoRuntimeException("NodeRef of content file incorrect");
                }

                saveFileToContentPropFromEform(new NodeRef(tempRefStr), prop, nodeRef);
            }
        }
    }

    public boolean isFileFromEformFormat(DataValue jsonNode) {
        if (!jsonNode.isArray()) {
            return false;
        }

        for (DataValue node : jsonNode) {
            DataValue data = node.get(MODEL_DATA);
            if (data.isNull()) {
                return false;
            }

            DataValue nodeRefObj = data.get(MODEL_NODE_REF);
            if (nodeRefObj.isNull()) {
                return false;
            }

            String nodeRef = nodeRefObj.asText();
            if (!NodeRef.isNodeRef(nodeRef)) {
                return false;
            }
        }
        return true;
    }

    public void processChildAssocFilesContent(QName assoc, DataValue jsonNodes, NodeRef nodeRef) {
        processAssocFilesContent(assoc, jsonNodes, nodeRef, true);
    }

    public void processAssocFilesContent(QName assoc, DataValue jsonNodes, NodeRef baseNodeRef, Boolean isChild) {

        AssociationDefinition assocDef = dictionaryService.getAssociation(assoc);
        QName assocType = assocDef.getName();
        QName assocTargetType = assocDef.getTargetClass().getName();

        List<NodeRef> currentFiles = isChild
                ? RepoUtils.getChildrenByAssoc(baseNodeRef, assocType, nodeService)
                : RepoUtils.getTargetAssoc(baseNodeRef, assocType, nodeService);

        Set<NodeRef> inboundMutatedRefs = new HashSet<>();
        List<AttachmentDto> attachments = parseAttachments(jsonNodes);

        for (AttachmentDto attachment : attachments) {

            NodeRef fileRef = attachment.documentRef;
            inboundMutatedRefs.add(fileRef);

            if (currentFiles.contains(fileRef)) {
                if (isChild) {
                    NodeRef primaryParentRef = nodeService.getPrimaryParent(fileRef).getParentRef();
                    if (primaryParentRef.equals(baseNodeRef)) {
                        processTypeKind(attachment, fileRef);
                    }
                }
                continue;
            }

            NodeRef createdNode = null;

            QName childAssocQName = QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, GUID.generate());
            Map<QName, Serializable> newNodeProps = new HashMap<>();
            newNodeProps.put(ContentModel.PROP_NAME, attachment.getDocumentName());

            if (!isChild) {

                NodeRef targetRef;

                if (attachment.isTempFile()) {

                    createdNode = nodeService.createNode(attachmentRoot,
                        ContentModel.ASSOC_CHILDREN,
                        childAssocQName,
                        assocTargetType,
                        newNodeProps).getChildRef();

                    targetRef = createdNode;

                } else {

                    targetRef = attachment.documentRef;
                }
                RepoUtils.createAssociation(baseNodeRef, targetRef, assocType, true, nodeService);

            } else {

                if (attachment.isTempFile()) {

                    createdNode = nodeService.createNode(baseNodeRef,
                        assocType,
                        childAssocQName,
                        assocTargetType,
                        newNodeProps
                    ).getChildRef();

                } else {

                    QName attachmentType = nodeService.getType(attachment.documentRef);
                    if (!dictionaryService.isSubClass(attachmentType, assocTargetType)) {
                        nodeService.setType(attachment.documentRef, assocTargetType);
                    }
                    nodeService.moveNode(attachment.documentRef, baseNodeRef, assocType, childAssocQName);
                }
            }

            if (log.isDebugEnabled() && createdNode != null) {
                log.debug(String.format("Create file node. Source node <%s>, type <%s>, assocType <%s>, nodeRef <%s>",
                        baseNodeRef.toString(), assocTargetType, assocType, createdNode.toString()));
            }

            if (createdNode != null) {
                saveFileToContentPropFromEform(attachment.documentRef, ContentModel.PROP_CONTENT, createdNode);
                processTypeKind(attachment, createdNode);
            }
        }

        if (isChild) {
            currentFiles.stream()
                .filter(ref -> {
                    if (!inboundMutatedRefs.contains(ref)) {

                        NodeRef primaryParentRef = nodeService.getPrimaryParent(ref).getParentRef();
                        if (primaryParentRef != baseNodeRef) {
                            return true;
                        }

                        QName type = nodeService.getType(ref);
                        return type.equals(assocTargetType);
                    }
                    return false;
                })
                .forEach(ref -> nodeService.removeChild(baseNodeRef, ref));
        } else {
            currentFiles.stream()
                    .filter(ref -> !inboundMutatedRefs.contains(ref))
                    .forEach(ref -> RepoUtils.removeAssociation(baseNodeRef, ref, assocType, true, nodeService));
        }
    }

    private void saveFileToContentPropFromEform(NodeRef contentFile, QName propName, NodeRef node) {

        ContentReader reader = contentService.getReader(contentFile, ContentModel.PROP_CONTENT);
        ContentWriter writer = contentService.getWriter(node, propName, true);
        writer.setEncoding(reader.getEncoding());
        writer.setMimetype(reader.getMimetype());
        writer.putContent(reader);

        Serializable fileName = nodeService.getProperty(contentFile, ContentModel.PROP_NAME);
        String currentName = (String) nodeService.getProperty(node, ContentModel.PROP_NAME);
        if (StringUtils.isBlank(currentName)) {
            nodeService.setProperty(node, ContentModel.PROP_NAME, fileName);
        }

        if (log.isDebugEnabled()) {
            log.debug(String.format("Copy content from <%s> to <%s>, fileName: <%s>", contentFile.toString(),
                    node.toString(), fileName));
        }

        QName contentFileType = nodeService.getType(contentFile);
        if (contentFileType.getLocalName().equals("tempFile")) {
            nodeService.deleteNode(contentFile);
        }
    }

    private void processTypeKind(AttachmentDto attachmentDto, NodeRef nodeRef) {

        Map<QName, Serializable> props = new HashMap<>();

        if (attachmentDto.getTypeRef() != null) {
            props.put(ClassificationModel.PROP_DOCUMENT_TYPE, attachmentDto.getTypeRef());
            props.put(ClassificationModel.PROP_DOCUMENT_KIND, attachmentDto.getKindRef());
        }

        String ecosType = attachmentDto.getEcosType();
        if (StringUtils.isNotBlank(ecosType)) {
            if (ecosType.startsWith("emodel/type@")) {
                ecosType = RecordRef.valueOf(ecosType).getId();
            }
            props.put(EcosTypeModel.PROP_TYPE, ecosType);
        }

        if (!props.isEmpty()) {
            nodeService.addProperties(nodeRef, props);
        }
    }

    private List<AttachmentDto> parseAttachments(DataValue jsonNode) {

        List<AttachmentDto> result = new ArrayList<>();

        if (jsonNode.isArray()) {
            for (DataValue node : jsonNode) {
                result.addAll(parseAttachments(node));
            }
        } else {

            String documentStr = jsonNode.get(MODEL_DATA).get(MODEL_NODE_REF).asText();

            if (StringUtils.isBlank(documentStr) || !documentStr.startsWith("workspace://")) {
                return Collections.emptyList();
            }
            NodeRef documentRef = new NodeRef(documentStr);
            if (!nodeService.exists(documentRef)) {
                throw new IllegalArgumentException("Document doesn't exists: " + documentRef + " json: " + jsonNode);
            }
            String documentName = jsonNode.get(MODEL_FILE_NAME).asText();

            if (StringUtils.isBlank(documentName)) {
                documentName = (String) nodeService.getProperty(documentRef, ContentModel.PROP_NAME);
            }

            String ecosType = jsonNode.get(MODEL_FILE_TYPE).asText();

            NodeRef typeRef = null;
            NodeRef kindRef = null;

            if (StringUtils.isNotBlank(ecosType)) {
                typeRef = uuidToNodeRef(StringUtils.substringBefore(ecosType, FILE_TYPE_DELIMITER));
                kindRef = uuidToNodeRef(StringUtils.substringAfter(ecosType, FILE_TYPE_DELIMITER));
            }

            QName type = nodeService.getType(documentRef);
            boolean isTempFile = type.getLocalName().equals("tempFile");
            result.add(new AttachmentDto(ecosType, typeRef, kindRef, documentRef, documentName, isTempFile));
        }

        return result;
    }

    private NodeRef uuidToNodeRef(String uuid) {

        if (StringUtils.isBlank(uuid)) {
            return null;
        }
        NodeRef ref = new NodeRef(WORKSPACE_PREFIX + uuid);
        return nodeService.exists(ref) ? ref : null;
    }

    @Data
    @AllArgsConstructor
    private static class AttachmentDto {
        private String ecosType;
        private NodeRef typeRef;
        private NodeRef kindRef;
        private NodeRef documentRef;
        private String documentName;
        private boolean isTempFile;
    }
}
