package ru.citeck.ecos.comment;

import org.alfresco.query.PagingRequest;
import org.alfresco.query.PagingResults;
import org.alfresco.service.cmr.repository.NodeRef;
import ru.citeck.ecos.comment.model.CommentDto;

/**
 * @author Roman Makarskiy
 */
public interface EcosCommentService {

    CommentDto create(CommentDto commentDTO);

    CommentDto update(CommentDto commentDTO);

    CommentDto getById(String id);

    void delete(String id);

    PagingResults<NodeRef> listComments(NodeRef discussableNode, PagingRequest pagingRequest);

}
