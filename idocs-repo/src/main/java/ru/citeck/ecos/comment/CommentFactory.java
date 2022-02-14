package ru.citeck.ecos.comment;

import ecos.com.fasterxml.jackson210.core.type.TypeReference;
import ecos.com.fasterxml.jackson210.databind.JsonNode;
import ecos.com.fasterxml.jackson210.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.forum.CommentServiceImpl;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.lock.LockService;
import org.alfresco.service.cmr.lock.LockStatus;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.comment.model.CommentDto;
import ru.citeck.ecos.comment.model.CommentPermissions;
import ru.citeck.ecos.comment.model.CommentTagDto;
import ru.citeck.ecos.model.EcosCommonModel;
import ru.citeck.ecos.records.models.AuthorityDTO;
import ru.citeck.ecos.records.source.PeopleRecordsDao;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsService;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Roman Makarskiy
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class CommentFactory {

    private static final int EDITED_DIFF_RANGE = 100;
    private static final String SITE_MANAGER = "SiteManager";
    private static final String APP_ALFRESCO = "alfresco";

    private static final List<CommentTag> TAGS_DISABLED_EDITING = Arrays.asList(CommentTag.TASK, CommentTag.ACTION,
        CommentTag.INTEGRATION);

    private final LockService lockService;
    private final PermissionService permissionService;
    private final ContentService contentService;
    private final NodeService nodeService;
    private final RecordsService recordsService;
    private final AuthorityService authorityService;
    private final ServiceRegistry serviceRegistry;
    private final CommentServiceImpl alfCommentService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${ecos.comments.editing.disabled}")
    private String isCommentsEditingDisabled;


    public CommentDto fromNode(NodeRef commentRef) {
        CommentDto dto = new CommentDto();

        Map<QName, Serializable> properties = nodeService.getProperties(commentRef);

        Date createdAt = (Date) properties.get(ContentModel.PROP_CREATED);
        Date modifiedAt = (Date) properties.get(ContentModel.PROP_MODIFIED);

        dto.setCreatedAt(createdAt);
        dto.setModifiedAt(modifiedAt);
        dto.setRecord(getDiscussableRecordRef(commentRef).toString());
        dto.setEdited(isEdited(createdAt, modifiedAt));
        dto.setAuthor(toUserDTO((String) properties.get(ContentModel.PROP_CREATOR)));
        dto.setEditor(toUserDTO((String) properties.get(ContentModel.PROP_MODIFIER)));

        dto.setText(getCommentText(commentRef));
        dto.setId(commentRef.getId());


        String tagsJson = (String) properties.get(EcosCommonModel.PROP_TAG);
        List<CommentTagDto> tags = getTagsFromJsonString(tagsJson);
        dto.setTags(tags);

        dto.setPermissions(getPermissions(commentRef, tags));

        return dto;
    }

    private List<CommentTagDto> getTagsFromJsonString(String tagsJson) {
        if (StringUtils.isBlank(tagsJson)) {
            return Collections.emptyList();
        }

        try {
            return mapper.readValue(tagsJson, new TypeReference<List<CommentTagDto>>() {
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed read comment tags from string", e);
        }
    }

    private Boolean isEdited(Date createdAt, Date modifiedAt) {
        if (createdAt == null || modifiedAt == null) {
            return false;
        }

        long diff = modifiedAt.getTime() - createdAt.getTime();
        return diff >= EDITED_DIFF_RANGE;
    }

    private AuthorityDTO toUserDTO(String userName) {
        NodeRef userRef = authorityService.getAuthorityNodeRef(userName);
        RecordRef userRecord = RecordRef.create("", userRef.toString());
        return recordsService.getMeta(userRecord, AuthorityDTO.class);
    }

    private String getCommentText(NodeRef commentRef) {
        ContentReader reader = contentService.getReader(commentRef, ContentModel.PROP_CONTENT);
        return reader != null ? reader.getContentString() : null;
    }

    //todo: use DTO instead of JsonNode
    private JsonNode getPermissions(NodeRef commentRef, List<CommentTagDto> tags) {

        Map<String, Boolean> permissions = new HashMap<>();

        if (Boolean.parseBoolean(isCommentsEditingDisabled) || isEditingDisabledByTag(tags)) {
            permissions.put(CommentPermissions.CAN_EDIT.getValue(), false);
            permissions.put(CommentPermissions.CAN_DELETE.getValue(), false);
            return mapper.convertValue(permissions, JsonNode.class);
        }

        boolean canEdit;
        boolean canDelete;
        boolean isNodeLocked = false;

        Set<QName> aspects = nodeService.getAspects(commentRef);

        boolean isWorkingCopy = aspects.contains(ContentModel.ASPECT_WORKING_COPY);
        if (!isWorkingCopy && aspects.contains(ContentModel.ASPECT_LOCKABLE)) {
            LockStatus lockStatus = lockService.getLockStatus(commentRef);
            if (lockStatus == LockStatus.LOCKED || lockStatus == LockStatus.LOCK_OWNER) {
                isNodeLocked = true;
            }
        }

        if (isNodeLocked || isWorkingCopy) {
            canEdit = false;
            canDelete = false;
        } else {
            String author = (String) nodeService.getProperties(commentRef).get(ContentModel.PROP_CREATOR);
            String currentUser = serviceRegistry.getAuthenticationService().getCurrentUserName();
            boolean isAdmin = recordsService.getAtt(
                RecordRef.create(PeopleRecordsDao.ID, currentUser), PeopleRecordsDao.PROP_IS_ADMIN).asBoolean();
            canEdit = author.equals(currentUser) || isAdmin;
            canDelete = canEdit &&
                permissionService.hasPermission(commentRef, PermissionService.DELETE) == AccessStatus.ALLOWED;
        }

        permissions.put(CommentPermissions.CAN_EDIT.getValue(), canEdit);
        permissions.put(CommentPermissions.CAN_DELETE.getValue(), canDelete);

        return mapper.convertValue(permissions, JsonNode.class);
    }

    private boolean isEditingDisabledByTag(List<CommentTagDto> commentTagsDto) {
        if (CollectionUtils.isEmpty(commentTagsDto)) {
            return false;
        }

        List<CommentTag> commentTags = commentTagsDto.stream()
            .map(CommentTagDto::getType)
            .collect(Collectors.toList());

        return CollectionUtils.containsAny(commentTags, TAGS_DISABLED_EDITING);
    }

    private RecordRef getDiscussableRecordRef(NodeRef commentRef) {
        NodeRef discussableRef = alfCommentService.getDiscussableAncestor(commentRef);
        if (discussableRef == null) {
            log.warn("Failed to get discussable Ref from commentRef: " + commentRef);
            return RecordRef.EMPTY;
        }

        return RecordRef.create(APP_ALFRESCO, "", discussableRef.toString());
    }

}
