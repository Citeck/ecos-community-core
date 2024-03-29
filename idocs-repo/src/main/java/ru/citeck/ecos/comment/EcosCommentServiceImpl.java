package ru.citeck.ecos.comment;

import ecos.com.fasterxml.jackson210.core.JsonProcessingException;
import ecos.com.fasterxml.jackson210.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.query.PagingRequest;
import org.alfresco.query.PagingResults;
import org.alfresco.repo.forum.CommentServiceImpl;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.PermissionService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.comment.event.EcosCommentEventDto;
import ru.citeck.ecos.comment.event.EcosCommentEventService;
import ru.citeck.ecos.comment.model.CommentDto;
import ru.citeck.ecos.comment.model.CommentTagDto;
import ru.citeck.ecos.model.EcosCommonModel;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.List;

/**
 * Facade of alfresco {@link CommentServiceImpl} for working with {@link CommentDto} in {@link CommentRecords}
 *
 * @author Roman Makarskiy
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor_ = {@Autowired})
public class EcosCommentServiceImpl implements EcosCommentService {

    private static final String WORKSPACE_PREFIX = "workspace://SpacesStore/";
    private static final boolean OFF_SUPPRESS_ROLL_UPS = false;

    private final CommentServiceImpl commentService;
    private final CommentFactory commentFactory;
    private final NodeService nodeService;
    private final PermissionService permissionService;
    private final EcosCommentEventService ecosCommentEventService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public CommentDto create(CommentDto commentDTO) {
        String record = commentDTO.getRecord();

        if (StringUtils.isBlank(record)) {
            throw new IllegalArgumentException("Record id is mandatory parameter for creating comment");
        }

        NodeRef discussableRef = toNodeRef(record);

        if (!hasReadPermission(discussableRef)) {
            throw new IllegalStateException("Read permission to " + discussableRef + " required for creating comment");
        }
        NodeRef createdComment = AuthenticationUtil.runAsSystem(() ->
            commentService.createComment(discussableRef, "", commentDTO.getText(), OFF_SUPPRESS_ROLL_UPS)
        );
        fillTags(createdComment, commentDTO.getTags());

        CommentDto created = commentFactory.fromNode(createdComment);

        ecosCommentEventService.sendCreateEvent(EcosCommentEventDto.builder()
            .rec(RecordRef.valueOf(created.getRecord()))
            .record(EntityRef.valueOf(created.getRecord()))
            .commentRec(EcosCommentUtils.commentIdToRecordRef(created.getId()))
            .textAfter(created.getText())
            .build()
        );

        return created;
    }

    private boolean hasReadPermission(NodeRef nodeRef) {
        AccessStatus status = permissionService.hasPermission(nodeRef, PermissionService.READ);
        return AccessStatus.ALLOWED.equals(status);
    }

    private void fillTags(NodeRef comment, List<CommentTagDto> tags) {
        if (CollectionUtils.isEmpty(tags)) {
            return;
        }

        try {
            String tagsJson = mapper.writeValueAsString(tags);
            nodeService.setProperty(comment, EcosCommonModel.PROP_TAG, tagsJson);
        } catch (JsonProcessingException e) {
            log.error("Failed write comment tags as string", e);
        }
    }

    @Override
    public CommentDto update(CommentDto commentDTO) {
        String commentId = commentDTO.getId();

        if (StringUtils.isBlank(commentId)) {
            throw new IllegalArgumentException("Comment id is mandatory parameter for updating comment");
        }

        CommentDto commentBefore = getById(commentId);

        NodeRef commentRef = toNodeRef(commentId);
        commentService.updateComment(commentRef, "", commentDTO.getText());

        CommentDto commentAfter = commentFactory.fromNode(commentRef);

        ecosCommentEventService.sendUpdateEvent(EcosCommentEventDto.builder()
            .rec(RecordRef.valueOf(commentAfter.getRecord()))
            .record(EntityRef.valueOf(commentAfter.getRecord()))
            .commentRec(EcosCommentUtils.commentIdToRecordRef(commentAfter.getId()))
            .textBefore(commentBefore.getText())
            .textAfter(commentAfter.getText())
            .build()
        );

        return commentAfter;
    }

    @Override
    public CommentDto getById(String id) {
        NodeRef commentRef = toNodeRef(id);
        if (!nodeService.exists(commentRef)) {
            throw new IllegalArgumentException(String.format("Comment with id <%s> not found", id));
        }

        return commentFactory.fromNode(commentRef);
    }

    @Override
    public void delete(String id) {
        NodeRef commentRef = toNodeRef(id);

        CommentDto comment = getById(id);

        commentService.deleteComment(commentRef);

        ecosCommentEventService.sendDeleteEvent(EcosCommentEventDto.builder()
            .rec(RecordRef.valueOf(comment.getRecord()))
            .record(EntityRef.valueOf(comment.getRecord()))
            .commentRec(EcosCommentUtils.commentIdToRecordRef(comment.getId()))
            .textBefore(comment.getText())
            .build()
        );
    }

    @Override
    public PagingResults<NodeRef> listComments(NodeRef discussableNode, PagingRequest pagingRequest) {
        return commentService.listComments(discussableNode, pagingRequest);
    }

    private NodeRef toNodeRef(String recordId) {
        String ref = recordId;
        if (ref != null) {
            int idx = ref.lastIndexOf('@');
            if (idx > -1 && idx < ref.length() - 1) {
                ref = ref.substring(idx + 1);
            }
        }
        if (!StringUtils.contains(ref, WORKSPACE_PREFIX)) {
            ref = WORKSPACE_PREFIX + ref;
        }
        return new NodeRef(ref);
    }
}
