package ru.citeck.ecos.workflow;

import org.activiti.engine.ProcessEngine;
import org.activiti.engine.TaskService;
import org.activiti.engine.delegate.DelegateTask;
import org.activiti.engine.impl.TaskQueryImpl;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.interceptor.CommandContextFactory;
import org.activiti.engine.impl.pvm.delegate.ActivityExecution;
import org.activiti.engine.impl.pvm.delegate.SignallableActivityBehavior;
import org.activiti.engine.task.Task;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.repo.workflow.activiti.ActivitiWorkflowEngine;
import org.alfresco.repo.workflow.activiti.ActivitiWorkflowManager;
import org.alfresco.repo.workflow.activiti.AlfrescoProcessEngineConfiguration;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.security.AuthorityService;
import org.alfresco.service.cmr.security.AuthorityType;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.QName;
import org.junit.*;
import org.springframework.context.ApplicationContext;
import ru.citeck.ecos.service.CiteckServices;
//import ru.citeck.ecos.test.ApplicationContextHelper;
import ru.citeck.ecos.workflow.tasks.AdvancedTaskQuery;

import javax.transaction.SystemException;
import javax.transaction.UserTransaction;
import java.io.Serializable;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Anton Fateev <anton.fateev@citeck.ru>
 */
@Ignore
public class AdvancedWorkflowServiceTest {

    public static final String TEST_ROLE = "GROUP_testCandidateRole";

    public static final String TEST_USER = "testCandidateUser";

    public static final String TEST_TASK1 = "testAssignedTask";

    public static final String TEST_TASK2 = "testUnassignedTask";

    private static final String WRONG_NUMBER = "Wrong number of tasks";

    private static final String TASK = "Task with id ";

    private static final String NOT_IN_OR_NULL = " not in actual task list or null";

    private static final String NOT_EQUALS = "Tasks are not equals ";

    private static final String ADD_USER = "additionalUser";

    private static final String TEST_TASK3 = "testSimpleTask";

    private static final String TEST_TASK4 = "testWithoutCandidateTask";

    private ActivitiWorkflowEngine engine;

    private TaskService taskService;

    private AuthorityService authorityService;

    private PersonService personService;

    private AdvancedWorkflowService advancedWorkflowService;

    private List<Task> expectedTasks;

    @Test
    public void testTasksByCandidateGroups() {
//        List<Task> expectedCandidateTasks = new ArrayList<Task>(3);
//        expectedCandidateTasks.add(getTaskById(TEST_TASK1, expectedTasks));
//        expectedCandidateTasks.add(getTaskById(TEST_TASK2, expectedTasks));
//        expectedCandidateTasks.add(getTaskById(TEST_TASK3, expectedTasks));
//
//        AdvancedTaskQuery taskQuery = new AdvancedTaskQuery()
//                .setCandidateGroups(Collections.singletonList(TEST_ROLE));
//        List<Task> actualTasks = advancedWorkflowService.testQueryTasks(taskQuery);
//        assertEquals(WRONG_NUMBER, expectedCandidateTasks.size(), actualTasks.size());
//
//        boolean found;
//        for (Task expectedTask : expectedCandidateTasks) {
//            found = false;
//            for (Task actualTask : actualTasks) {
//                if (expectedTask.getId().equals(actualTask.getId())) {
//                    found = true;
//                    assertEqualsTasks("Tasks with id " + expectedTask.getId() + " are not equals ", expectedTask, actualTask);
//                }
//            }
//            if (!found) {
//                Assert.fail(TASK + expectedTask.getId() + " not found");
//            }
//        }
    }

    @Test
    public void testTasksByAssignee() {
//        AdvancedTaskQuery taskQuery = new AdvancedTaskQuery()
//                .setAssignee(TEST_USER);
//        List<Task> actualTasks = advancedWorkflowService.testQueryTasks(taskQuery);
//        assertEquals(WRONG_NUMBER, 2, actualTasks.size());
//
//        Task expectedTask = getTaskById(TEST_TASK1, expectedTasks);
//        Task actualTask = getTaskById(TEST_TASK1, actualTasks);
//        assertNotNull(TASK + TEST_TASK1 + NOT_IN_OR_NULL, actualTask);
//        assertEqualsTasks(NOT_EQUALS, expectedTask, actualTask);
    }

    @Test
    public void testTasksByCandidateGroupsAndOwner() {
//        AdvancedTaskQuery taskQuery = new AdvancedTaskQuery()
//                .setCandidateGroups(Collections.singletonList(TEST_ROLE))
//                .setOwner(TEST_USER);
//        List<Task> actualTasks = advancedWorkflowService.testQueryTasks(taskQuery);
//        assertEquals(WRONG_NUMBER, 1, actualTasks.size());
//
//        Task expectedTask = getTaskById(TEST_TASK1, expectedTasks);
//        Task actualTask = getTaskById(TEST_TASK1, actualTasks);
//        assertNotNull(TASK + TEST_TASK1 + NOT_IN_OR_NULL, actualTask);
//        assertEqualsTasks(NOT_EQUALS, expectedTask, actualTask);
    }

    @Test
    public void testTasksByCandidateGroupAndOwnerList() {
//        AdvancedTaskQuery taskQuery = new AdvancedTaskQuery()
//                .setCandidateGroups(Collections.singletonList(TEST_ROLE))
//                .setOwners(Arrays.asList(TEST_USER, ADD_USER));
//        List<Task> actualTasks = advancedWorkflowService.testQueryTasks(taskQuery);
//        assertEquals(WRONG_NUMBER, 2, actualTasks.size());
    }

    @Test
    public void testTasksByAssigneeWithoutGroupCandidate() {
//        AdvancedTaskQuery taskQuery = new AdvancedTaskQuery()
//                .setAssignee(TEST_USER)
//                .withoutGroupCandidates();
//        List<Task> actualTasks = advancedWorkflowService.testQueryTasks(taskQuery);
//        assertEquals(WRONG_NUMBER, 1, actualTasks.size());
//
//        Task expectedTask = getTaskById(TEST_TASK4, expectedTasks);
//        Task actualTask = getTaskById(TEST_TASK4, actualTasks);
//        assertNotNull(TASK + TEST_TASK4 + NOT_IN_OR_NULL, actualTask);
//        assertEqualsTasks(NOT_EQUALS, expectedTask, actualTask);
    }

    @Test
    public void testTasksByOwnersListWithoutCandidate() {
//        AdvancedTaskQuery taskQuery = new AdvancedTaskQuery()
//                .withoutGroupCandidates()
//                .setOwners(Arrays.asList(TEST_USER, ADD_USER));
//        List<Task> actualTasks = advancedWorkflowService.testQueryTasks(taskQuery);
//        assertEquals(WRONG_NUMBER, 1, actualTasks.size());
//
//        Task expectedTask = getTaskById(TEST_TASK4, expectedTasks);
//        Task actualTask = getTaskById(TEST_TASK4, actualTasks);
//        assertNotNull(TASK + TEST_TASK4 + NOT_IN_OR_NULL, actualTask);
//        assertEqualsTasks(NOT_EQUALS, expectedTask, actualTask);
    }

    @Ignore
    @Test
    public void testTasksByOriginalOwner() {
//        taskService.setVariableLocal(TEST_TASK4, "taskOriginalOwner", ADD_USER);
//
//        Task expectedTask = getTaskById(TEST_TASK4, expectedTasks);
//        DelegateTask delegateTask = (DelegateTask) expectedTask;
//        delegateTask.setVariableLocal("taskOriginalOwner", ADD_USER);
//
//        AdvancedTaskQuery taskQuery = new AdvancedTaskQuery()
//                .withoutGroupCandidates()
//                .setOriginalOwner(ADD_USER);
//        List<Task> actualTasks = advancedWorkflowService.testQueryTasks(taskQuery);
//        assertEquals(WRONG_NUMBER, 1, actualTasks.size());
//
//        Task actualTask = getTaskById(TEST_TASK4, actualTasks);
//        assertEqualsTasks(NOT_EQUALS, expectedTask, actualTask);
    }

    @Test
    public void testTasksByCandidateUser() {
//        AdvancedTaskQuery taskQuery = new AdvancedTaskQuery()
//                .withoutGroupCandidates()
//                .setCandidateUser(ADD_USER);
//        List<Task> actualTasks = advancedWorkflowService.testQueryTasks(taskQuery);
//        assertEquals(WRONG_NUMBER, 1, actualTasks.size());
//
//        Task expectedTask = getTaskById(TEST_TASK4, expectedTasks);
//        Task actualTask = getTaskById(TEST_TASK4, actualTasks);
//        assertEqualsTasks(NOT_EQUALS, expectedTask, actualTask);
    }

    private void assertEqualsTasks(String message, Task preparedTask, Task actualTask) {
        assertEquals(message + "by name.", preparedTask.getName(), actualTask.getName());
        assertEquals(message + "by owner", preparedTask.getOwner(), actualTask.getOwner());
        assertEquals(message + "by assignee", preparedTask.getAssignee(), actualTask.getAssignee());
        assertEquals(message + "by create time", preparedTask.getCreateTime(), actualTask.getCreateTime());
    }

    private Task getTaskById(String taskId, List<Task> taskList) {
        for (Task task : taskList) {
            if (task.getId().equals(taskId)) {
                return task;
            }
        }
        return null;
    }

    @Before
    public void beforeTest() {
        // mark transaction as rollback-only, so that repository state does not change
        // regardless of test execution status (success/failure)
//        UserTransaction transaction = RetryingTransactionHelper.getActiveUserTransaction();
//        if (transaction != null) {
//            try {
//                transaction.setRollbackOnly();
//            } catch (SystemException e) {
//                throw new IllegalStateException(e);
//            }
//        }
//
//        ApplicationContext context = ApplicationContextHelper.getApplicationContext();
//        ServiceRegistry serviceRegistry = context.getBean("ServiceRegistry", ServiceRegistry.class);
//
//        /*AlfrescoProcessEngineConfiguration engineConfiguration = context.getBean("activitiProcessEngineConfiguration", AlfrescoProcessEngineConfiguration.class);
//        CommandContextFactory contextFactory = engineConfiguration.getCommandContextFactory();
//        CommandContext commandContext = contextFactory.createCommandContext(new TaskQueryImpl());*/
//
//        authorityService = serviceRegistry.getAuthorityService();
//        personService = serviceRegistry.getPersonService();
//        ProcessEngine processEngine = context.getBean("activitiProcessEngine", ProcessEngine.class);
//        taskService = processEngine.getTaskService();
//        ActivitiWorkflowManager workflowManager = context.getBean("activitiWorkflowManager", ActivitiWorkflowManager.class);
//        engine = workflowManager.getWorkflowEngine();
//        advancedWorkflowService = (AdvancedWorkflowService) serviceRegistry.getService(CiteckServices.ADVANCED_WORKFLOW_SERVICE);
//        AuthenticationUtil.setRunAsUserSystem();
//
//        int shortNameIndex = AuthorityType.GROUP.getPrefixString().length();
//
//        authorityService.createAuthority(AuthorityType.GROUP, TEST_ROLE.substring(shortNameIndex));
//        HashMap<QName, Serializable> personProperties = new HashMap<QName, Serializable>(1);
//        personProperties.put(ContentModel.PROP_USERNAME, TEST_USER);
//        personService.createPerson(personProperties);
//        authorityService.addAuthority(TEST_ROLE, TEST_USER);
//        personProperties.put(ContentModel.PROP_USERNAME, ADD_USER);
//        personService.createPerson(personProperties);
//
//        Task assignedTask = taskService.newTask(TEST_TASK1);
//        assignedTask.setAssignee(TEST_USER);
//        assignedTask.setName("Test");
//        assignedTask.setOwner(TEST_USER);
//        taskService.saveTask(assignedTask);
//        Task unassignedTask = taskService.newTask(TEST_TASK2);
//        taskService.saveTask(unassignedTask);
//        Task simpleTask = taskService.newTask(TEST_TASK3);
//        simpleTask.setOwner(ADD_USER);
//        taskService.saveTask(simpleTask);
//        Task withoutCandidateTask = taskService.newTask(TEST_TASK4);
//        withoutCandidateTask.setOwner(TEST_USER);
//        withoutCandidateTask.setAssignee(TEST_USER);
//        taskService.saveTask(withoutCandidateTask);
//        taskService.addCandidateGroup(TEST_TASK1, TEST_ROLE);
//        taskService.addCandidateGroup(TEST_TASK2, TEST_ROLE);
//        taskService.addCandidateGroup(TEST_TASK3, TEST_ROLE);
//        taskService.addCandidateUser(TEST_TASK4, ADD_USER);
//        expectedTasks = new ArrayList<Task>(4);
//        expectedTasks.add(assignedTask);
//        expectedTasks.add(unassignedTask);
//        expectedTasks.add(simpleTask);
//        expectedTasks.add(withoutCandidateTask);
//
//        /*RuntimeService runtimeService = processEngine.getRuntimeService();
//        ProcessDefinitionBuilder builder = new ProcessDefinitionBuilder();
//        builder.createActivity("testActivity");
//        builder.initial();
//        builder.behavior(new MyActivityBehavior());
//        builder.transition("testActivity2");
//        builder.endActivity();
//        builder.createActivity("testActivity2");
//        builder.behavior(new MyActivityBehavior());
//        builder.endActivity();
//        PvmProcessDefinition pvmProcessDefinition = builder.buildProcessDefinition();
//        PvmProcessInstance processInstance = pvmProcessDefinition.createProcessInstance();
//        processInstance.start();
//        //runtimeService.startProcessInstanceById(pvmProcessDefinition.getId());
//
//        List<Task> taskList = taskService.createTaskQuery().executionId(((ExecutionImpl) processInstance).getId()).list();
//
//        ((TaskEntity) assignedTask).setProcessInstanceId(((ExecutionImpl) processInstance).getProcessInstanceId());
//        taskService.saveTask(assignedTask);
//        taskList.get(0);*/
//
//        /*WorkflowService workflowService = serviceRegistry.getWorkflowService();
//        WorkflowDefinition definitionByName = engine.getDefinitionByName("activiti$activitiReview");
//        HashMap<QName, Serializable> properties = new HashMap<QName, Serializable>();
//        properties.put(WorkflowModel.PROP_DESCRIPTION, "PROP_DESCRIPTION");
//        properties.put(WorkflowModel.PROP_WORKFLOW_DESCRIPTION, "PROP_WORKFLOW_DESCRIPTION");
//        properties.put(WorkflowModel.ASSOC_ASSIGNEE, personService.getPerson("admin"));
//        NodeRef packageNodeRef = workflowService.createPackage(null);
//        properties.put(WorkflowModel.ASSOC_PACKAGE, packageNodeRef);
//        Date date = new Date(System.currentTimeMillis());
//        properties.put(WorkflowModel.PROP_DUE_DATE, date);
//        properties.put(WorkflowModel.PROP_WORKFLOW_DUE_DATE, date);
//        WorkflowPath workflowPath = workflowService.startWorkflow(definitionByName.getId(), properties);*/
    }

    @After
    public void afterTest() {
//        try {
//            taskService.deleteTask(TEST_TASK1, true);
//            taskService.deleteTask(TEST_TASK2, true);
//            taskService.deleteTask(TEST_TASK3, true);
//            taskService.deleteTask(TEST_TASK4, true);
//            personService.deletePerson(TEST_USER);
//            personService.deletePerson(ADD_USER);
//            authorityService.deleteAuthority(TEST_ROLE);
//        } catch (Exception e) {
//            // ignore
//        }
//        AuthenticationUtil.clearCurrentSecurityContext();
    }

    public static class MyActivityBehavior implements SignallableActivityBehavior {

        public void execute(ActivityExecution execution) throws Exception {
            System.out.println("Here ");
        }

        public void signal(ActivityExecution arg0, String arg1, Object arg2)
                throws Exception {
            System.out.println("Here signal");
        }

    }
}
