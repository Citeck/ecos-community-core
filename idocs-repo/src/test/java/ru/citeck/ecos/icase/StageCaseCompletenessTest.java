package ru.citeck.ecos.icase;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.QName;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import ru.citeck.ecos.icase.completeness.CaseCompletenessServiceImpl;
import ru.citeck.ecos.icase.element.CaseElementServiceImpl;
import ru.citeck.ecos.model.ClassificationModel;
import ru.citeck.ecos.model.ICaseModel;
import ru.citeck.ecos.model.StagesModel;
//import ru.citeck.ecos.test.ApplicationContextHelper;

import java.io.Serializable;
import java.util.*;

/**
 * @author Maxim Strizhov
 */
@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
//@ContextConfiguration(loader=ApplicationContextHelper.class)
@Transactional
public class StageCaseCompletenessTest {
    private static final String TEST_CONFIG_NAME = CaseConstants.ASSOCIATION_ELEMENTS;
    private ApplicationContext applicationContext;
    private NodeService nodeService;
    private CaseElementServiceImpl caseElementService;
    private CaseCompletenessServiceImpl caseCompletenessService;
    //private StageServiceImpl stageService;

    private NodeRef rootNode;
    private NodeRef caseNode1;
    private NodeRef testNode1, testNode2, testNode3;
    private NodeRef level1, level2;
    private NodeRef req11, req12, req21;
    private NodeRef type1, kind11, kind12, type2, kind21, kind22;
    private NodeRef testConfig;
    private NodeRef testStage;

    @Before
    public void setUp() throws Exception {
        // setup services
//        applicationContext = ApplicationContextHelper.getApplicationContext();
//        nodeService = applicationContext.getBean("nodeService", NodeService.class);
//        caseElementService = applicationContext.getBean("caseElementService", CaseElementServiceImpl.class);
//        caseCompletenessService = applicationContext.getBean("caseCompletenessService", CaseCompletenessServiceImpl.class);
//        //stageService = applicationContext.getBean("stageService", StageServiceImpl.class);
//
//        // authenticate
//        AuthenticationUtil.setRunAsUserSystem();
//
//        testConfig = caseElementService.getConfig(TEST_CONFIG_NAME);
//
//        // create test nodes
//        rootNode = createNode(
//                nodeService.getRootNode(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE),
//                ContentModel.TYPE_CONTAINER,
//                ContentModel.ASSOC_CHILDREN, StageCaseCompletenessTest.class.getSimpleName());
//
//        // types and kinds
//        type1 = createNode(rootNode, ContentModel.TYPE_CATEGORY, ContentModel.ASSOC_CHILDREN, "type1");
//        kind11 = createNode(type1, ContentModel.TYPE_CATEGORY, ContentModel.ASSOC_SUBCATEGORIES, "kind11");
//        kind12 = createNode(type1, ContentModel.TYPE_CATEGORY, ContentModel.ASSOC_SUBCATEGORIES, "kind12");
//        type2 = createNode(rootNode, ContentModel.TYPE_CATEGORY, ContentModel.ASSOC_CHILDREN, "type2");
//        kind21 = createNode(type1, ContentModel.TYPE_CATEGORY, ContentModel.ASSOC_SUBCATEGORIES, "kind21");
//        kind22 = createNode(type1, ContentModel.TYPE_CATEGORY, ContentModel.ASSOC_SUBCATEGORIES, "kind22");
//
//        // Level 1
//        level1 = createNode(rootNode, RequirementModel.TYPE_COMPLETENESS_LEVEL, ContentModel.ASSOC_CHILDREN, "level1");
//        // -> Requirement 1.1 (on documents)
//        req11 = createNode(level1, RequirementModel.TYPE_REQUIREMENT, RequirementModel.ASSOC_LEVEL_REQUIREMENT, "req11");
//        nodeService.createAssociation(req11, testConfig, RequirementModel.ASSOC_REQUIREMENT_SCOPE);
//        //    -> document with type1 exists (1-*)
//        nodeService.setProperty(req11, PredicateModel.PROP_QUANTIFIER, Quantifier.EXISTS.toString());
//        NodeRef con111 = createNode(req11, PredicateModel.TYPE_KIND_PREDICATE, PredicateModel.ASSOC_CONSEQUENT, "con111");
//        nodeService.setProperty(con111, PredicateModel.PROP_REQUIRED_TYPE, type1);
//        // -> Requirement 1.2 (on documents)
//        req12 = createNode(level1, RequirementModel.TYPE_REQUIREMENT, RequirementModel.ASSOC_LEVEL_REQUIREMENT, "req12");
//        nodeService.createAssociation(req12, testConfig, RequirementModel.ASSOC_REQUIREMENT_SCOPE);
//        //    -> document with kind21 exactly one
//        nodeService.setProperty(req12, PredicateModel.PROP_QUANTIFIER, Quantifier.EXACTLY_ONE.toString());
//        NodeRef con121 = createNode(req12, PredicateModel.TYPE_KIND_PREDICATE, PredicateModel.ASSOC_CONSEQUENT, "con121");
//        nodeService.setProperty(con121, PredicateModel.PROP_REQUIRED_TYPE, type2);
//        nodeService.setProperty(con121, PredicateModel.PROP_REQUIRED_KIND, kind21);
//
//        // Level 2
//        level2 = createNode(rootNode, RequirementModel.TYPE_COMPLETENESS_LEVEL, ContentModel.ASSOC_CHILDREN, "level2");
//        // -> Requirement 2.1 (on documents)
//        req21 = createNode(level2, RequirementModel.TYPE_REQUIREMENT, RequirementModel.ASSOC_LEVEL_REQUIREMENT, "req12");
//        nodeService.createAssociation(req21, testConfig, RequirementModel.ASSOC_REQUIREMENT_SCOPE);
//        //    -> document with kind12 exactly zero (0)
//        nodeService.setProperty(req21, PredicateModel.PROP_QUANTIFIER, Quantifier.EXACTLY_ZERO.toString());
//        NodeRef con211 = createNode(req21, PredicateModel.TYPE_KIND_PREDICATE, PredicateModel.ASSOC_CONSEQUENT, "con211");
//        nodeService.setProperty(con211, PredicateModel.PROP_REQUIRED_TYPE, type1);
//        nodeService.setProperty(con211, PredicateModel.PROP_REQUIRED_KIND, kind12);
//
//        // test documents
//        testNode1 = createNode(rootNode, ContentModel.TYPE_CONTENT, ContentModel.ASSOC_CHILDREN, "testNode1");
//        testNode2 = createNode(rootNode, ContentModel.TYPE_CONTENT, ContentModel.ASSOC_CHILDREN, "testNode2");
//        testNode3 = createNode(rootNode, ContentModel.TYPE_CONTENT, ContentModel.ASSOC_CHILDREN, "testNode3");
//        nodeService.addProperties(testNode1, typeAndKind(type1, kind11));
//        nodeService.addProperties(testNode2, typeAndKind(type1, kind12));
//        nodeService.addProperties(testNode3, typeAndKind(type2, kind21));
//
//        // test case configuration
//        caseNode1 = createNode(rootNode, ContentModel.TYPE_FOLDER, ContentModel.ASSOC_CHILDREN, "caseNode1");
//        nodeService.addAspect(caseNode1, ICaseModel.ASPECT_CASE, null);
//        nodeService.addAspect(caseNode1, RequirementModel.ASPECT_HAS_COMPLETENESS_LEVELS, null);
//        nodeService.createAssociation(caseNode1, level1, RequirementModel.ASSOC_COMPLETENESS_LEVELS);
//        nodeService.createAssociation(caseNode1, level2, RequirementModel.ASSOC_COMPLETENESS_LEVELS);
//
//        testStage = createStage(caseNode1, "testStage");
//        nodeService.createAssociation(testStage, level1, StagesModel.ASSOC_START_COMPLETENESS_LEVELS_RESTRICTION);
//        nodeService.createAssociation(testStage, level2, StagesModel.ASSOC_START_COMPLETENESS_LEVELS_RESTRICTION);
//        nodeService.createAssociation(testStage, level1, StagesModel.ASSOC_STOP_COMPLETENESS_LEVELS_RESTRICTION);
//        nodeService.createAssociation(testStage, level2, StagesModel.ASSOC_STOP_COMPLETENESS_LEVELS_RESTRICTION);
    }

    private static Map<QName, Serializable> typeAndKind(NodeRef type, NodeRef kind) {
        Map<QName, Serializable> map = new HashMap<>(2);
        map.put(ClassificationModel.PROP_DOCUMENT_TYPE, type);
        map.put(ClassificationModel.PROP_DOCUMENT_KIND, kind);
        return map;
    }

    private NodeRef createNode(NodeRef rootNode, QName type, QName assocType, String name) {
        return nodeService.createNode(
                rootNode,
                assocType,
                QName.createQName(ICaseModel.NAMESPACE, name),
                type).getChildRef();
    }

    private NodeRef createStage(NodeRef parent, String stageNodeName) {
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

    @After
    public void tearDown() throws Exception {
//        nodeService.addAspect(rootNode, ContentModel.ASPECT_TEMPORARY, null);
//        nodeService.deleteNode(rootNode);
//
//        AuthenticationUtil.clearCurrentSecurityContext();
    }

    @Ignore
    @Test(expected = AlfrescoRuntimeException.class)
    public void testStageStartCaseCompletenessNegative() {
        //stageService.startStage(testStage);
    }

    @Test
    public void tesStageStartCaseCompletenessPositive() {
        /*caseElementService.addElement(testNode1, caseNode1, TEST_CONFIG_NAME);
        caseElementService.addElement(testNode2, caseNode1, TEST_CONFIG_NAME);
        caseElementService.addElement(testNode3, caseNode1, TEST_CONFIG_NAME);
        nodeService.addProperties(testNode2, typeAndKind(type1, null));
        stageService.startStage(testStage);*/
    }

    @Ignore
    @Test(expected = AlfrescoRuntimeException.class)
    public void testStageStopCaseCompletenessNegative() {
        /*caseElementService.addElement(testNode1, caseNode1, TEST_CONFIG_NAME);
        caseElementService.addElement(testNode2, caseNode1, TEST_CONFIG_NAME);
        stageService.stopStage(testStage);*/
    }

    @Test
    public void tesStageStopCaseCompletenessPositive() {
        /*caseElementService.addElement(testNode1, caseNode1, TEST_CONFIG_NAME);
        caseElementService.addElement(testNode2, caseNode1, TEST_CONFIG_NAME);
        caseElementService.addElement(testNode3, caseNode1, TEST_CONFIG_NAME);
        nodeService.addProperties(testNode2, typeAndKind(type1, null));
        stageService.stopStage(testStage);*/
    }
}
