package ru.citeck.ecos.action.group;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.filestore.FileContentWriter;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.namespace.QName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import ru.citeck.ecos.commons.data.DataValue;
import ru.citeck.ecos.records2.RecordRef;
import ru.citeck.ecos.records2.source.dao.local.RecordsDaoBuilder;
import ru.citeck.ecos.records3.RecordsService;
import ru.citeck.ecos.records3.RecordsServiceFactory;
import ru.citeck.ecos.records3.record.atts.value.AttValue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class ExportActionFactoryBaseTest {

    public static final String PARAM_COLUMNS = "columns";
    protected static final String TEST = "test";
    protected String fileName = "testfile";
    protected List<ReportColumnDef> columnDefs;

    @BeforeEach
    @AfterEach
    public void removeFile() {
        File testFile = new File(getFileName());
        if (testFile.exists()) {
            testFile.delete();
        }
    }

    protected File getFile() {
        return new File(getFileName());
    }

    protected String getFileName() {
        return fileName;
    }

    protected List<RecordRef> getList() {
        ArrayList<RecordRef> refs = new ArrayList<>();
        for (int idx = 0; idx < 4; idx++) {
            refs.add(RecordRef.create(TEST, TEST + String.valueOf(idx)));
        }
        return refs;
    }

    protected List<ReportColumnDef> getColumns() {
        if (columnDefs != null) {
            return columnDefs;
        }
        columnDefs = new ArrayList<>();
        for (int idx = 1; idx < 4; idx++) {
            columnDefs.add(new ReportColumnDef(String.format("Column %d Title", idx), "prop" + idx));
        }
        return columnDefs;
    }

    protected GroupActionConfig adjustFactory(String actionId, AbstractExportActionFactory factory, int batchSize) {
        GroupActionService actionService = Mockito.mock(GroupActionService.class);
        NodeService nodeService = Mockito.mock(NodeService.class);
        Mockito.when(nodeService.createNode(Mockito.any(),
                Mockito.eq(ContentModel.ASSOC_CHILDREN),
                Mockito.any(), Mockito.any(), Mockito.any()))
            .thenAnswer(
                invocationOnMock -> {
                    ChildAssociationRef result = new ChildAssociationRef(
                        (QName) invocationOnMock.getArguments()[2],
                        (NodeRef) invocationOnMock.getArguments()[0],
                        (QName) invocationOnMock.getArguments()[3],
                        new NodeRef(new StoreRef("storeRef workspace://SpacesStore"), "testNodeId"));
                    return result;
                });

        File testFile = new File(getFileName());
        ContentService contentService = Mockito.mock(ContentService.class);
        Mockito.when(contentService.getWriter(
                Mockito.any(), Mockito.eq(ContentModel.PROP_CONTENT), Mockito.eq(true)))
            .thenReturn(new FileContentWriter(testFile));

        GroupActionConfig actionConfig = new GroupActionConfig();
        actionConfig.setBatchSize(batchSize);
        actionConfig.setActionId(actionId);
        actionConfig.setParam("reportTitle", "Test report");

        DataValue params = DataValue.create(actionConfig.getParams());
        params.set(PARAM_COLUMNS, getColumns());
        actionConfig.setParams(params.getAs(ObjectNode.class));

        RecordsServiceFactory recordsServices = new RecordsServiceFactory();
        RecordsService recordsService = recordsServices.getRecordsServiceV1();
        RecordsDaoBuilder daoBuilder = RecordsDaoBuilder.create("test");
        for (int recIdx = 0; recIdx < 110; recIdx++) {
            RecordValue value = new RecordValue(recIdx);
            daoBuilder.addRecord(value.getId(), value);
        }
        recordsService.register(daoBuilder.build());

        factory.setGroupActionService(actionService);
        factory.setContentService(contentService);
        factory.setRecordsService(recordsService);
        factory.setNodeService(nodeService);

        return actionConfig;
    }

    @Data
    @RequiredArgsConstructor
    static class RecordValue implements AttValue {
        private final int idx;
        public String getId() {
            return TEST + idx;
        }
        public Object getAtt(String name) throws Exception {
            return "record " + getId() + " " + name + " value";
        }
    }
}
