package ru.citeck.ecos.doclib.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.alfresco.service.cmr.repository.ContentData;
import org.jetbrains.annotations.NotNull;
import ru.citeck.ecos.records2.RecordRef;

import java.util.Date;
import java.util.function.Supplier;

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

    @NotNull
    private Supplier<ContentData> content;
}
