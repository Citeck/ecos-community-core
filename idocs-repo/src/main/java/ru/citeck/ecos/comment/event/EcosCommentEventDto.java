package ru.citeck.ecos.comment.event;

import lombok.Builder;
import lombok.Data;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

@Data
@Builder
public class EcosCommentEventDto {

    //For legacy reasons, we need to keep this field
    @Deprecated
    private RecordRef rec;

    private EntityRef record;

    private RecordRef commentRec;

    private String textBefore;

    private String textAfter;

}
