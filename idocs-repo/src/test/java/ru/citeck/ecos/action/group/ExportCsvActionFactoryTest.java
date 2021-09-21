package ru.citeck.ecos.action.group;

import org.junit.Assert;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.citeck.ecos.action.group.impl.BaseGroupAction;
import ru.citeck.ecos.records2.RecordRef;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public class ExportCsvActionFactoryTest extends ExportActionFactoryBaseTest {

    @Override
    protected String getFileName() {
        return "testfile.txt";
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    public void TestExport(int batchSize) {
        ExportCsvActionFactory factory = new ExportCsvActionFactory();
        GroupActionConfig groupActionConfig = adjustFactory("download-report-csv-action", factory, batchSize);

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

        try {
            List<String> fileContent = Files.readAllLines(testFile.toPath());
            Assert.assertEquals("Row count does not match", refs.size() + 1, fileContent.size());
            String[] columns = fileContent.get(0).split(ExportCsvActionFactory.DEFAULT_DELIMITER);
            Assert.assertEquals("Column count does not match", getColumns().size(), columns.length);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
