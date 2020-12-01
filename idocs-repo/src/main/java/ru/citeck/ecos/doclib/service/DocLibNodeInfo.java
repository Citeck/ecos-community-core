package ru.citeck.ecos.doclib.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.citeck.ecos.records2.RecordRef;

@Data
@AllArgsConstructor
public class DocLibNodeInfo {
    private RecordRef recordRef;
    private DocLibNodeType nodeType;
    private String displayName;
    private RecordRef typeRef;
}
