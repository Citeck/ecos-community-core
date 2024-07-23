package ru.citeck.ecos.doclib.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.alfresco.service.cmr.repository.ContentData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.webapp.api.entity.EntityRef;

import java.util.Date;
import java.util.function.Supplier;

@Data
@AllArgsConstructor
public class DocLibNodeInfo {

    private EntityRef recordRef;
    private DocLibNodeType nodeType;
    private String displayName;
    private EntityRef typeRef;
    private EntityRef docLibTypeRef;
    private Date modified;
    private Date created;
    private String modifier;
    private String creator;

    @NotNull
    private Supplier<ContentData> content;
    @Nullable
    private ObjectData previewInfo;
}
