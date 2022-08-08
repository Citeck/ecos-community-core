package ru.citeck.ecos.action.group;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.supercsv.io.CsvListWriter;
import org.supercsv.prefs.CsvPreference;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.utils.ExceptionUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
    public static final char DEFAULT_DELIMITER = '\t';
    private static final String DEFAULT_SEPARATOR = "\r\n";

    @Override
    public String getActionId() {
        return ACTION_ID;
    }

    @Override
    protected CsvEnvironment createEnvironment(GroupActionConfig config, List<ReportColumnDef> columns) throws Exception {

        mimeType = MIMETYPE;

        String configDelimiter = config.getStrParam(CONFIG_DELIMITER);
        char delimiter = DEFAULT_DELIMITER;
        if (StringUtils.isNotBlank(configDelimiter)) {
            delimiter = configDelimiter.charAt(0);
        }

        StringBuilder stringBuilder = new StringBuilder();
        StringBuilderWriter sbWriter = new StringBuilderWriter(stringBuilder);
        CsvPreference preference = new CsvPreference.Builder('"', delimiter, DEFAULT_SEPARATOR).build();
        CsvListWriter writer = new CsvListWriter(sbWriter, preference);

        CsvEnvironment environment = new CsvEnvironment(stringBuilder, writer);

        createColumnTitlesRow(columns, environment);
        return environment;
    }

    @Override
    protected int writeData(List<List<DataValue>> lines, int nextRowIndex, CsvEnvironment csvEnvironment) {
        for (List<DataValue> line : lines) {
            try {
                csvEnvironment.writer.write(line.stream().map(DataValue::asText).toArray(String[]::new));
            } catch (Exception e) {
                ExceptionUtils.throwException(e);
            }
        }
        return nextRowIndex + lines.size();
    }

    @Override
    protected void writeToStream(ByteArrayOutputStream outputStream, CsvEnvironment csvEnvironment) throws Exception {
        csvEnvironment.writer.flush();
        outputStream.write(csvEnvironment.getReportStringBuilder().toString().getBytes(encoding));
    }

    private void createColumnTitlesRow(List<ReportColumnDef> columns, CsvEnvironment csvEnvironment) throws IOException {
        if (CollectionUtils.isEmpty(columns)) {
            log.warn(EMPTY_REPORT_MSG);
            return;
        }
        String[] titles = columns.stream()
            .map(ReportColumnDef::getName)
            .toArray(String[]::new);

        csvEnvironment.writer.writeHeader(titles);
    }

    /**
     * Objects necessary for export to Csv-file
     */
    @Data
    @AllArgsConstructor
    public static class CsvEnvironment {
        private StringBuilder reportStringBuilder;
        private CsvListWriter writer;
    }
}
