package ru.citeck.ecos.comment;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.comment.model.CommentDto;
import ru.citeck.ecos.comment.model.CommentTagDto;
import ru.citeck.ecos.commons.data.MLText;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.config.EcosConfigService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Roman Makarskiy
 */
@Service
public class EcosCommentTagService {

    private static final Map<CommentTag, String> ENABLED_CONFIG_KEY_BY_TAG = Collections.unmodifiableMap(
        new HashMap<CommentTag, String>() {{
            put(CommentTag.TASK, "add-comment-with-task-tag-enabled");
            put(CommentTag.ACTION, "add-comment-with-action-tag-enabled");
        }});

    private final RecordsService recordsService;
    private final EcosConfigService ecosConfigService;

    @Autowired
    public EcosCommentTagService(RecordsService recordsService, EcosConfigService ecosConfigService) {
        this.recordsService = recordsService;
        this.ecosConfigService = ecosConfigService;
    }

    public void addCommentWithTag(@NotNull RecordRef document,
                                  @NotNull String comment,
                                  @NotNull CommentTag tag,
                                  @NotNull MLText tagName) {
        if (addCommentIsDisabled(tag)) {
            return;
        }

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

    private boolean addCommentIsDisabled(CommentTag tag) {
        return !Boolean.parseBoolean((String) ecosConfigService.getParamValue(ENABLED_CONFIG_KEY_BY_TAG.get(tag)));
    }

}
