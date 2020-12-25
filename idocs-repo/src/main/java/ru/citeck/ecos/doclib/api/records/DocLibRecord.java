package ru.citeck.ecos.doclib.api.records;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.NodeRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import ru.citeck.ecos.doclib.service.DocLibChildrenQuery;
import ru.citeck.ecos.doclib.service.DocLibNodeInfo;
import ru.citeck.ecos.doclib.service.DocLibNodeType;
import ru.citeck.ecos.doclib.service.DocLibService;
import ru.citeck.ecos.records.source.PeopleRecordsDao;
import ru.citeck.ecos.records.source.alf.file.FileRepresentation;
import ru.citeck.ecos.records2.RecordConstants;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.record.op.atts.service.schema.annotation.AttName;
import ru.citeck.ecos.records3.record.op.atts.service.value.AttValue;

import java.util.Date;
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

    public List<DocLibRecord> getPath() {
        return docLibService.getPath(recordRef)
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

    @AttName(RecordConstants.ATT_CONTENT)
    public Object getContent() {

        DocLibNodeInfo nodeInfo = getDocLibNodeInfo();

        String recordRefId = nodeInfo.getRecordRef().getId();
        if (!recordRefId.contains("$") || recordRefId.charAt(recordRefId.length() - 1) == '$') {
            return null;
        }
        recordRefId = recordRefId.substring(recordRefId.indexOf('$') + 1);
        if (!NodeRef.isNodeRef(recordRefId)) {
            return null;
        }
        ContentData contentData = nodeInfo.getContent().get();
        if (contentData == null) {
            return null;
        }
        return new RecordContentData(new NodeRef(recordRefId), contentData);
    }

    @AttName(RecordConstants.ATT_MODIFIED)
    public Date getModified() {
        return getDocLibNodeInfo().getModified();
    }

    @AttName(RecordConstants.ATT_CREATED)
    public Date getCreated() {
        return getDocLibNodeInfo().getCreated();
    }

    @AttName(RecordConstants.ATT_MODIFIER)
    public RecordRef getModifier() {
        return RecordRef.create(PeopleRecordsDao.ID, getDocLibNodeInfo().getModifier());
    }

    @AttName(RecordConstants.ATT_CREATOR)
    public RecordRef getCreator() {
        return RecordRef.create(PeopleRecordsDao.ID, getDocLibNodeInfo().getCreator());
    }

    private DocLibNodeInfo getDocLibNodeInfo() {
        if (docLibNodeInfo == null) {
            docLibNodeInfo = docLibService.getDocLibNodeInfo(recordRef);
        }
        return docLibNodeInfo;
    }

    @AttName("?json")
    public DocLibNodeInfo getJson() {
        return getDocLibNodeInfo();
    }

    @Data
    @RequiredArgsConstructor
    public class RecordContentData implements AttValue {

        private final NodeRef nodeRef;
        private final ContentData data;

        @Nullable
        @Override
        public Object getAs(String type) {

            if ("content-data".equals(type)) {
                JSONArray array = FileRepresentation.formContentData(data, docLibRecords.getNodeService(), nodeRef);
                return array.toString();
            }
            return null;
        }
    }
}
