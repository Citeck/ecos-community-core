package ru.citeck.ecos.action.group;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.validator.routines.UrlValidator;
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
    protected HtmlEnvironment createEnvironment(GroupActionConfig config, List<String> requestedAttributes, List<String> columnTitles) throws Exception {
        mimeType = MIMETYPE;
        URL templateUrl = Thread.currentThread().getContextClassLoader().getResource(FTL_TEMPLATE_PATH);
        if (templateUrl == null) {
            log.error("Failed to find export action template {}", config);
            return null;
        }

        HtmlEnvironment htmlEnvironment = new HtmlEnvironment(config);
        Configuration templateConfiguration = new Configuration();
        String path = templateUrl.getPath();
        htmlEnvironment.setInnerStream(new ByteArrayOutputStream());
        try {
            templateConfiguration.setDirectoryForTemplateLoading(new File(path));
            htmlEnvironment.setWriter(new OutputStreamWriter(htmlEnvironment.getInnerStream(), encoding));
        } catch (UnsupportedEncodingException e) {
            log.error("Failed to create export action {}", config, e);
            throw e;
        } catch (IOException e) {
            log.error("Failed to setup export action template configuration {}", config, e);
        }
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
    protected int writeData(List<RecordAtts> nodesAttributes, int nextRowIndex, List<String> requestedAttributes, HtmlEnvironment environment) {
        if (environment.getWriter() == null)
            return nextRowIndex;
        environment.getTemplateParams().put(PARAM_ROW_IDX, nextRowIndex);
        List<List<NodeDef>> nodesData = new ArrayList<>();
        for (RecordAtts attribute : nodesAttributes) {
            List<NodeDef> rowData = new ArrayList<>();
            for (int attIdx = 0; attIdx < requestedAttributes.size(); attIdx++) {
                String attributeName = requestedAttributes.get(attIdx);
                DataValue dataValue = attribute.getAtt(attributeName);
                String url = null;
                if (dataValue.isTextual() && UrlValidator.getInstance().isValid(dataValue.asText())) {
                    try {
                        url = new URL(dataValue.asText()).toString();
                    } catch (MalformedURLException e) {
                        //do not need to do anything
                    }
                }
                rowData.add(new NodeDef(dataValue.asText(), url, dataValue.isInt()));
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

        return nextRowIndex + nodesAttributes.size();
    }

    @Override
    protected void writeToStream(ByteArrayOutputStream outputStream, HtmlEnvironment environment) throws IOException {
        if (environment.getWriter() == null) {
            return;
        }
        try {
            Template headerTemplate = environment.getTemplateConfiguration().getTemplate(FTL_BOTTOM_TEMPLATE, encoding);
            headerTemplate.process(environment.getTemplateParams(), environment.getWriter());
        } catch (TemplateException e) {
            log.error(ERROR_MSG, environment.getConfig(), e);
        }
        environment.getInnerStream().writeTo(outputStream);
        environment.getInnerStream().close();
    }

    private void createColumnTitlesRow(HtmlEnvironment htmlEnvironment) {
        if (htmlEnvironment.getWriter() == null) {
            return;
        }
        try {
            Template headerTemplate = htmlEnvironment.getTemplateConfiguration().getTemplate(FTL_HEADER_TEMPLATE, encoding);
            headerTemplate.process(htmlEnvironment.getTemplateParams(), htmlEnvironment.getWriter());
        } catch (TemplateException | IOException e) {
            log.error(ERROR_MSG, htmlEnvironment.getConfig(), e);
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
