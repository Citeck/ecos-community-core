package ru.citeck.ecos.node;

import com.tradeshift.test.remote.Remote;
import com.tradeshift.test.remote.RemoteTestRunner;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.repo.workflow.WorkflowModel;
import org.alfresco.repo.workflow.activiti.ActivitiConstants;
import org.alfresco.service.cmr.repository.*;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.cmr.site.SiteVisibility;
import org.alfresco.service.cmr.workflow.*;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.alfresco.util.GUID;
import org.alfresco.util.test.junitrules.ApplicationContextInit;
import org.alfresco.util.test.junitrules.TemporaryNodes;
import org.alfresco.util.test.junitrules.TemporarySites;
import org.alfresco.util.test.junitrules.TemporarySites.TestSiteAndMemberInfo;
import org.alfresco.util.test.junitrules.WellKnownNodes;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import ru.citeck.ecos.utils.RepoUtils;

import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@Ignore
@RunWith(RemoteTestRunner.class)
@Remote(runnerClass = SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:alfresco/application-context.xml")
public class NodeInfoFactoryImplTest {

    private static Log logger = LogFactory.getLog(NodeInfoFactoryImplTest.class);
    @Autowired
    @Qualifier("NodeService")
    private NodeService nodeService;
//    private Repository repositoryHelper;
    @Autowired
    @Qualifier("contentService")
    private ContentService contentService;
    @Autowired
    @Qualifier("NodeInfoFactory")
    private NodeInfoFactory nodeInfoFactory;
    @Autowired
    @Qualifier("retryingTransactionHelper")
    private RetryingTransactionHelper transactionHelper;
    @Autowired
    @Qualifier("WorkflowService")
    private WorkflowService workflowService;
    @Autowired
    @Qualifier("personService")
    private PersonService personService;
    @Autowired
    @Qualifier("NamespaceService")
    private NamespaceService namespaceService;
    private TestSiteAndMemberInfo testSiteAndMemberInfo;

    private NodeRef testNode;
    private static NodeRef notExistNodeRef;
    private NodeRef folder;
    private String folderName;

    @ClassRule
    public static ApplicationContextInit APP_CONTEXT_INIT = new ApplicationContextInit();
    @Rule
    public TemporarySites temporarySites = new TemporarySites(APP_CONTEXT_INIT);
    @Rule
    public WellKnownNodes wellKnownNodes = new WellKnownNodes(APP_CONTEXT_INIT);
    @Rule
    public TemporaryNodes tempNodes = new TemporaryNodes(APP_CONTEXT_INIT);

    @BeforeClass
    public static void setUpClass() throws Exception {
        notExistNodeRef = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE,
                NodeInfoFactoryImplTest.class.getSimpleName() + "-not-exist-node-ref");
    }

    @Before
    public void setUp() throws Exception {
        final String siteShortName = this.getClass().getSimpleName() + "TestSite" + System.currentTimeMillis();

        testSiteAndMemberInfo = temporarySites.createTestSiteWithUserPerRole(siteShortName, "sitePreset",
                SiteVisibility.PUBLIC, AuthenticationUtil.getAdminUserName());

        AuthenticationUtil.setFullyAuthenticatedUser(AuthenticationUtil.getAdminUserName());
        testNode = transactionHelper.doInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<NodeRef>() {
            public NodeRef execute() throws Throwable {
                final NodeRef docLibNodeRef = testSiteAndMemberInfo.doclib;

                return nodeService.createNode(docLibNodeRef, ContentModel.ASSOC_CONTAINS, ContentModel.ASSOC_CONTAINS,
                        ContentModel.TYPE_CONTENT).getChildRef();
            }
        });
        NodeRef rootNode = nodeService.getRootNode(
                new StoreRef(StoreRef.PROTOCOL_WORKSPACE, "SpacesStore"));
        String guid = GUID.generate();
        Map<QName, Serializable> folderProps = new HashMap<>(1);
        folderName = "testFolder" + guid;
        folderProps.put(ContentModel.PROP_NAME, this.folderName);
        this.folder = nodeService.createNode(
                rootNode,
                ContentModel.ASSOC_CHILDREN,
                QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, "testFolder" + guid),
                ContentModel.TYPE_FOLDER,
                folderProps).getChildRef();
    }


    @Test
    public void testCreateNodeInfo() {
        NodeInfo nodeInfo = nodeInfoFactory.createNodeInfo();
        assertNotNull(nodeInfo);
        assertEquals("Properties are not empty", 0, nodeInfo.getProperties().size());
        assertEquals("TargetAssocs are not empty", 0, nodeInfo.getTargetAssocs().size());
        assertNull("SourceAssocs are not null", nodeInfo.getSourceAssocs());
        assertEquals("ChildAssocs are not empty", 0, nodeInfo.getChildAssocs().size());
    }

    @Test
    public void testCreateNodeInfoByNullNodeRef() {
        NodeInfo nodeInfo = nodeInfoFactory.createNodeInfo(notExistNodeRef);
        assertNull(nodeInfo);
    }

    @Test
    public void testCreateNodeInfoByNodeRef() {
        NodeRef contentRef = addTempScript(NodeInfoFactoryImplTest.class.getSimpleName() + "_" + System.currentTimeMillis() + ".js",
                "document.properties.name = \"Changed\" + \"_\" + document.properties.name;\ndocument.save();");

        NodeInfo nodeInfo = nodeInfoFactory.createNodeInfo(contentRef);
        assertNotNull(nodeInfo);
        assertEquals("TargetAssocs are not equals",
                nodeService.getTargetAssocs(contentRef, RegexQNamePattern.MATCH_ALL).size(),
                nodeInfo.getTargetAssocs().size());
        assertNull("Size of the SourceAssocs are not null", nodeInfo.getSourceAssocs());
        assertEquals("Size of the ChildAssocs are not equals",
                nodeService.getChildAssocs(contentRef).size(),
                nodeInfo.getChildAssocs().size());

        assertEquals("NodeRef is not equals", contentRef, nodeInfo.getNodeRef());
        assertEquals("Type is not equals", nodeService.getType(contentRef), nodeInfo.getType());
        assertEquals("Aspects are not equals",
                new ArrayList<>(nodeService.getAspects(contentRef)),
                nodeInfo.getAspects());
        assertEquals("Parent is not equals", nodeService.getPrimaryParent(contentRef).getParentRef(), nodeInfo.getParent());
        assertEquals("Properties are not equals", nodeService.getProperties(contentRef), nodeInfo.getProperties());
    }

    private NodeRef addTempScript(final String scriptFileName, final String javaScript) {
        AuthenticationUtil.setFullyAuthenticatedUser(AuthenticationUtil.getAdminUserName());
        logger.debug("start addTempScript");
        return transactionHelper.doInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<NodeRef>() {
            public NodeRef execute() throws Throwable {
                NodeRef companyHomeRef = wellKnownNodes.getCompanyHome();
                logger.debug("companyHomeRef: " + companyHomeRef);

                // Create the script node reference
                NodeRef script = nodeService.createNode(companyHomeRef, ContentModel.ASSOC_CONTAINS,
                        QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, scriptFileName),
                        ContentModel.TYPE_CONTENT).getChildRef();

                nodeService.setProperty(script, ContentModel.PROP_NAME, scriptFileName);

                ContentWriter contentWriter = contentService.getWriter(script, ContentModel.PROP_CONTENT, true);
                contentWriter.setMimetype(MimetypeMap.MIMETYPE_JAVASCRIPT);
                contentWriter.setEncoding("UTF-8");
                contentWriter.putContent(javaScript);

                tempNodes.addNodeRef(script);
                return script;
            }
        });
    }

    @Test
    public void testCreateNodeInfoByWorkflowTask() {
        WorkflowTask workflowTask = getWorkflowTask("activiti$activitiReview");

        NodeInfo nodeInfo = nodeInfoFactory.createNodeInfo(workflowTask);
        assertNotNull(nodeInfo);

        assertEquals("Type is not equals", QName.createQName(workflowTask.getName(), namespaceService), nodeInfo.getType());
        assertEquals("Size of properties is not equals", workflowTask.getProperties().size(),
                nodeInfo.getProperties().size() + nodeInfo.getTargetAssocs().size());
    }

    private WorkflowTask getWorkflowTask(String definitionName) {
        WorkflowDefinition reviewDef = workflowService.getDefinitionByName(definitionName);
        Map<QName, Serializable> properties = new HashMap<QName, Serializable>();
        NodeRef reviewer = personService.getPerson(AuthenticationUtil.getAdminUserName());
        properties.put(WorkflowModel.ASSOC_ASSIGNEE, reviewer);
        properties.put(WorkflowModel.ASSOC_PACKAGE, folder);
        WorkflowPath path = workflowService.startWorkflow(reviewDef.getId(), properties);
        WorkflowTask task = getTaskForPath(path);
        String startTaskId = reviewDef.getStartTaskDefinition().getId();
        if (startTaskId.equals(task.getDefinition().getId())) {
            workflowService.endTask(task.getId(), null);
            task = getTaskForPath(path);
        }
        return task;
    }

    private WorkflowTask getTaskForPath(WorkflowPath path) {
        List<WorkflowTask> tasks = workflowService.getTasksForWorkflowPath(path.getId());
        assertNotNull(tasks);
        assertTrue(tasks.size() > 0);
        WorkflowTask task = tasks.get(0);
        return task;
    }

    @Test
    public void testCreateNodeInfoByWorkflowInstance() {
        WorkflowDefinition definition = deployDefinition("activiti/testTransaction.bpmn20.xml");

        WorkflowPath path = workflowService.startWorkflow(definition.getId(), null);
        assertNotNull(path);
        assertTrue(path.isActive());
        assertNotNull(path.getNode());
        WorkflowInstance instance = path.getInstance();
        assertNotNull(instance);
        assertEquals(definition.getId(), instance.getDefinition().getId());

        NodeInfo nodeInfo = nodeInfoFactory.createNodeInfo(instance);

        assertEquals("Property is not equals",
                instance.getId(),
                nodeInfo.getProperties().get(WorkflowModel.PROP_WORKFLOW_INSTANCE_ID));
        assertEquals("Property is not equals",
                instance.getDefinition().getId(),
                nodeInfo.getProperties().get(WorkflowModel.PROP_WORKFLOW_DEFINITION_ID));
        assertEquals("Property is not equals",
                instance.getDescription(),
                nodeInfo.getProperties().get(WorkflowModel.PROP_WORKFLOW_DESCRIPTION));
        assertEquals("Property is not equals",
                instance.getStartDate(),
                nodeInfo.getProperties().get(WorkflowModel.PROP_START_DATE));
        assertEquals("Property is not equals",
                instance.getEndDate(),
                nodeInfo.getProperties().get(QName.createQName(NamespaceService.BPM_MODEL_1_0_URI, "endDate")));
        assertEquals("Property is not equals",
                instance.getDueDate(),
                nodeInfo.getProperties().get(WorkflowModel.PROP_DUE_DATE));
        assertEquals("Property is not equals",
                String.valueOf(instance.getInitiator()),
                nodeInfo.getProperties().get(QName.createQName(NamespaceService.BPM_MODEL_1_0_URI, "initiator")));
        assertEquals("Property is not equals",
                instance.getPriority(),
                nodeInfo.getProperties().get(WorkflowModel.PROP_WORKFLOW_PRIORITY));
        assertEquals("Property is not equals",
                String.valueOf(instance.getWorkflowPackage()),
                nodeInfo.getProperties().get(WorkflowModel.ASPECT_WORKFLOW_PACKAGE));
    }

    private WorkflowDefinition deployDefinition(String resource) {
        InputStream input = getInputStream(resource);
        WorkflowDeployment deployment = workflowService.deployDefinition(ActivitiConstants.ENGINE_ID, input,
                MimetypeMap.MIMETYPE_XML);
        WorkflowDefinition definition = deployment.getDefinition();
        return definition;
    }

    private InputStream getInputStream(String resource) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return classLoader.getResourceAsStream(resource);
    }

    @Test
    public void testCreateNodeInfoByAttributes() {
        NodeRef reviewer = personService.getPerson(AuthenticationUtil.getAdminUserName());
        Map<QName, Object> attributes = new HashMap();
        attributes.put(WorkflowModel.PROP_WORKFLOW_PRIORITY, 2);
        attributes.put(WorkflowModel.ASSOC_ASSIGNEE, reviewer);

        NodeInfo nodeInfo = nodeInfoFactory.createNodeInfo(attributes);
        assertEquals("Size property is not equals", 1, nodeInfo.getProperties().size());
        assertEquals("Property is not equals", 2, nodeInfo.getProperties().get(WorkflowModel.PROP_WORKFLOW_PRIORITY));
        assertEquals("Size targetAssoc is not equals", 1, nodeInfo.getTargetAssocs().size());
        assertEquals("TargetAssoc is not equals", RepoUtils.anyToNodeRefs(reviewer), nodeInfo.getTargetAssocs().get(WorkflowModel.ASSOC_ASSIGNEE));

        Map<QName, Object> attributes2 = new HashMap();
        attributes2.put(WorkflowModel.ASSOC_PACKAGE, folder);
        nodeInfoFactory.setAttributes(nodeInfo, attributes2);
        assertEquals("Size targetAssoc is not equals", 2, nodeInfo.getTargetAssocs().size());
        assertEquals("TargetAssoc is not equals", RepoUtils.anyToNodeRefs(folder), nodeInfo.getTargetAssocs().get(WorkflowModel.ASSOC_PACKAGE));
    }

    @Test
    public void testCreateNodeInfoPersist() {
        NodeInfo nodeInfo = nodeInfoFactory.createNodeInfo(testNode);
        Map<QName, Object> attributes = new HashMap();
        String name = this.getClass().getSimpleName() + ".testCreateNodeInfoPersist." + System.currentTimeMillis();
        attributes.put(ContentModel.PROP_NAME, name);
        attributes.put(ContentModel.PROP_CREATOR, testSiteAndMemberInfo.siteConsumer);

        nodeInfoFactory.setAttributes(nodeInfo, attributes);
        String stampNodeInfoBeforePersist = String.valueOf(nodeInfo);

        Map<QName, Serializable> propertiesBeforePersist = nodeService.getProperties(testNode);
        assertNotEquals(name, propertiesBeforePersist.get(ContentModel.PROP_NAME));
        assertEquals(AuthenticationUtil.getAdminUserName(), propertiesBeforePersist.get(ContentModel.PROP_CREATOR));

        nodeInfoFactory.persist(testNode, nodeInfo);

        assertEquals(stampNodeInfoBeforePersist, String.valueOf(nodeInfo));
        Map<QName, Serializable> propertiesAfterPersist = nodeService.getProperties(testNode);
        assertEquals(name, propertiesAfterPersist.get(ContentModel.PROP_NAME));
        assertNotEquals(testSiteAndMemberInfo.siteConsumer, propertiesAfterPersist.get(ContentModel.PROP_CREATOR));
    }
}
