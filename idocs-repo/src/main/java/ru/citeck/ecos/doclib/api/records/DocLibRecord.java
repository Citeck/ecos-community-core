package ru.citeck.ecos.doclib.api.records;

import org.jetbrains.annotations.NotNull;
import ru.citeck.ecos.doclib.service.DocLibChildrenQuery;
import ru.citeck.ecos.doclib.service.DocLibNodeInfo;
import ru.citeck.ecos.doclib.service.DocLibNodeType;
import ru.citeck.ecos.doclib.service.DocLibService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.record.op.atts.service.schema.annotation.AttName;

import java.util.List;
import java.util.stream.Collectors;

public class DocLibRecord {

    private final RecordRef recordRef;
    private final RecordRef typeRef;

    private DocLibNodeInfo docLibNodeInfo;

    private final DocLibService docLibService;
    private final DocLibRecords docLibRecords;

    public DocLibRecord(@NotNull DocLibNodeInfo info, @NotNull DocLibRecords docLibRecords) {
        this(info.getRecordRef(), info.getTypeRef(), docLibRecords);
        this.docLibNodeInfo = info;
    }

    public DocLibRecord(@NotNull RecordRef recordRef,
                        @NotNull RecordRef typeRef,
                        @NotNull DocLibRecords docLibRecords) {

        this.typeRef = typeRef;
        this.recordRef = recordRef;

        this.docLibRecords = docLibRecords;
        this.docLibService = docLibRecords.getDocLibService();
    }

    @AttName("?id")
    public RecordRef getId() {
        return recordRef;
    }

    public DocLibNodeType getNodeType() {
        return getDocLibNodeInfo().getNodeType();
    }

    public List<DocLibRecord> getChildren() {

        DocLibChildrenQuery query = new DocLibChildrenQuery();
        query.setParentRef(recordRef);
        query.setRecursive(false);
        query.setNodeType(null);
        query.setTypeRef(typeRef);

        return docLibService.getChildren(query, null).getRecords()
            .stream()
            .map(rec -> new DocLibRecord(rec, typeRef, docLibRecords))
            .collect(Collectors.toList());
    }

    @AttName("?disp")
    public String getDisplayName() {
        return getDocLibNodeInfo().getDisplayName();
    }

    private DocLibNodeInfo getDocLibNodeInfo() {
        if (docLibNodeInfo == null) {
            docLibNodeInfo = docLibService.getDocLibNodeInfo(recordRef, typeRef);
        }
        return docLibNodeInfo;
    }
}
