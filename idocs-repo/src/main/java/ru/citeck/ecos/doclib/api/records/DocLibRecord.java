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

    private DocLibNodeInfo docLibNodeInfo;

    private final DocLibService docLibService;
    private final DocLibRecords docLibRecords;

    public DocLibRecord(@NotNull DocLibNodeInfo info, @NotNull DocLibRecords docLibRecords) {
        this(info.getRecordRef(), docLibRecords);
        this.docLibNodeInfo = info;
    }

    public DocLibRecord(@NotNull RecordRef recordRef, @NotNull DocLibRecords docLibRecords) {

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

        return docLibService.getChildren(query, null).getRecords()
            .stream()
            .map(rec -> new DocLibRecord(rec, docLibRecords))
            .collect(Collectors.toList());
    }

    @AttName("_type")
    public RecordRef getTypeRef() {
        return getDocLibNodeInfo().getTypeRef();
    }

    public RecordRef getDocLibTypeRef() {
        return getDocLibNodeInfo().getDocLibTypeRef();
    }

    public Boolean getHasChildrenDirs() {
        return docLibService.hasChildrenDirs(recordRef);
    }

    @AttName("?disp")
    public String getDisplayName() {
        return getDocLibNodeInfo().getDisplayName();
    }

    private DocLibNodeInfo getDocLibNodeInfo() {
        if (docLibNodeInfo == null) {
            docLibNodeInfo = docLibService.getDocLibNodeInfo(recordRef);
        }
        return docLibNodeInfo;
    }
}
