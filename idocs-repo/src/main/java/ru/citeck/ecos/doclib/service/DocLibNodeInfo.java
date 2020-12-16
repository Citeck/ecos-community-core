package ru.citeck.ecos.doclib.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Date;

@Data
@AllArgsConstructor
public class DocLibNodeInfo {
    private RecordRef recordRef;
    private DocLibNodeType nodeType;
    private String displayName;
    private RecordRef typeRef;
    private RecordRef docLibTypeRef;
    private Date modified;
    private Date created;
    private String modifier;
    private String creator;
}
