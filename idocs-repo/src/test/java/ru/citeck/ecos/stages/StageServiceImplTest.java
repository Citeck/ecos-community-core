package ru.citeck.ecos.stages;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.model.Repository;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.model.IdocsModel;
//import ru.citeck.ecos.test.ApplicationContextHelper;
import ru.citeck.ecos.model.StagesModel;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * @author Maxim Strizhov
 */
@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
//@ContextConfiguration(loader = ApplicationContextHelper.class)
@Transactional
public class StageServiceImplTest {
    //private static StageServiceImpl stageService;
    private static NodeService nodeService;
    private static Repository repositoryHelper;

    private NodeRef docRef;

    @BeforeClass
    public static void setUpClass() {
//        ApplicationContext context = ApplicationContextHelper.getApplicationContext();
//        ServiceRegistry serviceRegistry = context.getBean("ServiceRegistry", ServiceRegistry.class);
//        repositoryHelper = context.getBean("repositoryHelper", Repository.class);
//        nodeService = serviceRegistry.getNodeService();
//
//        //stageService = context.getBean("stageService", StageServiceImpl.class);
//
//        String userName = AuthenticationUtil.getRunAsUser();
//        if (userName == null) {
//            userName = "admin";
//        }
//        runAsSystem();
//        AuthenticationUtil.setRunAsUser(userName);
    }

    @AfterClass
    public static void tearDownClass() {

    }

    @Before
    public void setUp() {
        NodeRef companyHome = repositoryHelper.getCompanyHome();
        ChildAssociationRef childRef = nodeService.createNode(companyHome,
                ContentModel.ASSOC_CONTAINS,
                QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI,
                        StageServiceImplTest.class.getSimpleName()),
                IdocsModel.TYPE_DOC);
        docRef = childRef.getChildRef();
    }

    @After
    public void tearDown() {
        if (docRef == null) {
            return;
        }
        nodeService.deleteNode(docRef);
    }

    private static void runAsSystem() {
        AuthenticationUtil.setRunAsUserSystem();
    }

    private NodeRef createStageNode(NodeRef parent, String stageNodeName) {
        Map<QName, Serializable> properties = new HashMap<>();
        properties.put(StagesModel.PROP_STATE, StagesModel.CONSTR_STAGE_STATE_NOT_STARTED);
        properties.put(StagesModel.PROP_PLANNED_START_DATE, new Date());
        properties.put(StagesModel.PROP_PLANNED_END_DATE, new Date());
        properties.put(StagesModel.PROP_START_EVENT, StagesModel.CONSTR_STAGE_START);
        properties.put(StagesModel.PROP_STOP_EVENT, StagesModel.CONSTR_STAGE_END);

        return nodeService.createNode(parent, StagesModel.ASSOC_CHILD_STAGES,
                QName.createQName(StagesModel.NAMESPACE, stageNodeName),
                StagesModel.TYPE_STAGE, properties).getChildRef();
    }

    @Test
    public void testAddStage() {
        /*NodeRef firstStage = createStageNode(docRef, "firstStage");
        List<NodeRef> stages = stageService.getStages(docRef);
        assertTrue(stages.size() == 1);
        for (NodeRef stageNodeRef : stages) {
            assertEquals(StagesModel.CONSTR_STAGE_START, nodeService.getProperty(stageNodeRef, StagesModel.PROP_START_EVENT));
            assertEquals(StagesModel.CONSTR_STAGE_END, nodeService.getProperty(stageNodeRef, StagesModel.PROP_STOP_EVENT));
        }
        NodeRef secondStage = createStageNode(docRef, "secondStage");
        stages = stageService.getStages(docRef);
        assertTrue(stages.size() == 2);
        NodeRef secondChildStage = createStageNode(secondStage, "secondChildStage");
        stages = stageService.getStages(docRef);
        assertTrue(stages.size() == 2);*/
    }

    @Test
    public void testStartStopStage() {
        /*NodeRef stageRef = createStageNode(docRef, "testStage");
        stageService.startStage(stageRef);
        assertNotNull(nodeService.getProperty(stageRef, StagesModel.PROP_ACTUAL_START_DATE));
        assertEquals(nodeService.getProperty(stageRef, StagesModel.PROP_STATE), "started");

        stageService.stopStage(stageRef);
        assertNotNull(nodeService.getProperty(stageRef, StagesModel.PROP_ACTUAL_END_DATE));
        assertEquals(nodeService.getProperty(stageRef, StagesModel.PROP_STATE), "stopped");*/
    }
}
