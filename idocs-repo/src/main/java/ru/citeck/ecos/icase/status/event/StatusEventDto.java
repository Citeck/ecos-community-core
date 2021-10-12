package ru.citeck.ecos.icase.status.event;

import lombok.Builder;
import lombok.Data;
import ru.citeck.ecos.records2.RecordRef;

/**
 * @author Roman Makarskiy
 */
@Data
@Builder
public class StatusEventDto {

    private RecordRef rec;

    private RecordRef statusBefore;

    private RecordRef statusAfter;

}
