package com.demo.cn.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FlowableHelper {

    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private HistoryService historyService;

    /**
     * Represents current process state with highlighted flows and activities.
     *
     */
    public static class ProcessState {
        String processInstanceId;
        String processDefinitionId;
        Set<String> completedActivities = new LinkedHashSet<>();
        Set<String> currentActivities = new HashSet<>();
        List<String> completedSequenceFlows = new ArrayList<>();

        public String getProcessInstanceId() {
            return processInstanceId;
        }

        public String getProcessDefinitionId() {
            return processDefinitionId;
        }

        public Set<String> getCompletedActivities() {
            return completedActivities;
        }

        public Set<String> getCurrentActivities() {
            return currentActivities;
        }

        public List<String> getCompletedSequenceFlows() {
            return completedSequenceFlows;
        }
    }

    /**
     * Represents historic process flow with activities and sequence flows.
     *
     */
    public static class HistoricActivityInstanceFlow {
        HistoricActivityInstance historicActivityInstance;
        SequenceFlow incomingFlow;
        FlowElement flowElement;

        public HistoricActivityInstance getHistoricActivityInstance() {
            return historicActivityInstance;
        }

        public SequenceFlow getIncomingFlow() {
            return incomingFlow;
        }

        public FlowElement getFlowElement() {
            return flowElement;
        }
    }

    /**
     * Returns Process Instance state:
     * <ul>
     * <li>completed activities</li>
     * <li>current activities</li>
     * <li>completed sequence flows</li>
     * </ul>
     *
     * Process Instance can be completed.
     *
     * @param processInstanceId
     * @return
     */
    public ProcessState getProcessState(String processInstanceId) {

        String processDefinitionId = null;
        try {
            ProcessInstance processInstance = getProcessInstanceFromRequest(processInstanceId);
            processDefinitionId = processInstance.getProcessDefinitionId();
        } catch (FlowableObjectNotFoundException e) {
            HistoricProcessInstance historicProcessInstance = getHistoricProcessInstanceFromRequest(processInstanceId);
            processDefinitionId = historicProcessInstance.getProcessDefinitionId();
        }

        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);

        if (bpmnModel == null || bpmnModel.getLocationMap().isEmpty()) {
            throw new FlowableException("Process definition could not be found with id " + processDefinitionId);
        }

        // Fetch process-instance activities
        List<HistoricActivityInstance> activityInstances = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId).orderByHistoricActivityInstanceStartTime().asc().list();

        // Gather completed flows
        List<HistoricActivityInstanceFlow> historicActivityInstanceFlows = gatherCompletedFlows(bpmnModel,
                activityInstances);
        List<String> completedFlows = historicActivityInstanceFlows.stream()
                .filter(hf -> Objects.nonNull(hf.incomingFlow)).map(hf -> hf.incomingFlow.getId())
                .collect(Collectors.toList());

        Set<String> completedActivityInstances = new LinkedHashSet<>();
        Set<String> currentElements = new HashSet<>();
        if (activityInstances != null && !activityInstances.isEmpty()) {
            for (HistoricActivityInstance activityInstance : activityInstances) {
                if (activityInstance.getEndTime() != null) {
                    completedActivityInstances.add(activityInstance.getActivityId());
                } else {
                    currentElements.add(activityInstance.getActivityId());
                }
            }
        }

        Set<String> completedElements = new HashSet<>(completedActivityInstances);
        completedElements.addAll(completedFlows);

        ProcessState processState = new ProcessState();

        processState.processInstanceId = processInstanceId;
        processState.processDefinitionId = processDefinitionId;

        processState.completedActivities.addAll(completedActivityInstances);
        processState.currentActivities.addAll(currentElements);
        processState.completedSequenceFlows.addAll(completedFlows);

        return processState;
    }

    /**
     * Returns historic activity flows.
     *
     * @param processInstanceId
     * @return
     */
    public List<HistoricActivityInstanceFlow> getProcessFlow(String processInstanceId) {
        String processDefinitionId = null;
        try {
            ProcessInstance processInstance = getProcessInstanceFromRequest(processInstanceId);
            processDefinitionId = processInstance.getProcessDefinitionId();
        } catch (FlowableObjectNotFoundException e) {
            HistoricProcessInstance historicProcessInstance = getHistoricProcessInstanceFromRequest(processInstanceId);
            processDefinitionId = historicProcessInstance.getProcessDefinitionId();
        }

        BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);

        if (bpmnModel == null || bpmnModel.getLocationMap().isEmpty()) {
            throw new FlowableException("Process definition could not be found with id " + processDefinitionId);
        }

        // Fetch process-instance activities
        List<HistoricActivityInstance> activityInstances = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId).orderByHistoricActivityInstanceStartTime().asc().list();

        // Gather completed flows
        return gatherCompletedFlows(bpmnModel, activityInstances);
    }

    /**
     * Returns completed historic activity flows. Can contain elements with null-incoming flows, i.e. StartEvent
     *
     * @param pojoModel
     * @param historicActivityInstances
     * @return
     */
    protected List<HistoricActivityInstanceFlow> gatherCompletedFlows(BpmnModel pojoModel,
                                                                      List<HistoricActivityInstance> historicActivityInstances) {

        List<HistoricActivityInstanceFlow> historicActivityInstanceFlows = new ArrayList<>();

        for (HistoricActivityInstance historicActivityInstance : historicActivityInstances) {
            String activityId = historicActivityInstance.getActivityId();
            FlowElement activity = pojoModel.getFlowElement(activityId);
            if (activity instanceof FlowNode) {
                HistoricActivityInstanceFlow historicActivityInstanceFlow = new HistoricActivityInstanceFlow();
                historicActivityInstanceFlow.historicActivityInstance = historicActivityInstance;
                historicActivityInstanceFlow.flowElement = activity;

                String type = historicActivityInstance.getActivityType();
                List<SequenceFlow> incomingFlows = ((FlowNode) activity).getIncomingFlows();
                for (SequenceFlow flow : incomingFlows) {
                    String sourceId = flow.getSourceRef();

                    HistoricActivityInstance sourceHistoricActivityInstance = null;
                    // Find in history with the same execution id, used for joined parallel gateways.
                    // Multiple incoming paths of execution have different executionIds and
                    // there is many instances of the Parallel Gateway activity in the
                    // history. Because of that we should find activity in the same execution.
                    // It is only for joined executions (incoming transitions count > 1)
                    if (("parallelGateway".equals(type) || "inclusiveGateway".equals(type))
                            && incomingFlows.size() > 1) {
                        sourceHistoricActivityInstance = getHistoricActivityById(historicActivityInstances, sourceId,
                                historicActivityInstance.getExecutionId());
                    } else {
                        sourceHistoricActivityInstance = getHistoricActivityById(historicActivityInstances, sourceId,
                                null);
                    }
                    if (sourceHistoricActivityInstance != null) {
                        historicActivityInstanceFlow.incomingFlow = flow;
                    }

                }
                historicActivityInstanceFlows.add(historicActivityInstanceFlow);
            }
        }

        // TODO: check subprocess

        return historicActivityInstanceFlows;
    }

    /**
     * Returns historic activity instance by activity id and specified execution id
     *
     * @param historicActivityInstances
     * @param activityId
     * @param executionId
     * @return
     */
    private HistoricActivityInstance getHistoricActivityById(List<HistoricActivityInstance> historicActivityInstances,
                                                             String activityId, String executionId) {
        for (HistoricActivityInstance historicActivityInstance : historicActivityInstances) {
            if (Objects.equals(historicActivityInstance.getActivityId(), activityId) && (executionId == null
                    || Objects.equals(executionId, historicActivityInstance.getExecutionId()))) {
                return historicActivityInstance;
            }
        }
        return null;
    }

    protected ProcessInstance getProcessInstanceFromRequest(String processInstanceId) {
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        if (processInstance == null) {
            throw new FlowableObjectNotFoundException(
                    "Could not find a process instance with id '" + processInstanceId + "'.");
        }
        return processInstance;
    }

    protected HistoricProcessInstance getHistoricProcessInstanceFromRequest(String processInstanceId) {
        HistoricProcessInstance processInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        if (processInstance == null) {
            throw new FlowableObjectNotFoundException(
                    "Could not find a historic process instance with id '" + processInstanceId + "'.");
        }
        return processInstance;
    }
}
