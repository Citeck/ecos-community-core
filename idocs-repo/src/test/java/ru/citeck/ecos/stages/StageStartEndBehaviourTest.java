package ru.citeck.ecos.stages;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.model.Repository;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.context.ApplicationContext;
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

import static org.junit.Assert.assertEquals;

/**
 * @author Maxim Strizhov
 */
@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
//@ContextConfiguration(loader = ApplicationContextHelper.class)
@Transactional
public class StageStartEndBehaviourTest {
    //private static StageServiceImpl stageService;
    protected static NodeService nodeService;
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
//        NodeRef companyHome = repositoryHelper.getCompanyHome();
//        ChildAssociationRef childRef = nodeService.createNode(companyHome,
//                ContentModel.ASSOC_CONTAINS,
//                QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI,
//                        StageServiceImplTest.class.getSimpleName()),
//                IdocsModel.TYPE_DOC);
//        docRef = childRef.getChildRef();
    }

    @After
    public void tearDown() {
//        if (docRef == null) {
//            return;
//        }
//        nodeService.deleteNode(docRef);
    }

    private static void runAsSystem() {
        AuthenticationUtil.setRunAsUserSystem();
    }

    private NodeRef createStageNode(NodeRef parent, String stageNodeName, NodeRef startStageRef, NodeRef stopStageRef, String startEvent, String stopEvent) {
        Map<QName, Serializable> properties = new HashMap<>();
        properties.put(StagesModel.PROP_STATE, StagesModel.CONSTR_STAGE_STATE_NOT_STARTED);
        properties.put(StagesModel.PROP_PLANNED_START_DATE, new Date());
        properties.put(StagesModel.PROP_PLANNED_END_DATE, new Date());
        properties.put(StagesModel.PROP_START_EVENT, startEvent);
        properties.put(StagesModel.PROP_STOP_EVENT, stopEvent);

        NodeRef sourceNodeRef = nodeService.createNode(parent, StagesModel.ASSOC_CHILD_STAGES,
                QName.createQName(StagesModel.NAMESPACE, stageNodeName),
                StagesModel.TYPE_STAGE, properties).getChildRef();

        if (startStageRef != null) {
            nodeService.createAssociation(sourceNodeRef, startStageRef, StagesModel.ASSOC_START_EVENT_STAGE);
        }
        if (stopStageRef != null) {
            nodeService.createAssociation(sourceNodeRef, stopStageRef, StagesModel.ASSOC_STOP_EVENT_STAGE);
        }
        return sourceNodeRef;
    }

    @Test
    public void testStartStageOnStartEvent() {
        /*NodeRef sourceStage = createStageNode(docRef, "Primary Stage", null, null, StagesModel.CONSTR_USER_ACTION, StagesModel.CONSTR_USER_ACTION);
        NodeRef startDependantStage = createStageNode(docRef, "Dependant Stage", sourceStage, null, StagesModel.CONSTR_STAGE_START, StagesModel.CONSTR_USER_ACTION);
        stageService.startStage(sourceStage);
        assertEquals(StagesModel.CONSTR_STAGE_STATE_STARTED, nodeService.getProperty(startDependantStage, StagesModel.PROP_STATE));*/
    }

    @Test
    public void testStartStageOnEndEvent() {
        /*NodeRef sourceStage = createStageNode(docRef, "Primary Stage", null, null, StagesModel.CONSTR_USER_ACTION, StagesModel.CONSTR_USER_ACTION);
        NodeRef endDependantStage = createStageNode(docRef, "Dependant Stage", sourceStage, null, StagesModel.CONSTR_STAGE_END, StagesModel.CONSTR_USER_ACTION);
        stageService.startStage(sourceStage);
        stageService.stopStage(sourceStage);
        assertEquals(StagesModel.CONSTR_STAGE_STATE_STARTED, nodeService.getProperty(endDependantStage, StagesModel.PROP_STATE));*/
    }

    @Test
    public void testStopStageOnStartEvent() {
        /*NodeRef sourceStage = createStageNode(docRef, "Primary Stage", null, null, StagesModel.CONSTR_USER_ACTION, StagesModel.CONSTR_USER_ACTION);
        NodeRef endDependantStage = createStageNode(docRef, "Dependant Stage", null, sourceStage, StagesModel.CONSTR_USER_ACTION, StagesModel.CONSTR_STAGE_START);
        stageService.startStage(endDependantStage);
        stageService.startStage(sourceStage);
        assertEquals(StagesModel.CONSTR_STAGE_STATE_STOPPED, nodeService.getProperty(endDependantStage, StagesModel.PROP_STATE));*/
    }

    @Test
    public void testStopStageOnEndEvent() {
        /*NodeRef sourceStage = createStageNode(docRef, "Primary Stage", null, null, StagesModel.CONSTR_USER_ACTION, StagesModel.CONSTR_USER_ACTION);
        NodeRef endDependantStage = createStageNode(docRef, "Dependant Stage", null, sourceStage, StagesModel.CONSTR_USER_ACTION, StagesModel.CONSTR_STAGE_END);
        stageService.startStage(endDependantStage);
        stageService.startStage(sourceStage);
        stageService.stopStage(sourceStage);
        assertEquals(StagesModel.CONSTR_STAGE_STATE_STOPPED, nodeService.getProperty(endDependantStage, StagesModel.PROP_STATE));*/
    }
}
