package ru.citeck.ecos.action.group;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.records3.record.atts.dto.RecordAtts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

/**
 * Factory of group action "Export to Excel-file"
 */
@Slf4j
@Component
public class ExportExcelActionFactory extends AbstractExportActionFactory<ExcelEnvironment> {
    private static final String ACTION_ID = "download-xlsx-report-action";
    private static final String MIMETYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    public ExportExcelActionFactory(GroupActionService groupActionService) {
        mimeType = MIMETYPE;
        groupActionService.register(this);
    }

    @Override
    public String getActionId() {
        return ACTION_ID;
    }

    @Override
    protected ExcelEnvironment createEnvironment(GroupActionConfig config, List<String> requestedAttributes, List<String> columnTitles) {
        Workbook workbook = null;
        String templatePath = config.getStrParam(PARAM_TEMPLATE);
        if (StringUtils.isBlank(templatePath)) {
            log.warn("The template path parameter '{}' is emptry or was not defined", PARAM_TEMPLATE);
        } else {
            try (InputStream bookTemplateStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("/" + templatePath)) {
                if (bookTemplateStream != null) {
                    workbook = WorkbookFactory.create(bookTemplateStream);
                } else {
                    log.warn("The template '{}' was not found", templatePath);
                }
            } catch (InvalidFormatException | IOException e) {
                log.error("Failed to create Excel-file {}", config, e);
            }
        }
        if (workbook == null) {
            workbook = ExcelEnvironment.createDefaultWorkbook();
        }
        int sheetIdx = 0;
        Sheet sheet = workbook.getSheetAt(sheetIdx);
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
        createColumnTitlesRow(columnTitles, workbook, sheet);

        return new ExcelEnvironment(workbook, sheet);
    }

    @Override
    protected int writeData(List<RecordAtts> nodesAttributes, int nextRowIndex, List<String> requestedAttributes, ExcelEnvironment excelEnvironment) {
        for (RecordAtts attribute : nodesAttributes) {
            Row currentRow = excelEnvironment.getSheet().createRow(nextRowIndex);
            for (int attIdx = 0; attIdx < requestedAttributes.size(); attIdx++) {
                String attributeName = requestedAttributes.get(attIdx);
                Cell newCell = currentRow.createCell(attIdx);
                newCell.setCellStyle(excelEnvironment.getValueCellStyle());
                DataValue dataValue = attribute.getAtt(attributeName);
                if (dataValue != null) {
                    if (dataValue.isDouble()) {
                        newCell.setCellStyle(excelEnvironment.getDoubleCellStyle());
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
    protected void writeToStream(ByteArrayOutputStream outputStream, ExcelEnvironment excelEnvironment) throws IOException {
        //autosize does not work for hyperlink cells
        for (int idx = 0; idx <= excelEnvironment.getSheet().getRow(0).getLastCellNum(); idx++) {
            excelEnvironment.getSheet().autoSizeColumn(idx);
        }
        excelEnvironment.getWorkbook().write(outputStream);
    }

    private void createColumnTitlesRow(List<String> columnTitles, Workbook workbook, Sheet sheet) {
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
}
