package ru.citeck.ecos.action.group;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.Assert;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.citeck.ecos.action.group.impl.BaseGroupAction;
import ru.citeck.ecos.records2.RecordRef;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ExportHtmlActionFactoryTest extends ExportActionFactoryBaseTest {

    @Override
    protected String getFileName() {
        return "testfile.html";
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    public void TestExport(int batchSize) {
        ExportHtmlActionFactory factory = new ExportHtmlActionFactory();
        GroupActionConfig groupActionConfig = adjustFactory("download-report-html-action", factory, batchSize);

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
            Document htmlFile = Jsoup.parse(testFile, factory.encoding);
            Elements rows = htmlFile.body().getElementsByTag("tr");
            Assert.assertEquals("Row count does not match", refs.size() + 1, rows.size());
            Elements cells = htmlFile.body().getElementsByTag("td");
            Assert.assertEquals("Cell count does not match", getColumns().size() * (refs.size() + 1), cells.size());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
