package ru.citeck.ecos.action.group;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.processor.report.ReportProducer;
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Factory of group action "Export to Csv-file"
 * Default data delimiter is <i>Tab</i>
 * To override delimiter default value define "config.params.delimiter" in action settings
 */
@Slf4j
@Component
public class ExportCsvActionFactory extends AbstractExportActionFactory<ExportCsvActionFactory.CsvEnvironment> {

    private static final String ACTION_ID = "download-report-csv-action";
    private static final String MIMETYPE = "text/csv";

    private static final String CONFIG_DELIMITER = "delimiter";
    public static final String DEFAULT_DELIMITER = "\t";
    private static final String DEFAULT_SEPARATOR = "\r\n";

    @Override
    public String getActionId() {
        return ACTION_ID;
    }

    @Override
    protected CsvEnvironment createEnvironment(GroupActionConfig config, List<String> requestedAttributes, List<String> columnTitles) {
        mimeType = MIMETYPE;
        String configDelimiter = config.getStrParam(CONFIG_DELIMITER);
        CsvEnvironment environment = new CsvEnvironment(
            StringUtils.isNotBlank(configDelimiter) ? configDelimiter : DEFAULT_DELIMITER,
            new StringBuilder());

        createColumnTitlesRow(columnTitles, environment);
        return environment;
    }

    @Override
    protected int writeData(List<RecordAtts> nodesAttributes, int nextRowIndex, List<String> requestedAttributes, CsvEnvironment csvEnvironment) {
        for (RecordAtts attributes : nodesAttributes) {
            csvEnvironment.getReportBuilder().append(requestedAttributes.stream()
                    .map(attributeName -> {
                        DataValue attributeValue = attributes.getAtt(attributeName);
                        String url = attributeValue.get(ReportProducer.DATA_TYPE_HYPERLINK) != null ?
                            attributeValue.get(ReportProducer.DATA_TYPE_HYPERLINK).asText() :
                            null;
                        return StringUtils.isNotBlank(url) ?
                            clean(attributeValue.asText() + " (" + url + ")") :
                            clean(attributeValue.asText());
                    })
                    .collect(Collectors.joining(csvEnvironment.getCellDelimiter())))
                .append(DEFAULT_SEPARATOR);
        }
        return nextRowIndex + nodesAttributes.size();
    }

    @Override
    protected void writeToStream(ByteArrayOutputStream outputStream, CsvEnvironment csvEnvironment) throws Exception {
        outputStream.write(csvEnvironment.getReportBuilder().toString().getBytes(encoding));
    }

    private void createColumnTitlesRow(List<String> columnTitles, CsvEnvironment csvEnvironment) {
        if (CollectionUtils.isEmpty(columnTitles)) {
            log.warn(EMPTY_REPORT_MSG);
            return;
        }
        csvEnvironment.getReportBuilder().append(columnTitles.stream()
                .map(title -> clean(title))
                .collect(Collectors.joining(csvEnvironment.getCellDelimiter())))
            .append(DEFAULT_SEPARATOR);
    }

    /**
     * Clear original data from newline symbols. Prevents distortion of
     * csv-file structure.
     */
    private String clean(String source) {
        if (source != null) {
            return source.replaceAll("[\r\n]", " ").trim();
        }
        return null;
    }

    /**
     * Objects necessary for export to Csv-file
     */
    @Data
    @AllArgsConstructor
    public class CsvEnvironment {
        private final String cellDelimiter;
        private StringBuilder reportBuilder;
    }
}
