/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.test.api.runtime;

import static org.camunda.bpm.engine.test.util.ActivityInstanceAssert.assertThat;
import static org.camunda.bpm.engine.test.util.ActivityInstanceAssert.describeActivityInstanceTree;
import static org.camunda.bpm.engine.test.util.ExecutionAssert.assertThat;
import static org.camunda.bpm.engine.test.util.ExecutionAssert.describeExecutionTree;

import java.util.Collections;
import java.util.List;

import org.camunda.bpm.engine.delegate.ExecutionListener;
import org.camunda.bpm.engine.impl.test.PluggableProcessEngineTestCase;
import org.camunda.bpm.engine.runtime.ActivityInstance;
import org.camunda.bpm.engine.runtime.EventSubscription;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.examples.bpmn.executionlistener.RecorderExecutionListener;
import org.camunda.bpm.engine.test.examples.bpmn.executionlistener.RecorderExecutionListener.RecordedEvent;
import org.camunda.bpm.engine.test.util.ExecutionTree;
import org.camunda.bpm.engine.variable.Variables;

/**
 * Tests cancellation of four basic patterns of active activities in a scope:
 * <ul>
 *  <li>single, non-scope activity
 *  <li>single, scope activity
 *  <li>two concurrent non-scope activities
 *  <li>two concurrent scope activities
 * </ul>
 *
 * @author Thorben Lindhauer
 */
public class ProcessInstanceModificationCancellationTest extends PluggableProcessEngineTestCase {

  // the four patterns as described above
  protected static final String ONE_TASK_PROCESS = "org/camunda/bpm/engine/test/api/runtime/oneTaskProcess.bpmn20.xml";
  protected static final String ONE_SCOPE_TASK_PROCESS = "org/camunda/bpm/engine/test/api/runtime/ProcessInstanceModificationTest.oneScopeTaskProcess.bpmn20.xml";
  protected static final String CONCURRENT_PROCESS = "org/camunda/bpm/engine/test/api/runtime/ProcessInstanceModificationTest.parallelGateway.bpmn20.xml";
  protected static final String CONCURRENT_SCOPE_TASKS_PROCESS = "org/camunda/bpm/engine/test/api/runtime/ProcessInstanceModificationTest.parallelGatewayScopeTasks.bpmn20.xml";

  // the four patterns nested in a subprocess and with an outer parallel task
  protected static final String NESTED_PARALLEL_ONE_TASK_PROCESS = "org/camunda/bpm/engine/test/api/runtime/ProcessInstanceModificationTest.nestedParallelOneTaskProcess.bpmn20.xml";
  protected static final String NESTED_PARALLEL_ONE_SCOPE_TASK_PROCESS = "org/camunda/bpm/engine/test/api/runtime/ProcessInstanceModificationTest.nestedParallelOneScopeTaskProcess.bpmn20.xml";
  protected static final String NESTED_PARALLEL_CONCURRENT_PROCESS = "org/camunda/bpm/engine/test/api/runtime/ProcessInstanceModificationTest.nestedParallelGateway.bpmn20.xml";
  protected static final String NESTED_PARALLEL_CONCURRENT_SCOPE_TASKS_PROCESS = "org/camunda/bpm/engine/test/api/runtime/ProcessInstanceModificationTest.nestedParallelGatewayScopeTasks.bpmn20.xml";

  protected static final String LISTENER_PROCESS = "org/camunda/bpm/engine/test/api/runtime/ProcessInstanceModificationTest.listenerProcess.bpmn20.xml";

  protected static final String INTERRUPTING_EVENT_SUBPROCESS = "org/camunda/bpm/engine/test/api/runtime/ProcessInstanceModificationTest.interruptingEventSubProcess.bpmn20.xml";

  // TODO: test cases
  // * event subscriptions, jobs, etc. are preserved when process instance is bare due to cancellations
  // * after cancellation and creation, there should be a new activity instance id for the newly instantiated task

  @Deployment(resources = ONE_TASK_PROCESS)
  public void testCancellationInOneTaskProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "theTask"))
      .execute();

    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = ONE_TASK_PROCESS)
  public void testCancellationAndCreationInOneTaskProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "theTask"))
      .startBeforeActivity("theTask")
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());
    assertTrue(!getInstanceIdForActivity(tree, "theTask").equals(getInstanceIdForActivity(updatedTree, "theTask")));

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("theTask")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree("theTask").scope()
        .done());

    // assert successful completion of process
    Task task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = ONE_TASK_PROCESS)
  public void testCreationAndCancellationInOneTaskProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .startBeforeActivity("theTask")
      .cancelActivityInstance(getInstanceIdForActivity(tree, "theTask"))
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());
    assertTrue(!getInstanceIdForActivity(tree, "theTask").equals(getInstanceIdForActivity(updatedTree, "theTask")));

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("theTask")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree("theTask").scope()
        .done());

    // assert successful completion of process
    Task task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = ONE_SCOPE_TASK_PROCESS)
  public void testCancellationInOneScopeTaskProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "theTask"))
      .execute();

    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = ONE_SCOPE_TASK_PROCESS)
  public void testCancellationAndCreationInOneScopeTaskProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "theTask"))
      .startBeforeActivity("theTask")
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());
    assertTrue(!getInstanceIdForActivity(tree, "theTask").equals(getInstanceIdForActivity(updatedTree, "theTask")));

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("theTask")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope()
        .child("theTask").scope()
      .done());

    // assert successful completion of process
    Task task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = ONE_SCOPE_TASK_PROCESS)
  public void testCreationAndCancellationInOneScopeTaskProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .startBeforeActivity("theTask")
      .cancelActivityInstance(getInstanceIdForActivity(tree, "theTask"))
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());
    assertTrue(!getInstanceIdForActivity(tree, "theTask").equals(getInstanceIdForActivity(updatedTree, "theTask")));

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("theTask")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope()
        .child("theTask").scope()
      .done());

    // assert successful completion of process
    Task task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = CONCURRENT_PROCESS)
  public void testCancellationInConcurrentProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("parallelGateway");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "task1"))
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("task2")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree("task2").scope()
      .done());

    // assert successful completion of process
    Task task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = CONCURRENT_PROCESS)
  public void testCancellationAndCreationInConcurrentProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("parallelGateway");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "task1"))
      .startBeforeActivity("task1")
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());
    assertTrue(!getInstanceIdForActivity(tree, "task1").equals(getInstanceIdForActivity(updatedTree, "task1")));

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("task1")
        .activity("task2")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope()
        .child("task1").noScope().concurrent().up()
        .child("task2").noScope().concurrent()
      .done());

    // assert successful completion of process
    List<Task> tasks = taskService.createTaskQuery().list();
    assertEquals(2, tasks.size());

    taskService.complete(tasks.get(0).getId());
    taskService.complete(tasks.get(1).getId());
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = CONCURRENT_PROCESS)
  public void testCreationAndCancellationInConcurrentProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("parallelGateway");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .startBeforeActivity("task1")
      .cancelActivityInstance(getInstanceIdForActivity(tree, "task1"))
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());
    assertTrue(!getInstanceIdForActivity(tree, "task1").equals(getInstanceIdForActivity(updatedTree, "task1")));

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("task1")
        .activity("task2")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope()
        .child("task1").noScope().concurrent().up()
        .child("task2").noScope().concurrent()
      .done());

    // assert successful completion of process
    List<Task> tasks = taskService.createTaskQuery().list();
    assertEquals(2, tasks.size());

    taskService.complete(tasks.get(0).getId());
    taskService.complete(tasks.get(1).getId());
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = CONCURRENT_SCOPE_TASKS_PROCESS)
  public void testCancellationInConcurrentScopeTasksProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("parallelGateway");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "task1"))
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());
    assertTrue(!getInstanceIdForActivity(tree, "task1").equals(getInstanceIdForActivity(updatedTree, "task1")));

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("task2")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope()
        .child("task2").scope()
      .done());

    // assert successful completion of process
    Task task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = CONCURRENT_SCOPE_TASKS_PROCESS)
  public void testCancellationAndCreationInConcurrentScopeTasksProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("parallelGateway");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "task1"))
      .startBeforeActivity("task1")
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());
    assertTrue(!getInstanceIdForActivity(tree, "task1").equals(getInstanceIdForActivity(updatedTree, "task1")));

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("task1")
        .activity("task2")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope()
        .child(null).noScope().concurrent()
          .child("task1").scope().up().up()
        .child(null).noScope().concurrent()
          .child("task2").scope()
      .done());

    // assert successful completion of process
    List<Task> tasks = taskService.createTaskQuery().list();
    assertEquals(2, tasks.size());

    taskService.complete(tasks.get(0).getId());
    taskService.complete(tasks.get(1).getId());
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = CONCURRENT_SCOPE_TASKS_PROCESS)
  public void testCreationAndCancellationInConcurrentScopeTasksProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("parallelGateway");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .startBeforeActivity("task1")
      .cancelActivityInstance(getInstanceIdForActivity(tree, "task1"))
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());
    assertTrue(!getInstanceIdForActivity(tree, "task1").equals(getInstanceIdForActivity(updatedTree, "task1")));

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("task1")
        .activity("task2")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope()
        .child(null).noScope().concurrent()
          .child("task1").scope().up().up()
        .child(null).noScope().concurrent()
          .child("task2").scope()
      .done());

    // assert successful completion of process
    List<Task> tasks = taskService.createTaskQuery().list();
    assertEquals(2, tasks.size());

    taskService.complete(tasks.get(0).getId());
    taskService.complete(tasks.get(1).getId());
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = NESTED_PARALLEL_ONE_TASK_PROCESS)
  public void testCancellationInNestedOneTaskProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("nestedOneTaskProcess");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "innerTask"))
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("outerTask")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree("outerTask").scope()
        .done());

    // assert successful completion of process
    Task task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());
    assertProcessEnded(processInstanceId);

    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = NESTED_PARALLEL_ONE_TASK_PROCESS)
  public void testCancellationAndCreationInNestedOneTaskProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("nestedOneTaskProcess");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "innerTask"))
      .startBeforeActivity("innerTask")
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());
    assertTrue(!getInstanceIdForActivity(tree, "innerTask").equals(getInstanceIdForActivity(updatedTree, "innerTask")));

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("outerTask")
        .beginScope("subProcess")
          .activity("innerTask")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope()
      .child("outerTask").concurrent().noScope().up()
      .child(null).concurrent().noScope()
        .child("innerTask").scope()
      .done());

    // assert successful completion of process
    List<Task> tasks = taskService.createTaskQuery().list();
    assertEquals(2, tasks.size());

    for (Task task : tasks) {
      taskService.complete(task.getId());
    }
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = NESTED_PARALLEL_ONE_TASK_PROCESS)
  public void testCreationAndCancellationInNestedOneTaskProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("nestedOneTaskProcess");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .startBeforeActivity("innerTask")
      .cancelActivityInstance(getInstanceIdForActivity(tree, "innerTask"))
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());
    assertTrue(!getInstanceIdForActivity(tree, "innerTask").equals(getInstanceIdForActivity(updatedTree, "innerTask")));

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("outerTask")
        .beginScope("subProcess")
          .activity("innerTask")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope()
      .child("outerTask").concurrent().noScope().up()
      .child(null).concurrent().noScope()
        .child("innerTask").scope()
      .done());

    // assert successful completion of process
    List<Task> tasks = taskService.createTaskQuery().list();
    assertEquals(2, tasks.size());

    for (Task task : tasks) {
      taskService.complete(task.getId());
    }
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = NESTED_PARALLEL_ONE_SCOPE_TASK_PROCESS)
  public void testCancellationInNestedOneScopeTaskProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("nestedOneScopeTaskProcess");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "innerTask"))
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("outerTask")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree("outerTask").scope()
      .done());

    // assert successful completion of process
    Task task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = NESTED_PARALLEL_ONE_SCOPE_TASK_PROCESS)
  public void testCancellationAndCreationInNestedOneScopeTaskProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("nestedOneScopeTaskProcess");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "innerTask"))
      .startBeforeActivity("innerTask")
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());
    assertTrue(!getInstanceIdForActivity(tree, "theTask").equals(getInstanceIdForActivity(updatedTree, "theTask")));

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("outerTask")
        .beginScope("subProcess")
          .activity("innerTask")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope()
      .child("outerTask").concurrent().noScope().up()
      .child(null).concurrent().noScope()
        .child(null).scope()
          .child("innerTask").scope()
      .done());

    // assert successful completion of process
    List<Task> tasks = taskService.createTaskQuery().list();
    assertEquals(2, tasks.size());

    for (Task task : tasks) {
      taskService.complete(task.getId());
    }
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = NESTED_PARALLEL_ONE_SCOPE_TASK_PROCESS)
  public void testCreationAndCancellationInNestedOneScopeTaskProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("nestedOneScopeTaskProcess");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .startBeforeActivity("innerTask")
      .cancelActivityInstance(getInstanceIdForActivity(tree, "innerTask"))
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());
    assertTrue(!getInstanceIdForActivity(tree, "theTask").equals(getInstanceIdForActivity(updatedTree, "theTask")));

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("outerTask")
        .beginScope("subProcess")
          .activity("innerTask")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope()
      .child("outerTask").concurrent().noScope().up()
      .child(null).concurrent().noScope()
        .child(null).scope()
          .child("innerTask").scope()
      .done());

    // assert successful completion of process
    List<Task> tasks = taskService.createTaskQuery().list();
    assertEquals(2, tasks.size());

    for (Task task : tasks) {
      taskService.complete(task.getId());
    }
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = NESTED_PARALLEL_CONCURRENT_PROCESS)
  public void testCancellationInNestedConcurrentProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("nestedParallelGateway");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "innerTask1"))
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("outerTask")
        .beginScope("subProcess")
          .activity("innerTask2")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope()
        .child("outerTask").concurrent().noScope().up()
        .child(null).concurrent().noScope()
          .child("innerTask2").scope()
      .done());

    // assert successful completion of process
    List<Task> tasks = taskService.createTaskQuery().list();
    assertEquals(2, tasks.size());
    for (Task task : tasks) {
      taskService.complete(task.getId());
    }
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = NESTED_PARALLEL_CONCURRENT_PROCESS)
  public void testCancellationAndCreationInNestedConcurrentProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("nestedParallelGateway");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "innerTask1"))
      .startBeforeActivity("innerTask1")
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());
    assertTrue(!getInstanceIdForActivity(tree, "innerTask1").equals(getInstanceIdForActivity(updatedTree, "innerTask1")));

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("outerTask")
        .beginScope("subProcess")
          .activity("innerTask1")
          .activity("innerTask2")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope()
        .child("outerTask").noScope().concurrent().up()
        .child(null).noScope().concurrent()
          .child(null).scope()
            .child("innerTask1").noScope().concurrent().up()
            .child("innerTask2").noScope().concurrent()
      .done());

    // assert successful completion of process
    List<Task> tasks = taskService.createTaskQuery().list();
    assertEquals(3, tasks.size());
    for (Task task : tasks) {
      taskService.complete(task.getId());
    }
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = NESTED_PARALLEL_CONCURRENT_PROCESS)
  public void testCreationAndCancellationInNestedConcurrentProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("nestedParallelGateway");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .startBeforeActivity("innerTask1")
      .cancelActivityInstance(getInstanceIdForActivity(tree, "innerTask1"))
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());
    assertTrue(!getInstanceIdForActivity(tree, "innerTask1").equals(getInstanceIdForActivity(updatedTree, "innerTask1")));

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("outerTask")
        .beginScope("subProcess")
          .activity("innerTask1")
          .activity("innerTask2")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope()
        .child("outerTask").noScope().concurrent().up()
        .child(null).noScope().concurrent()
          .child(null).scope()
            .child("innerTask1").noScope().concurrent().up()
            .child("innerTask2").noScope().concurrent()
      .done());

    // assert successful completion of process
    List<Task> tasks = taskService.createTaskQuery().list();
    assertEquals(3, tasks.size());
    for (Task task : tasks) {
      taskService.complete(task.getId());
    }
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = NESTED_PARALLEL_CONCURRENT_SCOPE_TASKS_PROCESS)
  public void testCancellationInNestedConcurrentScopeTasksProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("nestedParallelGatewayScopeTasks");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "innerTask1"))
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("outerTask")
        .beginScope("subProcess")
          .activity("innerTask2")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope()
        .child("outerTask").concurrent().noScope().up()
        .child(null).concurrent().noScope()
          .child(null).scope()
            .child("innerTask2").scope()
      .done());

    // assert successful completion of process
    List<Task> tasks = taskService.createTaskQuery().list();
    assertEquals(2, tasks.size());
    for (Task task : tasks) {
      taskService.complete(task.getId());
    }
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = NESTED_PARALLEL_CONCURRENT_SCOPE_TASKS_PROCESS)
  public void testCancellationAndCreationInNestedConcurrentScopeTasksProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("nestedParallelGatewayScopeTasks");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "innerTask1"))
      .startBeforeActivity("innerTask1")
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());
    assertTrue(!getInstanceIdForActivity(tree, "innerTask1").equals(getInstanceIdForActivity(updatedTree, "innerTask1")));

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("outerTask")
        .beginScope("subProcess")
          .activity("innerTask1")
          .activity("innerTask2")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope()
        .child("outerTask").concurrent().noScope()
        .child(null).noScope().concurrent()
          .child(null).scope()
            .child(null).concurrent().noScope()
              .child("innerTask1").scope().up().up()
            .child(null).concurrent().noScope()
              .child("innerTask2").scope()
      .done());

    // assert successful completion of process
    List<Task> tasks = taskService.createTaskQuery().list();
    assertEquals(3, tasks.size());

    for (Task task : tasks) {
      taskService.complete(task.getId());
    }
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = NESTED_PARALLEL_CONCURRENT_SCOPE_TASKS_PROCESS)
  public void testCreationAndCancellationInNestedConcurrentScopeTasksProcess() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("nestedParallelGatewayScopeTasks");
    String processInstanceId = processInstance.getId();

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService
      .createProcessInstanceModification(processInstance.getId())
      .startBeforeActivity("innerTask1")
      .cancelActivityInstance(getInstanceIdForActivity(tree, "innerTask1"))
      .execute();

    assertProcessNotEnded(processInstanceId);

    // assert activity instance
    ActivityInstance updatedTree = runtimeService.getActivityInstance(processInstanceId);
    assertNotNull(updatedTree);
    assertEquals(processInstanceId, updatedTree.getProcessInstanceId());
    assertTrue(!getInstanceIdForActivity(tree, "innerTask1").equals(getInstanceIdForActivity(updatedTree, "innerTask1")));

    assertThat(updatedTree).hasStructure(
      describeActivityInstanceTree(processInstance.getProcessDefinitionId())
        .activity("outerTask")
        .beginScope("subProcess")
          .activity("innerTask1")
          .activity("innerTask2")
      .done());

    // assert executions
    ExecutionTree executionTree = ExecutionTree.forExecution(processInstanceId, processEngine);

    assertThat(executionTree)
    .matches(
      describeExecutionTree(null).scope()
        .child("outerTask").concurrent().noScope()
        .child(null).noScope().concurrent()
          .child(null).scope()
            .child(null).concurrent().noScope()
              .child("innerTask1").scope().up().up()
            .child(null).concurrent().noScope()
              .child("innerTask2").scope()
      .done());

    // assert successful completion of process
    List<Task> tasks = taskService.createTaskQuery().list();
    assertEquals(3, tasks.size());

    for (Task task : tasks) {
      taskService.complete(task.getId());
    }
    assertProcessEnded(processInstanceId);
  }

  @Deployment(resources = LISTENER_PROCESS)
  public void testEndListenerInvocation() {
    RecorderExecutionListener.clear();

    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
        "listenerProcess",
        Collections.<String, Object>singletonMap("listener", new RecorderExecutionListener()));

    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    // when one inner task is cancelled
    runtimeService.createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "innerTask1"))
      .execute();

    assertEquals(1, RecorderExecutionListener.getRecordedEvents().size());
    RecordedEvent innerTask1EndEvent = RecorderExecutionListener.getRecordedEvents().get(0);
    assertEquals(ExecutionListener.EVENTNAME_END, innerTask1EndEvent.getEventName());
    assertEquals("innerTask1", innerTask1EndEvent.getActivityId());
    assertEquals(getInstanceIdForActivity(tree, "innerTask1"), innerTask1EndEvent.getActivityInstanceId());

    // when the second inner task is cancelled
    RecorderExecutionListener.clear();
    runtimeService.createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "innerTask2"))
      .execute();

    assertEquals(2, RecorderExecutionListener.getRecordedEvents().size());
    RecordedEvent innerTask2EndEvent = RecorderExecutionListener.getRecordedEvents().get(0);
    assertEquals(ExecutionListener.EVENTNAME_END, innerTask2EndEvent.getEventName());
    assertEquals("innerTask2", innerTask2EndEvent.getActivityId());
    assertEquals(getInstanceIdForActivity(tree, "innerTask2"), innerTask2EndEvent.getActivityInstanceId());

    RecordedEvent subProcessEndEvent = RecorderExecutionListener.getRecordedEvents().get(1);
    assertEquals(ExecutionListener.EVENTNAME_END, subProcessEndEvent.getEventName());
    assertEquals("subProcess", subProcessEndEvent.getActivityId());
    assertEquals(getInstanceIdForActivity(tree, "subProcess"), subProcessEndEvent.getActivityInstanceId());

    // when the outer task is cancelled (and so the entire process)
    RecorderExecutionListener.clear();
    runtimeService.createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "outerTask"))
      .execute();

    assertEquals(2, RecorderExecutionListener.getRecordedEvents().size());
    RecordedEvent outerTaskEndEvent = RecorderExecutionListener.getRecordedEvents().get(0);
    assertEquals(ExecutionListener.EVENTNAME_END, outerTaskEndEvent.getEventName());
    assertEquals("outerTask", outerTaskEndEvent.getActivityId());
    assertEquals(getInstanceIdForActivity(tree, "outerTask"), outerTaskEndEvent.getActivityInstanceId());

    RecordedEvent processEndEvent = RecorderExecutionListener.getRecordedEvents().get(1);
    assertEquals(ExecutionListener.EVENTNAME_END, processEndEvent.getEventName());
    assertNull(processEndEvent.getActivityId());
    assertEquals(tree.getId(), processEndEvent.getActivityInstanceId());

    RecorderExecutionListener.clear();
  }

  @Deployment(resources = INTERRUPTING_EVENT_SUBPROCESS)
  public void testProcessInstanceEventSubscriptionsPreservedOnIntermediateCancellation() {
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("process");

    // event subscription for the event subprocess
    EventSubscription subscription = runtimeService.createEventSubscriptionQuery().singleResult();
    assertNotNull(subscription);
    assertEquals(processInstance.getId(), subscription.getProcessInstanceId());

    // when I execute cancellation and then start, such that the intermediate state of the process instance
    // has no activities
    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService.createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "task1"))
      .startBeforeActivity("task1")
      .execute();

    // then the message event subscription remains (i.e. it is not deleted and later re-created)
    EventSubscription updatedSubscription = runtimeService.createEventSubscriptionQuery().singleResult();
    assertNotNull(updatedSubscription);
    assertEquals(subscription.getId(), updatedSubscription.getId());
    assertEquals(subscription.getProcessInstanceId(), updatedSubscription.getProcessInstanceId());
  }

  @Deployment(resources = ONE_TASK_PROCESS)
  public void testProcessInstanceVariablesPreservedOnIntermediateCancellation() {
    ProcessInstance processInstance = runtimeService
        .startProcessInstanceByKey("oneTaskProcess", Variables.createVariables().putValue("var", "value"));

    // when I execute cancellation and then start, such that the intermediate state of the process instance
    // has no activities
    ActivityInstance tree = runtimeService.getActivityInstance(processInstance.getId());

    runtimeService.createProcessInstanceModification(processInstance.getId())
      .cancelActivityInstance(getInstanceIdForActivity(tree, "theTask"))
      .startBeforeActivity("theTask")
      .execute();

    // then the process instance variables remain
    Object variable = runtimeService.getVariable(processInstance.getId(), "var");
    assertNotNull(variable);
    assertEquals("value", variable);
  }

  public String getInstanceIdForActivity(ActivityInstance activityInstance, String activityId) {
    ActivityInstance instance = getChildInstanceForActivity(activityInstance, activityId);
    if (instance != null) {
      return instance.getId();
    }
    return null;
  }

  public ActivityInstance getChildInstanceForActivity(ActivityInstance activityInstance, String activityId) {
    if (activityId.equals(activityInstance.getActivityId())) {
      return activityInstance;
    }

    for (ActivityInstance childInstance : activityInstance.getChildActivityInstances()) {
      ActivityInstance instance = getChildInstanceForActivity(childInstance, activityId);
      if (instance != null) {
        return instance;
      }
    }

    return null;
  }
}
