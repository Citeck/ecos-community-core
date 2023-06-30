package ru.citeck.ecos.action.group;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.Pair;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.action.group.impl.BaseGroupAction;
import ru.citeck.ecos.action.group.output.ExportOutputActionsRegistry;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.data.ObjectData;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.lib.attributes.dto.AttributeType;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts;
import ru.citeck.ecos.utils.EcosI18NUtils;
import ru.citeck.ecos.utils.RepoUtils;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AbstractExportActionFactory is the abstract base class for group export factories which produce group actions.
 * Have to register factory implementation at GroupActionService usually
 * in constructor body: <code>groupActionService.register(this)</code>
 * <p>Default encoding is StandardCharsets.UTF_8, default Mime-type is 'text/plain'
 *
 * @param <T> class contains necessary fields for exporting data such as output templates or format settings
 */
@Slf4j
public abstract class AbstractExportActionFactory<T> implements GroupActionFactory<RecordRef> {

    private static final FastDateFormat DATE_FORMAT = FastDateFormat.getInstance("dd.MM.yyyy");
    private static final FastDateFormat DEFAULT_DATE_FORMAT = FastDateFormat.getInstance("yyyyMMdd");
    private static final FastDateFormat DATE_TIME_FORMAT = FastDateFormat.getInstance("dd.MM.yyyy HH:mm:ss");

    protected RecordsService recordsService;
    protected ContentService contentService;
    protected NodeService nodeService;
    protected GroupActionService groupActionService;

    protected String mimeType = "text/plain";
    protected String encoding = StandardCharsets.UTF_8.name();

    protected static final String EMPTY_REPORT_MSG = "Report columns list is empty";
    protected static final String ERROR_MSG_ENVIRONMENT_NULL = "Export environment was not defined";
    protected static final String PARAM_TEMPLATE = "template";
    protected static final String PARAM_REPORT_TITLE = "reportTitle";
    protected static final String PARAM_REPORT_COLUMNS = "columns";
    protected static final String PARAM_REPORT_DATE_FORMAT = "dateFormat";
    protected static final String PARAM_REPORT_DECIMAL_FORMAT = "decimalFormat";
    protected static final String REPORT = "Report";
    protected static final NodeRef ROOT_NODE_REF = new NodeRef("workspace://SpacesStore/attachments-root");

    protected static final String PARAM_OUTPUT = "output";
    protected static final String PARAM_OUTPUT_TYPE = "type";
    protected static final String PARAM_OUTPUT_CONFIG = "config";

    private ExportOutputActionsRegistry outputActionsRegistry;

    public AbstractExportActionFactory() {
    }

    @PostConstruct
    protected void register() {
        if (groupActionService != null) {
            groupActionService.register(this);
        }
    }

    @Override
    public ExportAction createAction(GroupActionConfig config) {

        GroupActionConfig localConfig = new GroupActionConfig(config);

        OutputConfig outConfig = null;
        JsonNode output = localConfig.getParams().get(PARAM_OUTPUT);
        if (output != null && output.isObject() && output.get(PARAM_OUTPUT_TYPE).isTextual()) {
            String outputType = output.get(PARAM_OUTPUT_TYPE).asText();
            if (StringUtils.isNotBlank(outputType)) {
                outConfig = new OutputConfig();
                outConfig.setType(outputType);
                outConfig.setConfig(ObjectData.create(output.get(PARAM_OUTPUT_CONFIG)));
                outputActionsRegistry.validate(outConfig.type, outConfig.config);
            }
        }

        localConfig.setAsync(outConfig != null);

        String errorsLimitStr = config.getStrParam("errorsLimit");
        if (StringUtils.isNotBlank(errorsLimitStr)) {
            localConfig.setErrorsLimit(Integer.parseInt(errorsLimitStr));
        }
        String elementsLimitStr = config.getStrParam("elementsLimit");
        if (StringUtils.isNotBlank(elementsLimitStr)) {
            localConfig.setElementsLimit(Integer.parseInt(elementsLimitStr));
        }
        String maxResultsStr = config.getStrParam("maxResults");
        if (StringUtils.isNotBlank(maxResultsStr)) {
            localConfig.setMaxResults(Integer.parseInt(maxResultsStr));
        }
        String batchSizeStr = config.getStrParam("batchSize");
        if (StringUtils.isNotBlank(batchSizeStr)) {
            localConfig.setBatchSize(Integer.parseInt(batchSizeStr));
        }

        return new ExportAction(localConfig, outConfig);
    }

    /**
     * Create environment object specific for certain export
     *
     * @param config   configuration for current action
     * @param columns  columns for export
     * @return environment object with default settings
     * @throws Exception which can occur during the environment initialization
     */
    protected abstract T createEnvironment(GroupActionConfig config, List<ReportColumnDef> columns) throws Exception;

    /**
     * Writes nodes data according to the PARAM_REPORT_COLUMNS configuration.
     * Calls for each batch of nodes. Batch size is defined at GroupActionConfig.
     *
     * @param lines               lines for export
     * @param nextRowIndex        start index where to write data
     * @param environment         fields and settings for export
     * @return next row index after data was written
     */
    protected abstract int writeData(List<List<DataValue>> lines, int nextRowIndex, T environment);

    /**
     * Writes collected data to output stream at the end of the export
     */
    protected abstract void writeToStream(ByteArrayOutputStream outputStream, T environment) throws Exception;

    protected static String replaceIllegalChars(String source) {
        return source != null
            ? source.replaceAll("[\\\\/:*?\"<>|]", "_")
            : null;
    }

    @Autowired
    public void setRecordsService(RecordsService recordsService) {
        this.recordsService = recordsService;
    }

    @Autowired
    public void setContentService(ContentService contentService) {
        this.contentService = contentService;
    }

    @Autowired
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    @Autowired
    public void setGroupActionService(GroupActionService groupActionService) {
        this.groupActionService = groupActionService;
    }

    @Autowired
    public void setOutputActionsRegistry(ExportOutputActionsRegistry outputActionsRegistry) {
        this.outputActionsRegistry = outputActionsRegistry;
    }

    class ExportAction extends BaseGroupAction<RecordRef> {

        private final List<ReportColumnDef> columns;
        private final Map<String, String> attributesToRequest;
        private FastDateFormat configDateFormat;

        private final T environment;
        private int nodesRowIdx = 1;

        @Nullable
        private final OutputConfig outputConfig;

        public ExportAction(GroupActionConfig config, @Nullable OutputConfig outputConfig) {
            super(config);

            this.outputConfig = outputConfig;

            String dateFormatParam = config.getStrParam(PARAM_REPORT_DATE_FORMAT);
            if (StringUtils.isNotEmpty(dateFormatParam)) {
                configDateFormat = FastDateFormat.getInstance(dateFormatParam);
            }

            JsonNode columnsParam = config.getParams().get(PARAM_REPORT_COLUMNS);
            List<ReportColumnDef> columnsFromParam = DataValue.create(columnsParam).asList(ReportColumnDef.class);
            columns = normalizeReportColumns(columnsFromParam);
            attributesToRequest = new HashMap<>();

            for (ReportColumnDef columnDef : columns) {
                String attributeToRequest = columnDef.getAttribute();
                if (columnDef.isMultiple()) {
                    attributeToRequest += "[]";
                }
                attributesToRequest.put(columnDef.getAttribute(), attributeToRequest);
            }
            try {
                environment = createEnvironment(config, columns);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create environment", e);
            }
        }

        private List<ReportColumnDef> normalizeReportColumns(List<ReportColumnDef> columns) {

            List<ReportColumnDef> filteredColumns = columns.stream()
                .filter(it -> StringUtils.isNotBlank(it.getAttribute()))
                .collect(Collectors.toList());

            for (ReportColumnDef columnDef : filteredColumns) {
                if (StringUtils.isBlank(columnDef.getName())) {
                    columnDef.setName(columnDef.getAttribute());
                }
                if (columnDef.getType() == null) {
                    columnDef.setType(AttributeType.TEXT);
                }
            }
            return filteredColumns;
        }

        @Override
        protected void processNodesImpl(List<RecordRef> nodes) {
            if (environment == null) {
                log.error(ERROR_MSG_ENVIRONMENT_NULL);
                return;
            }
            if (CollectionUtils.isEmpty(nodes)) {
                log.warn("Process nodes was not defined");
                return;
            }
            if (attributesToRequest.isEmpty()) {
                log.warn("Requested attribute list is empty");
                return;
            }

            List<RecordAtts> nodesAttributes = recordsService.getAtts(nodes, attributesToRequest);

            List<List<DataValue>> lines = new ArrayList<>();
            for (RecordAtts atts : nodesAttributes) {
                List<DataValue> line = new ArrayList<>();
                for (ReportColumnDef column : columns) {
                    DataValue value = atts.getAtt(column.getAttribute());
                    line.add(formatCell(column, value));
                }
                lines.add(line);
            }

            nodesRowIdx = writeData(lines, nodesRowIdx, environment);
        }

        @NotNull
        private DataValue formatCell(@NotNull ReportColumnDef columnDef, @NotNull DataValue value) {

            if (value.isArray()) {
                if (value.size() == 0) {
                    return DataValue.createStr("");
                }
                StringBuilder sb = new StringBuilder();
                for (DataValue arrValue : value) {
                    sb.append(formatCell(columnDef, arrValue).asText()).append(", ");
                }
                sb.setLength(sb.length() - 2);
                return DataValue.createStr(sb.toString());
            }

            AttributeType type = columnDef.getType();

            FastDateFormat dateFormat = null;
            if (AttributeType.DATE.equals(type)) {
                dateFormat = configDateFormat != null ? configDateFormat : DATE_FORMAT;
            } else if (AttributeType.DATETIME.equals(type)) {
                dateFormat = DATE_TIME_FORMAT;
            }
            if (dateFormat != null) {
                if (!value.isTextual()) {
                    return value;
                }
                String valueStr = value.asText();
                if (StringUtils.isBlank(valueStr) || !valueStr.endsWith("Z") || !valueStr.contains("T")) {
                    try {
                        return DataValue.createStr(dateFormat.format(DEFAULT_DATE_FORMAT.parse(valueStr)));
                    } catch (ParseException e) {
                    }
                    return value;
                }
                Instant instant = Json.getMapper().convert(valueStr, Instant.class);
                if (instant == null) {
                    return value;
                }
                return DataValue.createStr(dateFormat.format(Date.from(instant)));
            }
            if (AttributeType.NUMBER.equals(type) && value.isNotNull()) {
                try {
                    return DataValue.create(Double.valueOf(value.asText()));
                } catch (NumberFormatException e) {
                }
            }
            if (AttributeType.BOOLEAN.equals(type)) {
                String text = value.asText();
                String valueRes = "";
                if (!text.isEmpty()) {
                    if (Boolean.TRUE.toString().equals(text)) {
                        valueRes = EcosI18NUtils.getMessage("label.yes");
                    } else {
                        valueRes = EcosI18NUtils.getMessage("label.no");
                    }
                }
                return DataValue.createStr(valueRes);
            }

            return value;
        }

        @Override
        protected void onComplete() {
            super.onComplete();
            if (environment == null) {
                log.error(ERROR_MSG_ENVIRONMENT_NULL);
                return;
            }
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                writeToStream(outputStream, environment);
                Pair<ActionResult<RecordRef>, NodeRef> actionResultList = createContentNode(outputStream);
                if (outputConfig != null) {
                    outputActionsRegistry.execute(outputConfig.type, outputConfig.config, actionResultList.getSecond());
                }
                onProcessed(Collections.singletonList(actionResultList.getFirst()));
            } catch (Exception e) {
                log.error("Failed to write file. {}", config, e);
                ActionResult<RecordRef> result = new ActionResult<>(RecordRef.valueOf(REPORT), ActionStatus.error(e));
                onProcessed(Collections.singletonList(result));
            }
        }

        protected Pair<ActionResult<RecordRef>, NodeRef> createContentNode(ByteArrayOutputStream baos) {

            Map<QName, Serializable> props = new HashMap<>(1);
            String name = "records_export_" + Instant.now().toEpochMilli();
            props.put(ContentModel.PROP_NAME, name);

            NodeRef contentNode = nodeService.createNode(ROOT_NODE_REF, ContentModel.ASSOC_CHILDREN,
                QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, name),
                ContentModel.TYPE_CONTENT, props).getChildRef();
            ActionStatus groupActionStatus;
            try (InputStream byteArrayInputStream = new ByteArrayInputStream(baos.toByteArray())) {
                ContentWriter writer = contentService.getWriter(contentNode, ContentModel.PROP_CONTENT, true);
                writer.setMimetype(mimeType);
                writer.setEncoding(encoding);
                writer.putContent(byteArrayInputStream);
                groupActionStatus = ActionStatus.ok();
            } catch (Exception e) {
                log.error("Failed to write node content. {} ", config, e);
                groupActionStatus = ActionStatus.error(e);
            }
            String statusUrl = RepoUtils.getDownloadURL(contentNode).substring(1);
            groupActionStatus.setUrl(statusUrl);
            ActionResult<RecordRef> groupActionResult = new ActionResult<>(
                RecordRef.valueOf("Document"),
                groupActionStatus);

            return new Pair<>(groupActionResult, contentNode);
        }
    }

    @Data
    private static class OutputConfig {
        private String type;
        private ObjectData config;
    }
}
