package ru.citeck.ecos.records;

import org.alfresco.repo.template.BaseTemplateProcessorExtension;
import ru.citeck.ecos.records3.record.op.query.dto.RecsQueryRes;

public class RecordsServiceTemplate extends BaseTemplateProcessorExtension {

    private RecordsServiceJS recordsService;

    public RecsQueryRes<?> getRecordsForClass(Object recordsQuery, String schemaClass) {
        return recordsService.getRecords(recordsQuery, getClass(schemaClass));
    }

    private Class<?> getClass(String className) {
        Class<?> clazz;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class not found: " + className, e);
        }
        return clazz;
    }

    public void setRecordsService(RecordsServiceJS recordsService) {
        this.recordsService = recordsService;
    }
}
