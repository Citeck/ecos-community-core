package ru.citeck.ecos.records;

import ru.citeck.ecos.records3.record.resolver.LocalRecordsResolver;

public interface RecordsResolverWrapper extends LocalRecordsResolver {
    void setRecordsResolver(LocalRecordsResolver recordsResolver);
}
