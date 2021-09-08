package ru.citeck.ecos.action.group;

import lombok.Data;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

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
        createCellStyles();
    }

    private void createCellStyles() {
        CellStyle valueCellStyle = workbook.createCellStyle();
        CellStyle doubleCellStyle = workbook.createCellStyle();
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

        setValueCellStyle(valueCellStyle);
        setDoubleCellStyle(doubleCellStyle);
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
}
