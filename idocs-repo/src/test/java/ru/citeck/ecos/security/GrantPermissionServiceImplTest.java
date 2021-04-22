package ru.citeck.ecos.security;


import static org.junit.Assert.*;

import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.CopyService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.security.AccessStatus;
import org.alfresco.service.cmr.security.OwnableService;
import org.alfresco.service.cmr.security.PermissionService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.model.Repository;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.junit.*;
import org.springframework.context.ApplicationContext;

//import ru.citeck.ecos.test.ApplicationContextHelper;
@Ignore
public class GrantPermissionServiceImplTest
{
	private static final String SYSTEM = AuthenticationUtil.SYSTEM_USER_NAME;
	private static final String USER1 = "abeecher";
	private static final String USER2 = "mjackson";

	private static final String PROVIDER1 = "provider1";
	private static final String PROVIDER2 = "provider2";

	private ServiceRegistry serviceRegistry;
	private NodeService nodeService;
	private CopyService copyService;
	private DictionaryService dictionaryService;
	private PermissionService permissionService;
	private OwnableService ownableService;
	private Repository repositoryHelper;
	private NodeRef testRoot;
	private NodeRef user1Home;
	private NodeRef user2Home;

	private AssociationWalker walkNowhere;
	private AssociationWalker walkSecondaryChildren;
	private AssociationWalker walkTargetAssocs;

	private GrantPermissionServiceImpl grantPermissionService;

	private AssociationWalker createWalker(Boolean propagatePrimaryChildAssociations,
			Boolean propagateSecondaryChildAssociations,
			Boolean propagateTargetAssociations) {
		AssociationWalker walker = new AssociationWalker();
		walker.setNodeService(nodeService);
		walker.setPropagatePrimaryChildAssociations(propagatePrimaryChildAssociations);
		walker.setPropagateSecondaryChildAssociations(propagateSecondaryChildAssociations);
		walker.setPropagateTargetAssociations(propagateTargetAssociations);
		return walker;
	}

	private static void runAs(String userName) {
//		AuthenticationUtil.setRunAsUser(userName);
		AuthenticationUtil.setFullyAuthenticatedUser(userName);
	}

	@Before
	public void setUp() throws Exception {

//		ApplicationContext context = ApplicationContextHelper.getApplicationContext();
//
//		serviceRegistry = context.getBean("ServiceRegistry", ServiceRegistry.class);
//		nodeService = serviceRegistry.getNodeService();
//		copyService = serviceRegistry.getCopyService();
//		dictionaryService = serviceRegistry.getDictionaryService();
//		permissionService = serviceRegistry.getPermissionService();
//		ownableService = serviceRegistry.getOwnableService();
//		repositoryHelper = context.getBean("repositoryHelper", Repository.class);
//
//		// initialize association walkers
//		walkNowhere = createWalker(false, false, false);
//		walkSecondaryChildren = createWalker(false, true, false);
//		walkTargetAssocs = createWalker(false, false, true);
//
//		// create test root folder
//		testRoot = createNode(SYSTEM, repositoryHelper.getCompanyHome(), ContentModel.TYPE_FOLDER, GrantPermissionServiceImplTest.class.getSimpleName());
//		permissionService.setInheritParentPermissions(testRoot, false);
//
//		grantPermissionService = new GrantPermissionServiceImpl();
//		grantPermissionService.setNodeService(nodeService);
//		grantPermissionService.setDictionaryService(dictionaryService);
//		grantPermissionService.setPermissionService(permissionService);
//
//		user1Home = createNode(SYSTEM, testRoot, ContentModel.TYPE_FOLDER, USER1);
//		ownableService.setOwner(user1Home, USER1);
//
//		user2Home = createNode(SYSTEM, testRoot, ContentModel.TYPE_FOLDER, USER2);
//		ownableService.setOwner(user2Home, USER2);
	}

	private NodeRef createNode(String user, NodeRef parent, QName type, String name) {
		runAs(user);
		ChildAssociationRef testNodeRef = nodeService.createNode(parent, ContentModel.ASSOC_CONTAINS, QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, QName.createValidLocalName(name)), type);
		return testNodeRef.getChildRef();
	}

	private NodeRef copyNode(String user, NodeRef source, NodeRef parent, String name) {
		runAs(user);
		return copyService.copy(source, parent, ContentModel.ASSOC_CONTAINS, QName.createQName(NamespaceService.CONTENT_MODEL_1_0_URI, name));
	}

	private void grant(NodeRef testNode, String authority, String permission, String provider) {
		runAs(SYSTEM);
		grantPermissionService.grantPermission(testNode, authority, permission, provider);
	}

	private void revoke(NodeRef testNode, String authority, String permission, String provider) {
		runAs(SYSTEM);
		grantPermissionService.revokePermission(testNode, authority, permission, provider);
	}

	private void assertAllowed(NodeRef nodeRef, String username, String permission) {
		runAs(username);
		AccessStatus status = permissionService.hasPermission(nodeRef, permission);
		assertEquals(AccessStatus.ALLOWED, status);
	}

	private void assertDenied(NodeRef nodeRef, String username, String permission) {
		runAs(username);
		AccessStatus status = permissionService.hasPermission(nodeRef, permission);
		assertEquals(AccessStatus.DENIED, status);
	}

	@Test
	public void simpleTest() {
//		grantPermissionService.setWalker(walkNowhere);
//
//		NodeRef testNode = createNode(SYSTEM, testRoot, ContentModel.TYPE_FOLDER, "test");
//
//		grant(testNode, USER1, PermissionService.READ, PROVIDER1);
//		assertAllowed(testNode, USER1, PermissionService.READ);
//		assertDenied(testNode, USER1, PermissionService.WRITE);
//
//		revoke(testNode, USER1, PermissionService.READ, PROVIDER1);
//		assertDenied(testNode, USER1, PermissionService.READ);

	}

	@Test
	public void copyGrantedObjectTest() {
//		grantPermissionService.setWalker(walkNowhere);
//
//		// try to force exception Can not delete from this acl in a node context SHARED
//
//		NodeRef testNode = createNode(SYSTEM, testRoot, ContentModel.TYPE_CONTENT, "test");
//		assertDenied(testNode, USER1, PermissionService.READ);
//		assertDenied(testNode, USER2, PermissionService.READ);
//
//		grant(testNode, USER1, PermissionService.READ, PROVIDER1);
//		assertAllowed(testNode, USER1, PermissionService.READ);
//		assertDenied(testNode, USER2, PermissionService.READ);
//
//		grant(testNode, USER2, PermissionService.READ, PROVIDER1);
//		assertAllowed(testNode, USER1, PermissionService.READ);
//		assertAllowed(testNode, USER2, PermissionService.READ);
//
//		grant(testNode, USER2, PermissionService.READ_PERMISSIONS, PROVIDER2);
//		assertDenied(testNode, USER1, PermissionService.READ_PERMISSIONS);
//		assertAllowed(testNode, USER2, PermissionService.READ_PERMISSIONS);
//
//		NodeRef user1Copy = copyNode(USER1, testNode, user1Home, "test");
//		assertAllowed(user1Copy, USER1, PermissionService.READ);
//		assertDenied(user1Copy, USER2, PermissionService.READ);
//
//		NodeRef user2Copy = copyNode(USER2, testNode, user2Home, "test");
//		assertDenied(user2Copy, USER1, PermissionService.READ);
//		assertAllowed(user2Copy, USER2, PermissionService.READ);



	}

	@After
	public void tearDown() throws Exception {
//		if(testRoot != null) {
//			runAs(SYSTEM);
//			nodeService.deleteNode(testRoot);
//		}
	}

}
