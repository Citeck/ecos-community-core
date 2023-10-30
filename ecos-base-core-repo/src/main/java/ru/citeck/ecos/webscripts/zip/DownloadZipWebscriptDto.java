package ru.citeck.ecos.webscripts.zip;

import lombok.Data;
import ru.citeck.ecos.records2.RecordRef;

import java.util.List;

@Data
public class DownloadZipWebscriptDto {
    private List<RecordRef> documentsRef;
}
