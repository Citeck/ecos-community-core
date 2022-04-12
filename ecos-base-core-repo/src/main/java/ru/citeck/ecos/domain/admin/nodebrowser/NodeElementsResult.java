package ru.citeck.ecos.domain.admin.nodebrowser;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
public class NodeElementsResult<T> {
    private final List<T> elements;
    private final boolean hasMore;
}
