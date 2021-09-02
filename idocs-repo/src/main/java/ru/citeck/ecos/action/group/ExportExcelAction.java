package ru.citeck.ecos.action.group;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

@Slf4j
@Component
public class ExportExcelAction implements GroupActionFactory<RecordRef> {

    private static final String ACTION_ID = "download-xlsx-report-action";

    private RecordsService recordsService;
    private ContentService contentService;
    private NodeService nodeService;

    @Autowired
    public ExportExcelAction(GroupActionService groupActionService,
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

    class Action extends ExportAction {
        private static final String MIMETYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

        private Workbook workbook;
        private Sheet sheet;
        private CellStyle valueCellStyle;
        private CellStyle doubleCellStyle;

        Action(GroupActionConfig config) {
            super(config, contentService, nodeService, recordsService, MIMETYPE, null);
            String templatePath = config.getStrParam(PARAM_TEMPLATE);
            if (StringUtils.isBlank(templatePath)) {
                log.warn("The template path parameter '{}' is emptry or was not defined", PARAM_TEMPLATE);
            } else {
                try (InputStream bookTemplateStream = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream("/" + templatePath)) {
                    if (bookTemplateStream != null) {
                        workbook = WorkbookFactory.create(bookTemplateStream);
                    }else{
                        log.warn("The template '{}' was not found", templatePath);
                    }
                } catch (InvalidFormatException | IOException e) {
                    log.error("Failed to create Excel-file {}", config, e);
                }
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
            createColumnTitlesRow(columnTitles);
            createCellStyles();
        }

        @Override
        protected int writeData(List<RecordRef> nodes, int nextRowIndex) {
            if (requestedAttributes.isEmpty()) {
                return nextRowIndex;
            }

            List<RecordAtts> nodesAttributes = recordsService.getAtts(nodes, requestedAttributes);
            for (RecordAtts attribute : nodesAttributes) {
                Row currentRow = sheet.createRow(nextRowIndex);
                for (int attIdx = 0; attIdx < requestedAttributes.size(); attIdx++) {
                    String attributeName = requestedAttributes.get(attIdx);
                    Cell newCell = currentRow.createCell(attIdx);
                    newCell.setCellStyle(valueCellStyle);
                    DataValue dataValue = attribute.getAtt(attributeName);
                    if (dataValue != null) {
                        if (dataValue.isDouble()) {
                            newCell.setCellStyle(doubleCellStyle);
                            newCell.setCellValue(dataValue.asDouble());
                        } else if (dataValue.isInt()) {
                            newCell.setCellValue(dataValue.asInt());
                        } else {
                            if (dataValue.isTextual() && UrlValidator.getInstance().isValid(dataValue.asText())) {
                                try {
                                    URL urlValue = new URL(dataValue.asText());
                                    newCell.setCellType(Cell.CELL_TYPE_FORMULA);
                                    newCell.setCellFormula(
                                        String.format("HYPERLINK(\"%s\", \"%s\")", urlValue,
                                            dataValue.asText()));
                                } catch (MalformedURLException e) {
                                    newCell.setCellValue(dataValue.asText());
                                }
                            } else {
                                newCell.setCellValue(dataValue.asText());
                            }
                        }
                    }
                }
                ++nextRowIndex;
            }
            return nextRowIndex;
        }

        @Override
        protected void writeToStream(ByteArrayOutputStream outputStream) throws IOException {
            autoSizeColumns();
            workbook.write(outputStream);
        }

        private Workbook createDefaultWorkbook() {
            Workbook defaultWorkbook = new XSSFWorkbook();
            Sheet sheet = defaultWorkbook.createSheet();
            Row row = sheet.createRow(0);
            CellStyle headerStyle = defaultWorkbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.SKY_BLUE.getIndex());
            headerStyle.setFillPattern(CellStyle.SOLID_FOREGROUND);
            headerStyle.setAlignment(CellStyle.ALIGN_CENTER);
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
            valueCellStyle.setWrapText(true);
            valueCellStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);
        }

        private void createColumnTitlesRow(List<String> columnTitles) {
            if (CollectionUtils.isEmpty(columnTitles)) {
                log.warn(EMPTY_REPORT_MSG);
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

        private void autoSizeColumns() {
            //autosize does not work for hyperlink cells
            for (int idx = 0; idx < requestedAttributes.size(); idx++) {
                sheet.autoSizeColumn(idx);
            }
        }
    }
}
