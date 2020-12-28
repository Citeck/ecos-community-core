package ru.citeck.ecos.records;

import org.alfresco.repo.template.BaseTemplateProcessorExtension;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.RecordsServiceImpl;
import ru.citeck.ecos.records3.record.op.query.dto.RecsQueryRes;
import ru.citeck.ecos.utils.JsUtils;

import java.util.Map;

public class RecordsServiceTemplate extends BaseTemplateProcessorExtension {

    private RecordsServiceJS recordsServiceJs;

    @Autowired
    private JsUtils jsUtils;

    @Autowired
    private RecordsServiceImpl recordsService;

    public RecsQueryRes<?> getRecordsForClass(Object recordsQuery, String schemaClass) {
        return recordsServiceJs.getRecords(recordsQuery, getClass(schemaClass));
    }

    public DataValue getAtt(Object record, String att) {
        RecordRef recordRef = jsUtils.getRecordRef(record);
        return recordsService.getAttribute(recordRef, att);
    }

    public ObjectData getRecordAtts(Object record, Object atts) {
        RecordRef recordRef = jsUtils.getRecordRef(record);
        @SuppressWarnings("unchecked")
        Map<String, String> attsMap = (Map<String, String>) jsUtils.toJava(atts);
        return recordsService.getAttributes(recordRef, attsMap).getAttributes().deepCopy();
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

    public void setRecordsServiceJs(RecordsServiceJS recordsServiceJs) {
        this.recordsServiceJs = recordsServiceJs;
    }
}
