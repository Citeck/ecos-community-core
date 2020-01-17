/*
 * Copyright (C) 2008-2015 Citeck LLC.
 *
 * This file is part of Citeck EcoS
 *
 * Citeck EcoS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Citeck EcoS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Citeck EcoS. If not, see <http://www.gnu.org/licenses/>.
 */
package ru.citeck.ecos.processor.report;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import ru.citeck.ecos.processor.AbstractDataBundleLine;
import ru.citeck.ecos.processor.DataBundle;
import ru.citeck.ecos.processor.ProcessorConstants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Create Excel Report from DataBundle
 *
 * @author Alexey Moiseev <alexey.moiseev@citeck.ru>
 */
public class ReportOutputExcel extends AbstractDataBundleLine {

    private static final String REPORT_DATA = "reportData";
    private static final String REPORT_TITLE = "reportTitle";
    private static final String REPORT_COLUMNS = "reportColumns";
    private static final String COLUMN_TITLE = "title";
    private static final String XLSX_MIMETYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    
    private String template;

    @Override
    public DataBundle process(DataBundle input) {
        Map<String, Object> model = input.needModel();
        HashMap<String, Object> newModel = new HashMap<>();
        newModel.putAll(model);
        newModel.put(ProcessorConstants.KEY_MIMETYPE, XLSX_MIMETYPE);
        
        ByteArrayOutputStream os = getExcelReportStream(getTemplate(), model);
        InputStream excelIS = null;
        if (os != null) {
            excelIS = new ByteArrayInputStream(os.toByteArray());
            IOUtils.closeQuietly(os);
        }
        
        return new DataBundle(excelIS, newModel);
    }
    
    @SuppressWarnings("unchecked")
    private ByteArrayOutputStream getExcelReportStream(String template, Map<String, Object> model) {
        InputStream is = null;
        try {
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream("/" + template);
            Workbook wb = WorkbookFactory.create(is);
            
            Sheet sheet = wb.getSheetAt(0);
            Header header = sheet.getHeader();
            Header firstHeader = ((XSSFSheet) sheet).getFirstHeader();
            
            // set titles
            if (model.get(REPORT_TITLE) != null) {
                String reportTitle = (String) model.get(REPORT_TITLE);
                wb.setSheetName(wb.getSheetIndex(sheet), replaceIllegalChars(reportTitle));
                
                String firstCenter = firstHeader.getCenter(); 
                if (firstCenter != null) {
                    firstCenter = firstCenter.replace("{reportTitle}", reportTitle);
                    firstHeader.setCenter(firstCenter);
                }
                
                String otherCenter = header.getCenter(); 
                if (otherCenter != null) {
                    otherCenter = otherCenter.replace("{reportTitle}", reportTitle);
                    header.setCenter(otherCenter);
                }
            }
            
            List<Map<String, String>> reportColumns = (List<Map<String, String>>) model.get(REPORT_COLUMNS);
            List<List<Map<String, Object>>> reportData = (List<List<Map<String, Object>>>) model.get(REPORT_DATA);
            
            createColumnTitlesRow(wb, sheet, reportColumns);

            createSheetData(wb, sheet, reportColumns, reportData);

            // remove rows if no columns defined or no data presents
            if ((reportColumns == null) || (reportColumns.isEmpty())) {
                removeRow(sheet, 0);
                removeRow(sheet, 0);
            } else if ((reportData == null) || (reportData.isEmpty())) {
                removeRow(sheet, 1);
            }    
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos;
        } catch (Exception e) {
            Logger.getLogger(ReportOutputExcel.class).error(e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(is);
        }

        return null;
    }
    
    private void createColumnTitlesRow(Workbook wb, Sheet sheet, List<Map<String, String>> reportColumns) {
        if ((reportColumns == null) || (reportColumns.isEmpty())) {
            return;
        }

        Row row = sheet.getRow(0);
        Cell formatCell = row.getCell(0);
        CellStyle titleCellStyle = wb.createCellStyle();
        titleCellStyle.cloneStyleFrom(formatCell.getCellStyle());

        int i = 0;
        for (Map<String, String> col : reportColumns) {
            if (col == null) {
                continue;
            }

            String title = col.get(COLUMN_TITLE);

            if (title == null) {
                title = "";
            }

            if (i == 0) {
                formatCell.setCellValue(title);
            } else {
                Cell cell = row.createCell(i);
                cell.setCellStyle(titleCellStyle);
                cell.setCellValue(title);
            }

            i++;
        }
    }

    private void createSheetData(Workbook wb, Sheet sheet, List<Map<String, String>> reportColumns,
                                 List<List<Map<String, Object>>> reportData) {
        if ((reportColumns == null) || (reportColumns.isEmpty()) || (reportData == null) || (reportData.isEmpty())) {
            return;
        }

        Row sourceRow = sheet.getRow(1);
        Cell formatCell = sourceRow.getCell(0);
        CellStyle valueCellStyle = wb.createCellStyle();
        valueCellStyle.cloneStyleFrom(formatCell.getCellStyle());
        CellStyle doubleCellStyle = wb.createCellStyle();
        DataFormat dataFormat = wb.createDataFormat();
        doubleCellStyle.cloneStyleFrom(formatCell.getCellStyle());
        doubleCellStyle.setDataFormat(dataFormat.getFormat("0.0"));

        int i = 0;
        for (List<Map<String, Object>> rowData : reportData) {
            if (rowData != null) {
                Row newRow = sheet.createRow(i+2);

                int j = 0;
                for (Map<String, Object> cellData : rowData) {
                    Cell newCell = newRow.createCell(j);
                    newCell.setCellStyle(valueCellStyle);

                    String dataType = (String) cellData.get(ReportProducer.DATA_TYPE_ATTR);
                    String valStr = String.valueOf(cellData.get(ReportProducer.DATA_VALUE_ATTR));
                    if (valStr != null && !valStr.isEmpty()) {
                        if ("Integer".equals(dataType)) {
                            try {
                                Integer val = Integer.parseInt(valStr);
                                newCell.setCellType(Cell.CELL_TYPE_NUMERIC);
                                newCell.setCellValue(Double.valueOf(val));
                            } catch (NumberFormatException e) {
                                appendStringValue(newCell, valStr);
                            }
                        } else if ("Double".equals(dataType)) {
                            try {
                                double val = Double.parseDouble(valStr);
                                newCell.setCellStyle(doubleCellStyle);
                                newCell.setCellType(Cell.CELL_TYPE_NUMERIC);
                                newCell.setCellValue(val);
                            } catch (NumberFormatException e) {
                                appendStringValue(newCell, valStr);
                            }
                        } else if ("String".equals(dataType)) {
                            appendStringValue(newCell, valStr);
                        }
                    }
                    j++;
                }

                if (i == 19) {
                    autoSizeColumns(sheet);
                }

                i++;
            }

            if (i < 20) {
                autoSizeColumns(sheet);
            }
        }

        removeRow(sheet, 1);
    }
    
    private void autoSizeColumns(Sheet sheet) {
        short columnsCount = sheet.getRow(0).getLastCellNum();
        for (short s = 0; s < columnsCount; s++)
            sheet.autoSizeColumn(s, false);
    }

    private void appendStringValue(Cell cell, String value) {
        if (value != null) {
            String currentValue = cell.getStringCellValue();

            String newValue;
            if ((currentValue != null) && (!currentValue.isEmpty()))
                newValue = currentValue + "\n" + value;
            else
                newValue = value;

            cell.setCellValue(newValue);
        }
    }

    private String replaceIllegalChars(String source) {
        return source != null
                ? source.replaceAll("[\\\\/:*?\"<>|]", "_")
                : null;
    }
    
    public void removeRow(Sheet sheet, int rowIndex) {
        int lastRowNum = sheet.getLastRowNum();
        if (rowIndex >= 0 && rowIndex < lastRowNum) {
            sheet.shiftRows(rowIndex + 1, lastRowNum, -1);
        }
        
        if (rowIndex == lastRowNum) {
            Row removingRow = sheet.getRow(rowIndex);
            if (removingRow != null) {
                sheet.removeRow(removingRow);
            }
        }
    }
    
    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }
}
