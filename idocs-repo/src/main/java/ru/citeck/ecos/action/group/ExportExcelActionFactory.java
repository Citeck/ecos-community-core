package ru.citeck.ecos.action.group;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ClassPathResource;
import org.springframework.extensions.surf.util.I18NUtil;
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
public class ExportExcelActionFactory extends AbstractExportActionFactory<ExportExcelActionFactory.ExcelEnvironment> {
    private static final String ACTION_ID = "download-report-xlsx-action";
    private static final String MIMETYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Override
    public String getActionId() {
        return ACTION_ID;
    }

    @Override
    protected ExcelEnvironment createEnvironment(GroupActionConfig config, List<String> requestedAttributes, List<String> columnTitles) {
        mimeType = MIMETYPE;
        Workbook workbook = null;
        String templatePath = config.getStrParam(PARAM_TEMPLATE);
        if (StringUtils.isBlank(templatePath)) {
            log.debug("The template path parameter '{}' is emptry or was not defined", PARAM_TEMPLATE);
        } else {
            try (InputStream bookTemplateStream = new ClassPathResource(templatePath).getInputStream()) {
                if (bookTemplateStream != null) {
                    workbook = WorkbookFactory.create(bookTemplateStream);
                } else {
                    log.error("The template '{}' was not found", templatePath);
                }
            } catch (InvalidFormatException | IOException e) {
                log.error("Failed to create Excel-file {}", config, e);
            }
        }
        if (workbook == null) {
            workbook = createDefaultWorkbook();
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

        ExcelEnvironment excelEnvironment = new ExcelEnvironment(workbook, sheet);
        createCellStyles(excelEnvironment);
        return excelEnvironment;
    }

    @Override
    protected int writeData(List<RecordAtts> nodesAttributes, int nextRowIndex, List<String> requestedAttributes, ExcelEnvironment excelEnvironment) {
        for (RecordAtts attributes : nodesAttributes) {
            Row currentRow = excelEnvironment.getSheet().createRow(nextRowIndex);
            for (int attIdx = 0; attIdx < requestedAttributes.size(); attIdx++) {
                String attributeName = requestedAttributes.get(attIdx);
                Cell newCell = currentRow.createCell(attIdx);
                newCell.setCellStyle(excelEnvironment.getValueCellStyle());
                DataValue dataValue = attributes.getAtt(attributeName);
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
            ++nextRowIndex;
        }
        return nextRowIndex;
    }

    @Override
    protected void writeToStream(ByteArrayOutputStream outputStream, ExcelEnvironment excelEnvironment) throws Exception {
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

    private void createCellStyles(ExcelEnvironment environment) {
        CellStyle valueCellStyle = environment.getWorkbook().createCellStyle();
        CellStyle doubleCellStyle = environment.getWorkbook().createCellStyle();
        DataFormat dataFormat = environment.getWorkbook().createDataFormat();
        doubleCellStyle.setDataFormat(dataFormat.getFormat("0.0"));

        Row sourceRow = environment.getSheet().getRow(1);
        if (sourceRow != null) {
            Cell formatCell = sourceRow.getCell(0);
            valueCellStyle.cloneStyleFrom(formatCell.getCellStyle());
            doubleCellStyle.cloneStyleFrom(formatCell.getCellStyle());
        }
        valueCellStyle.setWrapText(true);
        valueCellStyle.setVerticalAlignment(CellStyle.VERTICAL_CENTER);

        environment.setValueCellStyle(valueCellStyle);
        environment.setDoubleCellStyle(doubleCellStyle);
    }

    public static Workbook createDefaultWorkbook() {
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

    /**
     * Objects necessary for export to Excel-file
     */
    @Data
    public class ExcelEnvironment {
        private Workbook workbook;
        private Sheet sheet;
        private CellStyle valueCellStyle;
        private CellStyle doubleCellStyle;

        public ExcelEnvironment(Workbook workbook, Sheet sheet) {
            this.workbook = workbook;
            this.sheet = sheet;
        }
    }
}
