package ru.citeck.ecos.action.group;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.Assert;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.citeck.ecos.action.group.impl.BaseGroupAction;
import ru.citeck.ecos.records2.RecordRef;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

public class ExportExcelActionFactoryTest extends ExportActionFactoryBaseTest {

    @Override
    protected String getFileName() {
        return "testfile.xlsx";
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    public void TestExport(int batchSize) {
        ExportExcelActionFactory factory = new ExportExcelActionFactory();
        GroupActionConfig groupActionConfig = adjustFactory("download-report-xlsx-action", factory, batchSize);

        BaseGroupAction<RecordRef> action = factory.createAction(groupActionConfig);
        List<RecordRef> refs = getList();
        for (RecordRef recordRef : refs) {
            Assert.assertTrue("Record was not export " + recordRef, action.process(recordRef));
        }

        ActionResults<RecordRef> actionResults = action.complete();
        for (ActionResult<RecordRef> result : actionResults.getResults()) {
            Assert.assertTrue("Export complete is incorrect", result.getStatus().isOk());
        }

        File testFile = getFile();
        Assert.assertTrue("Test file does not exists", testFile.exists());

        try (FileInputStream inputStream = new FileInputStream(testFile)) {
            final Workbook workbook = WorkbookFactory.create(inputStream);
            final Sheet sheet = workbook.getSheetAt(0);
            Assert.assertEquals("Row count does not match", refs.size(), sheet.getLastRowNum());
            Assert.assertEquals("Column count does not match", getColumns().size(), sheet.getRow(0).getLastCellNum());
        } catch (IOException | InvalidFormatException e) {
            throw new RuntimeException(e);
        }

    }
}
