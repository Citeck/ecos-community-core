package ru.citeck.ecos.records.source.alf.meta;

import lombok.Data;
import ru.citeck.ecos.records2.graphql.meta.annotation.MetaAtt;

@Data
public class StatusMetaDTO {
    @MetaAtt("icase:caseStatusAssoc.cm:name")
    private String id;
    @MetaAtt("icase:caseStatusAssoc-prop")
    private String ecosId;
    @MetaAtt("icase:caseStatusAssoc?disp")
    private String name;
}
