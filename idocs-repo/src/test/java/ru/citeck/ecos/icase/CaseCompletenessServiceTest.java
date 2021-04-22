package ru.citeck.ecos.icase;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
//import ru.citeck.ecos.test.ApplicationContextHelper;

@Ignore
@RunWith(SpringJUnit4ClassRunner.class)
//@ContextConfiguration(loader=ApplicationContextHelper.class)
@Transactional
public class CaseCompletenessServiceTest {

    private static final String TEST_CONFIG_NAME = CaseConstants.ASSOCIATION_ELEMENTS;
    private ApplicationContext applicationContext;
    private NodeService nodeService;
    private CaseElementServiceImpl caseElementService;
    private CaseCompletenessServiceImpl caseCompletenessService;

    private NodeRef rootNode;
    private NodeRef caseNode1;
    private NodeRef testNode1, testNode2, testNode3;
    private NodeRef level1, level2;
    private NodeRef req11, req12, req21;
    private NodeRef type1, kind11, kind12, type2, kind21, kind22;
    private NodeRef testConfig;

    @Before
    public void setUp() throws Exception {

        // setup services
//        applicationContext = ApplicationContextHelper.getApplicationContext();
//        nodeService = applicationContext.getBean("nodeService", NodeService.class);
//        caseElementService = applicationContext.getBean("caseElementService", CaseElementServiceImpl.class);
//        caseCompletenessService = applicationContext.getBean("caseCompletenessService", CaseCompletenessServiceImpl.class);
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
//                ContentModel.ASSOC_CHILDREN, CaseCompletenessServiceTest.class.getSimpleName());
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

    @After
    public void tearDown() throws Exception {
//        nodeService.addAspect(rootNode, ContentModel.ASPECT_TEMPORARY, null);
//        nodeService.deleteNode(rootNode);
//
//        AuthenticationUtil.clearCurrentSecurityContext();
    }

    @Test
    public void testGetLevels() {

//        Set<NodeRef> allLevels = caseCompletenessService.getAllLevels(caseNode1);
//        assertTrue(allLevels != null);
//        assertEquals(createSet(level1, level2), allLevels);
//
//        Set<NodeRef> completedLevels = caseCompletenessService.getCompletedLevels(caseNode1);
//        assertEquals(createSet(level2), completedLevels);

    }

    @Test
    public void testAddDocuments() {

        // test addition
//        caseElementService.addElement(testNode1, caseNode1, TEST_CONFIG_NAME);
//        assertEquals(createSet(level2), caseCompletenessService.getCompletedLevels(caseNode1));
//
//        caseElementService.addElement(testNode2, caseNode1, TEST_CONFIG_NAME);
//        assertEquals(createSet(), caseCompletenessService.getCompletedLevels(caseNode1));
//
//        caseElementService.addElement(testNode3, caseNode1, TEST_CONFIG_NAME);
//        assertEquals(createSet(level1), caseCompletenessService.getCompletedLevels(caseNode1));
//
//        // test removal
//        caseElementService.removeElement(testNode1, caseNode1, TEST_CONFIG_NAME);
//        assertEquals(createSet(level1), caseCompletenessService.getCompletedLevels(caseNode1));
//
//        caseElementService.removeElement(testNode3, caseNode1, TEST_CONFIG_NAME);
//        assertEquals(createSet(), caseCompletenessService.getCompletedLevels(caseNode1));
//
//        caseElementService.removeElement(testNode2, caseNode1, TEST_CONFIG_NAME);
//        assertEquals(createSet(level2), caseCompletenessService.getCompletedLevels(caseNode1));
//
//        // test updates
//        caseElementService.addElement(testNode1, caseNode1, TEST_CONFIG_NAME);
//        caseElementService.addElement(testNode2, caseNode1, TEST_CONFIG_NAME);
//        caseElementService.addElement(testNode3, caseNode1, TEST_CONFIG_NAME);
//        assertEquals(createSet(level1), caseCompletenessService.getCompletedLevels(caseNode1));
//
//        nodeService.addProperties(testNode2, typeAndKind(type1, null));
//        assertEquals(createSet(level1, level2), caseCompletenessService.getCompletedLevels(caseNode1));
//
//        nodeService.addProperties(testNode1, typeAndKind(type2, kind21));
//        assertEquals(createSet(level2), caseCompletenessService.getCompletedLevels(caseNode1));

    }

    @SafeVarargs
    private static <E> Set<E> createSet(E... elements) {
        if(elements.length == 0) return Collections.emptySet();
        if(elements.length == 1) return Collections.singleton(elements[0]);
        Set<E> result = new HashSet<E>(elements.length);
        Collections.addAll(result, elements);
        return result;
    }


}
