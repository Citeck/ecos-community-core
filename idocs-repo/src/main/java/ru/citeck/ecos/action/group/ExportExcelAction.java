package ru.citeck.ecos.action.group;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.GUID;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.action.group.impl.BaseGroupAction;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.utils.RepoUtils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

@Slf4j
@Component
public class ExportExcelAction implements GroupActionFactory<RecordRef> {

    private static final String ACTION_ID = "download-xlsx-report-action";
    private static final String PARAM_TEMPLATE = "template";
    private static final String PARAM_REPORT_TITLE = "reportTitle";
    private static final String PARAM_REPORT_COLUMNS = "columns";
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_TITLE = "name";
    private static final NodeRef rootNodeRef = new NodeRef("workspace://SpacesStore/attachments-root");

    private RecordsService recordsService;
    private ContentService contentService;
    private NodeService nodeService;

    @Autowired
    public ExportExcelAction(TransactionService transactionService,
                             GroupActionService groupActionService,
                             ContentService contentService,
                             NodeService nodeService,
                             RecordsService recordsService) {
        this.recordsService = recordsService;
        this.nodeService = nodeService;
        this.contentService = contentService;
        groupActionService.register(this);
    }

    @Override
    public GroupAction<RecordRef> createAction(GroupActionConfig config) {
        return new Action(config);
    }

    @Override
    public String getActionId() {
        return ACTION_ID;
    }

    class Action extends BaseGroupAction<RecordRef> {

        private Workbook workbook;
        private Sheet sheet;
        private CellStyle valueCellStyle;
        private CellStyle doubleCellStyle;
        private int nodesRowIdx = 1;

        List<String> requestedAttributes = new ArrayList<>();

        Action(GroupActionConfig config) {
            super(config);
            String templatePath = config.getStrParam(PARAM_TEMPLATE);
            if (StringUtils.isBlank(templatePath)) {
                log.warn("The template path parameter '{}' is emptry or was not defined", PARAM_TEMPLATE);
            } else
                try (InputStream bookTemplateStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("/" + templatePath)) {
                    workbook = WorkbookFactory.create(bookTemplateStream);
                } catch (InvalidFormatException | IOException e) {
                    log.error("Failed to create Excel-file", e);
                }
            if (workbook == null) {
                workbook = createDefaultWorkbook();
            }

            int sheetIdx = 0;
            sheet = workbook.getSheetAt(sheetIdx);

            String reportTitle = config.getStrParam(PARAM_REPORT_TITLE);
            if (StringUtils.isNotBlank(reportTitle)) {
                workbook.setSheetName(sheetIdx, replaceIllegalChars(reportTitle));
                Header header = sheet.getHeader();
                String headerCenter = header.getCenter();
                if (headerCenter != null) {
                    headerCenter = headerCenter.replace(String.format("{%s}", PARAM_REPORT_TITLE), reportTitle);
                    header.setCenter(headerCenter);
                }
            }

            List<String> columnTitles = new ArrayList<>();
            JsonNode columns = config.getParams().get(PARAM_REPORT_COLUMNS);
            if (columns != null && columns.isArray()) {
                for (final JsonNode column : columns) {
                    JsonNode idNode = column.get(COLUMN_ID);
                    if (idNode != null) {
                        requestedAttributes.add(idNode.asText());
                    }
                    JsonNode titleNode = column.get(COLUMN_TITLE);
                    String title = titleNode != null ? titleNode.asText() : "";
                    columnTitles.add(title == null ? "" : title);
                }
            }

            createColumnTitlesRow(columnTitles);
            createCellStyles();
        }

        @Override
        protected void processNodesImpl(List<RecordRef> nodes) {
            if (CollectionUtils.isEmpty(nodes)){
                log.warn("Process node was not defined");
                return;
            }
            for (RecordRef node: nodes) {
                nodesRowIdx = writeData(node, nodesRowIdx);
            }
        }

        @Override
        protected void onComplete() {
            super.onComplete();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                autoSizeColumns();
                workbook.write(outputStream);
                List<ActionResult<RecordRef>> actionResultList = createContentNode(outputStream, rootNodeRef);
                onProcessed(actionResultList);
            } catch (IOException e) {
                log.error("Failed to write Excel-file", e);
                ActionResult<RecordRef> result = new ActionResult<>(RecordRef.valueOf("Report"), ActionStatus.error(e));
                onProcessed(Collections.singletonList(result));
            } finally {
                IOUtils.closeQuietly(outputStream);
            }
        }

        protected List<ActionResult<RecordRef>> createContentNode(ByteArrayOutputStream byteArrayOutputStream,
                                                                  NodeRef parentRef) {
            Map<QName, Serializable> props = new HashMap(1);
            String name = GUID.generate();
            props.put(ContentModel.PROP_NAME, name);

            NodeRef contentNode = nodeService.createNode(parentRef, ContentModel.ASSOC_CHILDREN,
                QName.createQName("http://www.alfresco.org/model/content/1.0", name),
                ContentModel.TYPE_CONTENT, props).getChildRef();
            ActionStatus groupActionStatus;
            try (InputStream byteArrayInputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray())) {
                ContentWriter writer = contentService.getWriter(contentNode, ContentModel.PROP_CONTENT, true);
                writer.setMimetype("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                writer.setEncoding("UTF-8");
                writer.putContent(byteArrayInputStream);
                groupActionStatus = ActionStatus.ok();
            } catch (Exception e) {
                log.error("Failed to write node content", e);
                groupActionStatus = ActionStatus.error(e);
            }

            groupActionStatus.setUrl(RepoUtils.getDownloadURL(contentNode, nodeService));
            ActionResult<RecordRef> groupActionResult = new ActionResult(
                RecordRef.valueOf("Document"),
                groupActionStatus);
            return Collections.singletonList(groupActionResult);
        }

        private Workbook createDefaultWorkbook() {
            Workbook defaultWorkbook = new XSSFWorkbook();
            Sheet sheet = defaultWorkbook.createSheet();
            Row row = sheet.createRow(0);
            CellStyle headerStyle = defaultWorkbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.SKY_BLUE.getIndex());
            headerStyle.setFillPattern(CellStyle.SOLID_FOREGROUND);
            Font font = defaultWorkbook.createFont();
            font.setFontName("Arial");
            font.setFontHeightInPoints((short) 14);
            font.setBoldweight(Font.BOLDWEIGHT_BOLD);
            headerStyle.setFont(font);
            Cell headerCell = row.createCell(0);
            headerCell.setCellStyle(headerStyle);
            return defaultWorkbook;
        }

        private void createCellStyles() {
            valueCellStyle = workbook.createCellStyle();
            doubleCellStyle = workbook.createCellStyle();
            DataFormat dataFormat = workbook.createDataFormat();
            doubleCellStyle.setDataFormat(dataFormat.getFormat("0.0"));

            Row sourceRow = sheet.getRow(1);
            if (sourceRow != null) {
                Cell formatCell = sourceRow.getCell(0);
                valueCellStyle.cloneStyleFrom(formatCell.getCellStyle());
                doubleCellStyle.cloneStyleFrom(formatCell.getCellStyle());
            }
        }

        private void createColumnTitlesRow(List<String> columnTitles) {
            if (CollectionUtils.isEmpty(columnTitles)) {
                log.warn("Report columns list is empty");
                return;
            }
            Row row = sheet.getRow(0);
            Cell formatCell = row.getCell(0);
            CellStyle titleCellStyle = workbook.createCellStyle();
            titleCellStyle.cloneStyleFrom(formatCell.getCellStyle());
            for (int idx = 0; idx < columnTitles.size(); idx++) {
                Cell cell = row.createCell(idx);
                cell.setCellStyle(titleCellStyle);
                cell.setCellValue(columnTitles.get(idx) != null ? columnTitles.get(idx) : "");
            }
        }

        private int writeData(RecordRef node, int nextRowIndex) {
            if (requestedAttributes.isEmpty()) {
                return nextRowIndex;
            }

            Row currentRow = sheet.createRow(nextRowIndex);
            for (int attIdx = 0; attIdx < requestedAttributes.size(); attIdx++) {
                String attributeName = requestedAttributes.get(attIdx);
                Cell newCell = currentRow.createCell(attIdx);
                newCell.setCellStyle(valueCellStyle);
                DataValue dataValue = recordsService.getAtt(node, attributeName);
                if (dataValue != null) {
                    if (dataValue.isDouble()) {
                        newCell.setCellStyle(doubleCellStyle);
                        newCell.setCellValue(dataValue.asDouble());
                    } else if (dataValue.isInt()) {
                        newCell.setCellValue(dataValue.asInt());
                    } else {
                        try {
                            //Todo: test hyperlink case
                            URL urlValue = new URL(dataValue.asText());
                            newCell.setCellType(Cell.CELL_TYPE_FORMULA);
                            newCell.setCellFormula(
                                String.format("HYPERLINK(\"%s\", \"%s\")", urlValue,
                                    dataValue.asText()));
                        } catch (MalformedURLException e) {
                            newCell.setCellValue(dataValue.asText());
                        }
                    }
                }
            }
            return nextRowIndex + 1;
        }

        private void autoSizeColumns(){
            for (int idx = 0; idx < requestedAttributes.size(); idx++) {
                sheet.autoSizeColumn(idx);
            }
        }

        private String replaceIllegalChars(String source) {
            return source != null
                ? source.replaceAll("[\\\\/:*?\"<>|]", "_")
                : null;
        }
    }
}
