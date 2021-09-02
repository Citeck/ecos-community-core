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
import org.alfresco.util.GUID;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import ru.citeck.ecos.action.group.impl.BaseGroupAction;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.utils.RepoUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Base class
 * Have to override writeData for list or single node
 */
@Slf4j
public abstract class ExportAction extends BaseGroupAction<RecordRef> {

    protected static final String PARAM_TEMPLATE = "template";
    protected static final String PARAM_REPORT_TITLE = "reportTitle";
    protected static final String PARAM_REPORT_COLUMNS = "columns";
    protected static final String REPORT = "Report";
    protected static final String EMPTY_REPORT_MSG = "Report columns list is empty";
    protected static final NodeRef ROOT_NODE_REF = new NodeRef("workspace://SpacesStore/attachments-root");


    protected RecordsService actionRecordsService;
    protected ContentService actionContentService;
    protected NodeService actionNodeService;

    protected List<String> requestedAttributes = new ArrayList<>();
    protected List<String> columnTitles = new ArrayList<>();

    protected int nodesRowIdx = 1;
    protected String mimeType = "text/plain";
    protected String encoding = StandardCharsets.UTF_8.name();

    public ExportAction(GroupActionConfig config,
                        ContentService contentService,
                        NodeService nodeService,
                        RecordsService recordsService,
                        String fileMimeType,
                        String fileEncoding) {
        super(config);
        this.actionRecordsService = recordsService;
        this.actionNodeService = nodeService;
        this.actionContentService = contentService;
        if (StringUtils.isNotBlank(fileMimeType)) {
            this.mimeType = fileMimeType;
        }
        if (StringUtils.isNotBlank(fileEncoding)) {
            this.encoding = fileEncoding;
        }

        JsonNode columnsParam = config.getParams().get(PARAM_REPORT_COLUMNS);
        List<ReportColumnDef> columns = DataValue.create(columnsParam).asList(ReportColumnDef.class);
        for (ReportColumnDef columnDef : columns) {
            String attributeName = columnDef.getAttribute();
            if (StringUtils.isNotBlank(attributeName)) {
                requestedAttributes.add(attributeName);
            } else {
                log.warn("Attribute name was not defined {} \n{}", columnDef.getName(), config);
                continue;
            }
            columnTitles.add(StringUtils.isNotBlank(columnDef.getName()) ? columnDef.getName() :
                attributeName);
        }
    }

    @Override
    protected void processNodesImpl(List<RecordRef> nodes) {
        if (CollectionUtils.isEmpty(nodes)) {
            log.warn("Process node was not defined");
            return;
        }
        nodesRowIdx = writeData(nodes, nodesRowIdx);
    }

    /**
     * Writes nodes data according to the PARAM_REPORT_COLUMNS configuration.
     * Calls for each batch of nodes. Batch size is defined at GroupActionConfig.
     *
     * @param nodes        - data source for export
     * @param nextRowIndex - start index where to write data
     * @return next row index after data was written
     */
    protected int writeData(List<RecordRef> nodes, int nextRowIndex) {
        for (RecordRef node : nodes) {
            nextRowIndex = writeData(node, nextRowIndex);
        }
        return nextRowIndex;
    }

    /**
     * Write single node data
     *
     * @param node         - data source
     * @param nextRowIndex - start index where to write data
     * @return next row index after data was written
     */
    protected int writeData(RecordRef node, int nextRowIndex) {
        return nextRowIndex + 1;
    }

    @Override
    protected void onComplete() {
        super.onComplete();
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            writeToStream(outputStream);
            List<ActionResult<RecordRef>> actionResultList = createContentNode(outputStream, ROOT_NODE_REF);
            onProcessed(actionResultList);
        } catch (IOException e) {
            log.error("Failed to write file. {}", config, e);
            ActionResult<RecordRef> result = new ActionResult<>(RecordRef.valueOf(REPORT), ActionStatus.error(e));
            onProcessed(Collections.singletonList(result));
        }
    }

    protected abstract void writeToStream(ByteArrayOutputStream outputStream) throws IOException;

    protected List<ActionResult<RecordRef>> createContentNode(ByteArrayOutputStream byteArrayOutputStream,
                                                              NodeRef parentRef) {
        Map<QName, Serializable> props = new HashMap(1);
        String name = GUID.generate();
        props.put(ContentModel.PROP_NAME, name);

        NodeRef contentNode = actionNodeService.createNode(parentRef, ContentModel.ASSOC_CHILDREN,
            QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, name),
            ContentModel.TYPE_CONTENT, props).getChildRef();
        ActionStatus groupActionStatus;
        try (InputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray())) {
            ContentWriter writer = actionContentService.getWriter(contentNode, ContentModel.PROP_CONTENT, true);
            writer.setMimetype(mimeType);
            writer.setEncoding(encoding);
            writer.putContent(byteArrayInputStream);
            groupActionStatus = ActionStatus.ok();
        } catch (Exception e) {
            log.error("Failed to write node content. {} " + config, e);
            groupActionStatus = ActionStatus.error(e);
        }
        String statusUrl = RepoUtils.getDownloadURL(contentNode, actionNodeService).substring(1);
        groupActionStatus.setUrl(statusUrl);
        ActionResult<RecordRef> groupActionResult = new ActionResult(
            RecordRef.valueOf("Document"),
            groupActionStatus);
        return Collections.singletonList(groupActionResult);
    }

    protected static String replaceIllegalChars(String source) {
        return source != null
            ? source.replaceAll("[\\\\/:*?\"<>|]", "_")
            : null;
    }
}
