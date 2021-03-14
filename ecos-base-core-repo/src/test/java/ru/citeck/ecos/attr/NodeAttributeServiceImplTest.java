package ru.citeck.ecos.attr;

import com.tradeshift.test.remote.Remote;
import com.tradeshift.test.remote.RemoteTestRunner;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.dictionary.DataTypeDefinition;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.site.SiteVisibility;
import org.alfresco.service.namespace.QName;
import org.alfresco.util.test.junitrules.ApplicationContextInit;
import org.alfresco.util.test.junitrules.TemporaryNodes;
import org.alfresco.util.test.junitrules.TemporarySites;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import ru.citeck.ecos.model.AttributeModel;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

@Ignore
@RunWith(RemoteTestRunner.class)
@Remote(runnerClass = SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:alfresco/application-context.xml")
public class NodeAttributeServiceImplTest {

    @Autowired
    @Qualifier("nodeAttributeService")
    private NodeAttributeService nodeAttributeService;
    @Autowired
    @Qualifier("NodeService")
    private NodeService nodeService;
    @Autowired
    @Qualifier("retryingTransactionHelper")
    private RetryingTransactionHelper transactionHelper;
    @Autowired
    @Qualifier("contentService")
    private ContentService contentService;

    @ClassRule
    public static ApplicationContextInit APP_CONTEXT_INIT = new ApplicationContextInit();
    @Rule
    public TemporarySites temporarySites = new TemporarySites(APP_CONTEXT_INIT);
    @Rule
    public TemporaryNodes tempNodes = new TemporaryNodes(APP_CONTEXT_INIT);

    private TemporarySites.TestSiteAndMemberInfo testSiteAndMemberInfo;

    private NodeRef testNode;

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
        tempNodes.addNodeRef(testNode);
    }

    @Test
    public void testWiring() {
        assertNotNull(nodeAttributeService);
    }

    @Test
    public void registerAttributeProvider() {
        assertEquals(AttributeModel.TYPE_PROPERTY, nodeAttributeService.getAttributeType(ContentModel.PROP_NAME));

        assertEquals(AttributeModel.TYPE_CHILD_ASSOCIATION, nodeAttributeService.getAttributeType(ContentModel.ASSOC_CONTAINS));
        assertEquals(AttributeModel.TYPE_TARGET_ASSOCIATION, nodeAttributeService.getAttributeType(ContentModel.ASSOC_REFERENCES));
        assertEquals(AttributeModel.TYPE_VIRTUAL, nodeAttributeService.getAttributeType(AttributeModel.ATTR_TYPES));
        assertEquals(AttributeModel.TYPE_VIRTUAL, nodeAttributeService.getAttributeType(AttributeModel.ATTR_ASPECTS));
        assertEquals(AttributeModel.TYPE_VIRTUAL, nodeAttributeService.getAttributeType(AttributeModel.ATTR_NODEREF));
        assertEquals(AttributeModel.TYPE_VIRTUAL, nodeAttributeService.getAttributeType(AttributeModel.ATTR_PARENT));
        assertEquals(AttributeModel.TYPE_VIRTUAL, nodeAttributeService.getAttributeType(AttributeModel.ATTR_PARENT_ASSOC));
    }

    @Test
    public void getDefinedAttributeNamesContent() {
        Set<QName> definedAttributeNames = nodeAttributeService.getDefinedAttributeNames(ContentModel.TYPE_CONTENT, true);
        assertTrue(definedAttributeNames.contains(ContentModel.TYPE_CONTENT));
        assertTrue(definedAttributeNames.contains(AttributeModel.ATTR_ASPECTS));
        assertEquals(definedAttributeNames, nodeAttributeService.getDefinedAttributeNames(testNode));
    }

    @Test
    public void getDefinedAttributeNamesContentOnly() {
        Set<QName> definedAttributeNames = nodeAttributeService.getDefinedAttributeNames(ContentModel.TYPE_CONTENT, false);
        assertTrue(definedAttributeNames.contains(ContentModel.PROP_CONTENT));
        assertFalse(definedAttributeNames.contains(AttributeModel.ATTR_ASPECTS));
        assertFalse(nodeAttributeService.getDefinedAttributeNames(ContentModel.TYPE_DICTIONARY_MODEL, false)
                .contains(ContentModel.PROP_CONTENT));
    }

    @Test
    public void getDefinedAttributeNamesJustCreated() {
        final String name = this.getClass().getSimpleName() + "_test.txt";
        NodeRef docLibNodeRef = testSiteAndMemberInfo.doclib;
        final NodeRef childRef = nodeService.createNode(docLibNodeRef, ContentModel.ASSOC_CONTAINS, ContentModel.ASSOC_CONTAINS,
                ContentModel.TYPE_CONTENT).getChildRef();
        tempNodes.addNodeRef(childRef);

        Set<QName> definedAttributeNames = nodeAttributeService.getPersistedAttributeNames(childRef);
        assertFalse(definedAttributeNames.contains(ContentModel.PROP_CONTENT));


        transactionHelper.doInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<Void>() {
            public Void execute() throws Throwable {
                nodeAttributeService.setAttribute(childRef, ContentModel.PROP_NAME, name);

                ContentWriter contentWriter = contentService.getWriter(childRef, ContentModel.PROP_CONTENT, true);
                contentWriter.setMimetype(MimetypeMap.MIMETYPE_TEXT_PLAIN);
                contentWriter.setEncoding("UTF-8");
                contentWriter.putContent("test text");
                return null;
            }
        });

        Set<QName> definedAttributeNames2 = nodeAttributeService.getPersistedAttributeNames(childRef);
        assertTrue(definedAttributeNames2.contains(ContentModel.PROP_CONTENT));

        assertEquals(name, nodeAttributeService.getAttribute(childRef, ContentModel.PROP_NAME));
    }

    @Test
    public void getAttributeType() {
        assertEquals(AttributeModel.TYPE_PROPERTY, nodeAttributeService.getAttributeType(ContentModel.PROP_NAME));
        assertEquals(DataTypeDefinition.TEXT, nodeAttributeService.getAttributeSubtype(ContentModel.PROP_NAME));
        assertEquals(String.class, nodeAttributeService.getAttributeValueType(ContentModel.PROP_NAME));
    }

    @Test
    public void persistAttributes() {
        final String name = this.getClass().getSimpleName() + ".persistAttributes";
        transactionHelper.doInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<Void>() {
            public Void execute() throws Throwable {
                Map<QName, Object> attributes = nodeAttributeService.getAttributes(testNode);
                attributes.put(ContentModel.PROP_NAME, name);
                nodeAttributeService.persistAttributes(attributes);
                return null;
            }
        });
        assertEquals(name, nodeAttributeService.getAttribute(testNode, ContentModel.PROP_NAME));
    }
}
