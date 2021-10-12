package ru.citeck.ecos.comment.event;

import lombok.Builder;
import lombok.Data;
import ru.citeck.ecos.records2.RecordRef;

@Data
@Builder
public class EcosCommentEventDto {

    private RecordRef rec;

    private RecordRef commentRec;

    private String textBefore;

    private String textAfter;

}
