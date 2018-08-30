package ru.citeck.ecos.records.query;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BaseRecordsResult<T> {

    private RecordsQuery query;
    private List<T> records;
    private boolean hasMore;
    private long totalCount = 0;

    public BaseRecordsResult() {
    }

    public BaseRecordsResult(BaseRecordsResult<T> other) {
        query = other.query;
        records = new ArrayList<>(other.getRecords());
        hasMore = other.hasMore;
        totalCount = other.totalCount;
    }

    public <K> BaseRecordsResult(BaseRecordsResult<K> other, Function<K, T> converter) {
        query = other.query;
        records = other.records.stream().map(converter).collect(Collectors.toList());
        hasMore = other.hasMore;
        totalCount = other.totalCount;
    }

    public RecordsQuery getQuery() {
        return query;
    }

    public void setQuery(RecordsQuery query) {
        this.query = query;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public boolean hasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(long totalCount) {
        this.totalCount = totalCount;
    }
}
