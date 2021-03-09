package ru.citeck.ecos.comment;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.comment.model.CommentDto;
import ru.citeck.ecos.comment.model.CommentTagDto;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;

import java.util.Collections;

/**
 * @author Roman Makarskiy
 */
@Service
public class EcosCommentTagService {

    private final RecordsService recordsService;

    @Autowired
    public EcosCommentTagService(RecordsService recordsService) {
        this.recordsService = recordsService;
    }

    public void addCommentWithTag(@NotNull RecordRef document,
                                  @NotNull String comment,
                                  @NotNull CommentTag tag,
                                  @NotNull MLText tagName) {
        if (StringUtils.isBlank(comment)) {
            throw new IllegalArgumentException("Comment cannot be blank");
        }

        if (RecordRef.isEmpty(document)) {
            throw new IllegalArgumentException("Document recordRef cannot be empty");
        }

        CommentDto commentDto = new CommentDto();
        commentDto.setRecord(document.toString());
        commentDto.setText(comment);

        CommentTagDto tagDto = new CommentTagDto();
        tagDto.setType(tag.getValue());
        tagDto.setName(tagName);

        commentDto.setTags(Collections.singletonList(tagDto));

        recordsService.mutate(RecordRef.create(CommentRecords.ID, ""), ObjectData.create(commentDto));
    }

}
