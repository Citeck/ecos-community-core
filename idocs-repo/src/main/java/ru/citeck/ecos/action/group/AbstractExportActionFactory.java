package ru.citeck.ecos.action.group;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.springframework.beans.factory.annotation.Autowired;
import ru.citeck.ecos.action.group.impl.BaseGroupAction;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.commons.json.Json;
import ru.citeck.ecos.model.lib.attributes.dto.AttributeType;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts;
import ru.citeck.ecos.utils.RepoUtils;

import javax.annotation.PostConstruct;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

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
    protected static final String REPORT = "Report";
    protected static final NodeRef ROOT_NODE_REF = new NodeRef("workspace://SpacesStore/attachments-root");

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

        localConfig.setAsync(false);

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

        return new ExportAction(localConfig);
    }

    /**
     * Create environment object specific for certain export
     *
     * @param config              configuration for current action
     * @param requestedAttributes attributes requested by action settings for export @see ReportColumnDef
     * @param columnTitles        Column titles which export file must provide @see ReportColumnDef
     * @return environment object with default settings
     * @throws Exception which can occur during the environment initialization
     */
    protected abstract T createEnvironment(GroupActionConfig config, List<String> requestedAttributes, List<String> columnTitles) throws Exception;

    /**
     * Writes nodes data according to the PARAM_REPORT_COLUMNS configuration.
     * Calls for each batch of nodes. Batch size is defined at GroupActionConfig.
     *
     * @param nodesAttributes     data source for export
     * @param nextRowIndex        start index where to write data
     * @param requestedAttributes attributes requested by action settings for export @see ReportColumnDef
     * @param environment         fields and settings for export
     * @return next row index after data was written
     */
    protected abstract int writeData(List<RecordAtts> nodesAttributes, int nextRowIndex, List<String> requestedAttributes, T environment);

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

    class ExportAction extends BaseGroupAction<RecordRef> {
        /**
         * Attributes requested by action settings for export
         *
         * @see ReportColumnDef
         */
        protected List<String> requestedAttributes = new ArrayList<>();

        private Map<String, AttributeType> typeByAttribute = new HashMap<>();
        /**
         * Column titles which export file must provide
         *
         * @see ReportColumnDef
         */
        protected List<String> columnTitles = new ArrayList<>();

        private final T environment;
        private int nodesRowIdx = 1;

        public ExportAction(GroupActionConfig config) {
            super(config);

            JsonNode columnsParam = config.getParams().get(PARAM_REPORT_COLUMNS);
            List<ReportColumnDef> columns = DataValue.create(columnsParam).asList(ReportColumnDef.class);
            for (ReportColumnDef columnDef : columns) {
                String attributeName = columnDef.getAttribute();
                if (StringUtils.isNotBlank(attributeName)) {
                    requestedAttributes.add(attributeName);
                    typeByAttribute.put(attributeName, columnDef.getType());
                } else {
                    log.warn("Attribute name was not defined {} \n{}", columnDef.getName(), config);
                    continue;
                }
                columnTitles.add(StringUtils.isNotBlank(columnDef.getName()) ? columnDef.getName() :
                    attributeName);
            }
            try {
                environment = createEnvironment(config, requestedAttributes, columnTitles);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to create environment", e);
            }
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
            if (requestedAttributes.isEmpty()) {
                log.warn("Requested attribute list is empty");
                return;
            }

            List<RecordAtts> nodesAttributes = recordsService.getAtts(nodes, requestedAttributes);

            // date format. todo: refactor
            typeByAttribute.forEach((att, type) -> {
                FastDateFormat format = null;
                if (AttributeType.DATE.equals(type)) {
                    format = DATE_FORMAT;
                } else if (AttributeType.DATETIME.equals(type)) {
                    format = DATE_TIME_FORMAT;
                }
                if (format != null) {
                    for (RecordAtts atts : nodesAttributes) {
                        DataValue value = atts.getAtt(att);
                        if (value.isTextual()) {
                            String valueStr = value.asText();
                            if (StringUtils.isNotBlank(valueStr)
                                    && valueStr.endsWith("Z")
                                    && valueStr.contains("T")) {
                                Instant instant = Json.getMapper().convert(valueStr, Instant.class);
                                if (instant != null) {
                                    atts.setAtt(att, format.format(Date.from(instant)));
                                }
                            }
                        }
                    }
                }
            });

            nodesRowIdx = writeData(nodesAttributes, nodesRowIdx, requestedAttributes, environment);
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
                List<ActionResult<RecordRef>> actionResultList = createContentNode(outputStream);
                onProcessed(actionResultList);
            } catch (Exception e) {
                log.error("Failed to write file. {}", config, e);
                ActionResult<RecordRef> result = new ActionResult<>(RecordRef.valueOf(REPORT), ActionStatus.error(e));
                onProcessed(Collections.singletonList(result));
            }
        }

        protected List<ActionResult<RecordRef>> createContentNode(ByteArrayOutputStream byteArrayOutputStream) {

            Map<QName, Serializable> props = new HashMap<>(1);
            String name = "records_export_" + Instant.now().toEpochMilli();
            props.put(ContentModel.PROP_NAME, name);

            NodeRef contentNode = nodeService.createNode(ROOT_NODE_REF, ContentModel.ASSOC_CHILDREN,
                QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, name),
                ContentModel.TYPE_CONTENT, props).getChildRef();
            ActionStatus groupActionStatus;
            try (InputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray())) {
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
            return Collections.singletonList(groupActionResult);
        }
    }
}
