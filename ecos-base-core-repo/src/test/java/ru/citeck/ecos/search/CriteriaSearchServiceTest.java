package ru.citeck.ecos.search;

import com.tradeshift.test.remote.Remote;
import com.tradeshift.test.remote.RemoteTestRunner;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.search.SearchService;
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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Ignore
@RunWith(RemoteTestRunner.class)
@Remote(runnerClass = SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:alfresco/application-context.xml")
public class CriteriaSearchServiceTest {
    @Autowired
    @Qualifier("criteriaSearchService")
    private CriteriaSearchService criteriaSearchService;
    @Autowired
    @Qualifier("searchCriteriaFactory")
    private SearchCriteriaFactory criteriaFactory;
    @Autowired
    @Qualifier("NodeService")
    private NodeService nodeService;
    @Autowired
    @Qualifier("retryingTransactionHelper")
    private RetryingTransactionHelper transactionHelper;

    @ClassRule
    public static ApplicationContextInit APP_CONTEXT_INIT = new ApplicationContextInit();
    @Rule
    public TemporarySites temporarySites = new TemporarySites(APP_CONTEXT_INIT);
    @Rule
    public TemporaryNodes tempNodes = new TemporaryNodes(APP_CONTEXT_INIT);

    private TemporarySites.TestSiteAndMemberInfo testSiteAndMemberInfo;

    private NodeRef testNode;
    private String testNodeName;

    @Before
    public void setUp() throws Exception {
        String siteShortName = this.getClass().getSimpleName() + "TestSite" + System.currentTimeMillis();
        testNodeName = this.getClass().getSimpleName() + "-TestNode-" + System.currentTimeMillis();

        testSiteAndMemberInfo = temporarySites.createTestSiteWithUserPerRole(siteShortName, "sitePreset",
                SiteVisibility.PUBLIC, AuthenticationUtil.getAdminUserName());

        AuthenticationUtil.setFullyAuthenticatedUser(AuthenticationUtil.getAdminUserName());
        testNode = transactionHelper.doInTransaction(new RetryingTransactionHelper.RetryingTransactionCallback<NodeRef>() {
            public NodeRef execute() throws Throwable {
                final NodeRef docLibNodeRef = testSiteAndMemberInfo.doclib;
                Map<QName, Serializable> properties = new HashMap<>(1);
                properties.put(ContentModel.PROP_NAME, testNodeName);

                return nodeService.createNode(docLibNodeRef, ContentModel.ASSOC_CONTAINS, ContentModel.ASSOC_CONTAINS,
                        ContentModel.TYPE_CONTENT, properties).getChildRef();
            }
        });
        tempNodes.addNodeRef(testNode);
    }

    @Test
    public void testWiring() {
        assertNotNull(criteriaSearchService);
    }

    @Test
    public void search1() {
        SearchCriteria searchCriteria = criteriaFactory.createSearchCriteria();
        searchCriteria.addCriteriaTriplet(FieldType.TYPE, SearchPredicate.TYPE_EQUALS, ContentModel.TYPE_CONTENT);
        CriteriaSearchResults results = criteriaSearchService.query(searchCriteria, SearchService.LANGUAGE_LUCENE);
//        assertEquals(new Long(0), results.getTotalCount());
        assertEquals(searchCriteria, results.getCriteria());
        assertTrue(results.getResults().size() > 1);
        assertEquals(false, results.hasMore());
    }

    @Test
    public void search2() {
        SearchCriteria searchCriteria = criteriaFactory.createSearchCriteria();
        searchCriteria.addCriteriaTriplet(FieldType.TYPE, SearchPredicate.TYPE_EQUALS, ContentModel.TYPE_CONTENT);
        searchCriteria.addCriteriaTriplet(ContentModel.PROP_NAME, SearchPredicate.STRING_EQUALS, testNodeName);
        CriteriaSearchResults results = criteriaSearchService.query(searchCriteria, SearchService.LANGUAGE_LUCENE);
        assertEquals(searchCriteria, results.getCriteria());
        assertEquals(1, results.getResults().size());
        assertEquals(false, results.hasMore());
    }
}
