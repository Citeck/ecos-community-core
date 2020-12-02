package ru.citeck.ecos.doclib.api.records;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.doclib.service.DocLibChildrenQuery;
import ru.citeck.ecos.doclib.service.DocLibService;
import ru.citeck.ecos.node.DisplayNameService;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.record.dao.AbstractRecordsDao;
import ru.citeck.ecos.records3.record.op.atts.dao.RecordAttsDao;
import ru.citeck.ecos.records3.record.op.atts.dto.LocalRecordAtts;
import ru.citeck.ecos.records3.record.op.mutate.dao.RecordMutateDao;
import ru.citeck.ecos.records3.record.op.query.dao.RecordsQueryDao;
import ru.citeck.ecos.records3.record.op.query.dto.RecsQueryRes;
import ru.citeck.ecos.records3.record.op.query.dto.query.QueryPage;
import ru.citeck.ecos.records3.record.op.query.dto.query.RecordsQuery;

import java.util.stream.Collectors;

@Component
public class DocLibRecords extends AbstractRecordsDao
        implements RecordsQueryDao, RecordMutateDao, RecordAttsDao {

    public static final String SOURCE_ID = "doclib";

    private static final String LANG_CHILDREN = "children";

    @Getter
    @NotNull
    private final DocLibService docLibService;
    @Getter
    private final DisplayNameService displayNameService;

    @Autowired
    public DocLibRecords(@NotNull DocLibService docLibService,
                         DisplayNameService displayNameService) {

        this.docLibService = docLibService;
        this.displayNameService = displayNameService;
    }

    @Nullable
    @Override
    public Object getRecordAtts(@NotNull String recordId) {
        return new DocLibRecord(RecordRef.create(SOURCE_ID, recordId), this);
    }

    @NotNull
    @Override
    public String mutate(@NotNull LocalRecordAtts localRecordAtts) {
        String id = localRecordAtts.getId();
        if (id.lastIndexOf(DocLibService.TYPE_DELIM) == id.length() - 1) {
            return docLibService.createEntity(localRecordAtts.getAttributes()).getId();
        }
        throw new IllegalArgumentException("Source with id '" + SOURCE_ID
            + "' can't mutate record with non-empty id: '" + id + "'");
    }

    @Nullable
    @Override
    public RecsQueryRes<?> queryRecords(@NotNull RecordsQuery recordsQuery) {

        if (LANG_CHILDREN.equals(recordsQuery.getLanguage())) {
            DocLibChildrenQuery childrenQuery = recordsQuery.getQuery(DocLibChildrenQuery.class);
            return getChildren(childrenQuery, recordsQuery.getPage());
        }
        return null;
    }

    public RecsQueryRes<DocLibRecord> getChildren(DocLibChildrenQuery query, QueryPage page) {

        RecsQueryRes<RecordRef> childrenRes = docLibService.getChildren(query, page);
        if (childrenRes.getRecords().isEmpty() && childrenRes.getTotalCount() == 0) {
            return new RecsQueryRes<>();
        }

        RecsQueryRes<DocLibRecord> result = new RecsQueryRes<>();
        result.setRecords(childrenRes.getRecords()
            .stream()
            .map(rec -> new DocLibRecord(rec, this))
            .collect(Collectors.toList())
        );
        result.setHasMore(childrenRes.getHasMore());
        result.setTotalCount(childrenRes.getTotalCount());

        return result;
    }

    @NotNull
    @Override
    public String getId() {
        return SOURCE_ID;
    }
}
