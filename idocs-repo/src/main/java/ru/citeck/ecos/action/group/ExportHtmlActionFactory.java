package ru.citeck.ecos.action.group;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory of group action "Export to Html-file".
 * Constructs file from *.FTL templates
 */
@Slf4j
@Component
public class ExportHtmlActionFactory extends AbstractExportActionFactory<ExportHtmlActionFactory.HtmlEnvironment> {
    private static final String ACTION_ID = "download-report-html-action";
    private static final String MIMETYPE = "text/html";

    private static final String FTL_COLUMN_TITLES = "columnTitles";
    private static final String FTL_TEMPLATE_PATH = "/alfresco/templates/reports/ru/citeck";
    private static final String FTL_HEADER_TEMPLATE = "defaultHeader.ftl";
    private static final String FTL_ROW_TEMPLATE = "defaultRow.ftl";
    private static final String FTL_BOTTOM_TEMPLATE = "defaultBottom.ftl";
    private static final String PARAM_ROW_IDX = "rowIdx";
    private static final String PARAM_NODES = "nodes";
    private static final String ERROR_MSG = "Failed to generate Html-report {}";

    @Override
    public String getActionId() {
        return ACTION_ID;
    }

    @Override
    protected HtmlEnvironment createEnvironment(GroupActionConfig config, List<ReportColumnDef> columns) throws Exception {
        mimeType = MIMETYPE;
        ClassPathResource classPathResource = new ClassPathResource(FTL_TEMPLATE_PATH);

        HtmlEnvironment htmlEnvironment = new HtmlEnvironment(config);
        Configuration templateConfiguration = new Configuration();
        htmlEnvironment.setInnerStream(new ByteArrayOutputStream());
        try {
            templateConfiguration.setDirectoryForTemplateLoading(classPathResource.getFile());
            htmlEnvironment.setWriter(new OutputStreamWriter(htmlEnvironment.getInnerStream(), encoding));
        } catch (UnsupportedEncodingException e) {
            log.error("Failed to create export action {}", config, e);
            throw e;
        } catch (IOException e) {
            log.error("Failed to setup export action template configuration {}", config, e);
            throw e;
        }

        List<String> columnTitles = columns.stream()
            .map(ReportColumnDef::getName)
            .collect(Collectors.toList());

        htmlEnvironment.setTemplateConfiguration(templateConfiguration);
        htmlEnvironment.getTemplateParams().put(PARAM_REPORT_TITLE, config.getStrParam(PARAM_REPORT_TITLE));
        htmlEnvironment.getTemplateParams().put(FTL_COLUMN_TITLES, columnTitles);
        if (CollectionUtils.isEmpty(columnTitles)) {
            log.warn(EMPTY_REPORT_MSG);
        } else {
            createColumnTitlesRow(htmlEnvironment);
        }

        return htmlEnvironment;
    }

    @Override
    protected int writeData(List<List<DataValue>> lines, int nextRowIndex, HtmlEnvironment environment) {
        if (environment.getWriter() == null) {
            return nextRowIndex;
        }
        environment.getTemplateParams().put(PARAM_ROW_IDX, nextRowIndex);
        List<List<NodeDef>> nodesData = new ArrayList<>();
        for (List<DataValue> line : lines) {
            List<NodeDef> rowData = new ArrayList<>();
            for (DataValue value : line) {
                String url = null;
                if (value.isTextual() && UrlValidator.getInstance().isValid(value.asText())) {
                    try {
                        url = new URL(value.asText()).toString();
                    } catch (MalformedURLException e) {
                        //do not need to do anything
                    }
                }
                rowData.add(new NodeDef(value.asText(), url, value.isInt()));
            }
            nodesData.add(rowData);
        }
        environment.getTemplateParams().put(PARAM_NODES, nodesData);
        try {
            Template bodyTemplate = environment.getTemplateConfiguration().getTemplate(FTL_ROW_TEMPLATE, encoding);
            bodyTemplate.process(environment.getTemplateParams(), environment.getWriter());
        } catch (TemplateException | IOException e) {
            log.error(ERROR_MSG, environment.getConfig(), e);
        }

        return nextRowIndex + lines.size();
    }

    @Override
    protected void writeToStream(ByteArrayOutputStream outputStream, HtmlEnvironment environment) throws Exception {
        if (environment.getWriter() == null) {
            return;
        }
        try {
            Template headerTemplate = environment.getTemplateConfiguration().getTemplate(FTL_BOTTOM_TEMPLATE, encoding);
            headerTemplate.process(environment.getTemplateParams(), environment.getWriter());
        } catch (TemplateException e) {
            log.error(ERROR_MSG, environment.getConfig(), e);
            throw e;
        }
        environment.getInnerStream().writeTo(outputStream);
        environment.getInnerStream().close();
    }

    private void createColumnTitlesRow(HtmlEnvironment htmlEnvironment) throws Exception {
        if (htmlEnvironment.getWriter() == null) {
            return;
        }
        try {
            Template headerTemplate = htmlEnvironment.getTemplateConfiguration().getTemplate(FTL_HEADER_TEMPLATE, encoding);
            headerTemplate.process(htmlEnvironment.getTemplateParams(), htmlEnvironment.getWriter());
        } catch (TemplateException | IOException e) {
            log.error(ERROR_MSG, htmlEnvironment.getConfig(), e);
            throw e;
        }
    }

    /**
     * Objects necessary for export to Html-file
     */
    @Data
    public class HtmlEnvironment {
        private GroupActionConfig config;
        private Writer writer;
        private Configuration templateConfiguration;
        private ByteArrayOutputStream innerStream;
        private Map<String, Object> templateParams = new HashMap<>();

        HtmlEnvironment(GroupActionConfig config) {
            this.config = config;
        }
    }
}
