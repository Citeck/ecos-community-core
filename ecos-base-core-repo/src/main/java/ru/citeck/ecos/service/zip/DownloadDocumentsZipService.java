package ru.citeck.ecos.service.zip;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.commons.compress.utils.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;

import java.io.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class DownloadDocumentsZipService {

    private final RecordsService recordsService;
    private final ContentService contentService;


    @Autowired
    DownloadDocumentsZipService(RecordsService recordsService, ContentService contentService) {
        this.recordsService = recordsService;
        this.contentService = contentService;
    }


    public void getZip(List<RecordRef> documentsRef, OutputStream outputStream) {
        List<DocumentData> documents = getDocuments(documentsRef);
        packageDocumentsToZip(documents, outputStream);
    }

    private List<DocumentData> getDocuments(List<RecordRef> documentsRef) {
        return documentsRef.stream()
            .filter(this::isNotEmptyRecordRef)
            .map(this::getFile)
            .collect(Collectors.toList());
    }

    private boolean isNotEmptyRecordRef(RecordRef recordRef) {
        if (RecordRef.isEmpty(recordRef)) {
            log.error("RecordRef is empty!");
            return false;
        }
        return true;
    }

    private DocumentData getFile(RecordRef recordRef) {
        DocumentData documentData = new DocumentData();
        documentData.setName(recordsService.getAtt(recordRef, "name").textValue());
        documentData.setContent(contentService.getReader(new NodeRef(recordRef.getId()), ContentModel.PROP_CONTENT));
        return documentData;
    }

    private void packageDocumentsToZip(List<DocumentData> documents, OutputStream outputStream) {
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream);
        try {
            zipDocumentsFiles(zipOutputStream, documents);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            IOUtils.closeQuietly(zipOutputStream);
        }
    }

    private void zipDocumentsFiles(ZipOutputStream zipOutputStream,
                                   List<DocumentData> documentsFiles) throws IOException {
        for (DocumentData documentsFile : documentsFiles) {
            ZipEntry zipEntry = new ZipEntry(documentsFile.getName());
            zipOutputStream.putNextEntry(zipEntry);

            ContentReader content = documentsFile.getContent();
            InputStream contentInputStream = content.getContentInputStream();
            IOUtils.copy(contentInputStream, zipOutputStream);

            zipOutputStream.closeEntry();
            IOUtils.closeQuietly(contentInputStream);
        }
    }


    @Data
    private static class DocumentData {
        private String name;
        private ContentReader content;
    }
}
