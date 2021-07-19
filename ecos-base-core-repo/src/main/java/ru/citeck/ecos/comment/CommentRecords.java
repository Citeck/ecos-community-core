package ru.citeck.ecos.comment;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;
import org.alfresco.query.PagingRequest;
import org.alfresco.query.PagingResults;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.comment.model.CommentDto;
import ru.citeck.ecos.records2.RecordMeta;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.graphql.meta.value.MetaField;
import ru.citeck.ecos.records2.request.delete.RecordsDelResult;
import ru.citeck.ecos.records2.request.delete.RecordsDeletion;
import ru.citeck.ecos.records2.request.mutation.RecordsMutResult;
import ru.citeck.ecos.records2.request.query.RecordsQuery;
import ru.citeck.ecos.records2.request.query.RecordsQueryResult;
import ru.citeck.ecos.records2.source.dao.local.v2.LocalRecordsCrudDao;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Roman Makarskiy
 */
@Log4j
@Component
public class CommentRecords extends LocalRecordsCrudDao<CommentDto> {

    public static final String ID = "comment";

    private final EcosCommentServiceImpl ecosCommentServiceImpl;
    private final CommentFactory commentFactory;

    @Autowired
    public CommentRecords(EcosCommentServiceImpl ecosCommentServiceImpl, CommentFactory commentFactory) {
        setId(ID);
        this.ecosCommentServiceImpl = ecosCommentServiceImpl;
        this.commentFactory = commentFactory;
    }

    @Override
    public List<CommentDto> getValuesToMutate(List<RecordRef> list) {
        return getValues(list);
    }

    @Override
    public List<CommentDto> getLocalRecordsMeta(List<RecordRef> list, MetaField metaField) {
        return getValues(list);
    }

    private List<CommentDto> getValues(List<RecordRef> list) {
        List<CommentDto> result = new ArrayList<>();

        for (RecordRef recordRef : list) {
            String id = recordRef.getId();
            if (StringUtils.isBlank(id)) {
                result.add(new CommentDto());
                continue;
            }

            CommentDto found = ecosCommentServiceImpl.getById(id);
            result.add(found);
        }

        return result;
    }

    @Override
    public RecordsMutResult save(List<CommentDto> list) {
        RecordsMutResult recordsMutResult = new RecordsMutResult();

        list.forEach(commentDto -> {
            CommentDto saved;

            String id = commentDto.getId();
            if (StringUtils.isBlank(id)) {
                saved = ecosCommentServiceImpl.create(commentDto);
            } else {
                saved = ecosCommentServiceImpl.update(commentDto);
            }

            RecordMeta recordMeta = new RecordMeta(saved.getId());
            recordsMutResult.addRecord(recordMeta);
        });

        return recordsMutResult;
    }

    @Override
    public RecordsDelResult delete(RecordsDeletion recordsDeletion) {
        List<RecordMeta> resultRecords = new ArrayList<>();
        recordsDeletion.getRecords()
                .forEach(commentRef -> {
                    ecosCommentServiceImpl.delete(commentRef.getId());
                    resultRecords.add(new RecordMeta(commentRef));
                });

        RecordsDelResult result = new RecordsDelResult();
        result.setRecords(resultRecords);
        return result;
    }

    @Override
    public RecordsQueryResult<CommentDto> queryLocalRecords(RecordsQuery recordsQuery, MetaField metaField) {
        CommentQuery query = recordsQuery.getQuery(CommentQuery.class);
        if (query.record == null || StringUtils.isBlank(query.record.getId())) {
            throw new IllegalArgumentException("You mus specify a record to find comments");
        }

        String id = query.record.getId();
        if (!NodeRef.isNodeRef(id)) {
            return new RecordsQueryResult<>();
        }

        NodeRef recordRef = new NodeRef(id);
        PagingRequest pagingRequest = getPagingRequest(recordsQuery);
        PagingResults<NodeRef> pagingResults = ecosCommentServiceImpl.listComments(recordRef, pagingRequest);

        List<CommentDto> comments = pagingResults.getPage()
                .stream()
                .map(commentFactory::fromNode)
                .collect(Collectors.toList());

        RecordsQueryResult<CommentDto> result = new RecordsQueryResult<>();
        result.setRecords(comments);
        result.setTotalCount(pagingResults.getTotalResultCount().getFirst());
        result.setHasMore(pagingResults.hasMoreItems());
        return result;
    }

    private PagingRequest getPagingRequest(RecordsQuery recordsQuery) {
        int maxItems = recordsQuery.getMaxItems();
        if (maxItems == -1) {
            maxItems = Integer.MAX_VALUE;
        }
        int skipCount = recordsQuery.getSkipCount();

        PagingRequest pagingRequest = new PagingRequest(skipCount, maxItems);
        pagingRequest.setRequestTotalCountMax(Integer.MAX_VALUE);

        return pagingRequest;
    }

    private static class CommentQuery {

        @Getter
        @Setter
        private RecordRef record;
    }
}
