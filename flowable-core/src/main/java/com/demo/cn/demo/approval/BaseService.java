package com.demo.cn.demo.approval;

import java.util.ArrayList;

import com.demo.cn.demo.approval.dto.HistoryInfoResp;
import com.demo.cn.utils.FlowableHelper;
import com.google.gson.Gson;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.*;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.impl.bpmn.deployer.ResourceNameUtil;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.idm.api.Group;
import org.flowable.idm.api.User;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;

import java.text.SimpleDateFormat;
import java.util.*;


@Component
public class BaseService {
    @Resource(name = "processEngineConfiguration")
    private ProcessEngineConfiguration cfg;

    @Resource(name = "repositoryService")
    private RepositoryService repositoryService;

    @Resource(name = "processEngine")
    private ProcessEngine processEngine;

    @Resource(name = "runtimeService")
    private RuntimeService runtimeService;

    @Resource(name = "taskService")
    private TaskService taskService;

    @Resource(name = "historyService")
    private HistoryService historyService;

    @Resource(name = "identityService")
    private IdentityService identityService;

    @Resource
    private FlowableHelper flowableHelper;

    private static final String DEMO_PROCESS_ID = "ThreeLevelAuditing";

    public String startAuditingProcessByProcessID(String processId) {
        return startAuditingProcessByProcessID(processId, null);
    }

    public String startAuditingProcessByProcessID(String processId, Map value) {
//        cfg.getClock().setCurrentTime(null);
        ProcessInstance instance;
        if (value == null) {
            instance = runtimeService.startProcessInstanceByKey(processId);
        } else {
            instance = runtimeService.startProcessInstanceByKey(processId, value);
        }
        return instance.getProcessInstanceId();
    }


    /**
     * 受理任务
     *
     * @param taskId
     * @param userID
     */
    public void accpectTaskByTaskId(String taskId, String userID) {
//        cfg.getClock().setCurrentTime(null);
        taskService.claim(taskId, userID);
    }


    /**
     * 完成任务
     *
     * @param taskId
     */
    public void completeTaskByTaskId(String taskId) {
        Boolean pass = getRandomBooelean();
        taskService.setVariable(taskId, "pass", pass);
        System.out.println(taskService.createTaskQuery().taskId(taskId).list().get(0).getName() + ":" +
                (pass ? "审批通过" : "审批驳回"));
        taskService.complete(taskId);
    }

    /**
     * 查询正在进行中的ProcessInstance
     *
     * @param processId
     * @return
     */
    public List<String> queryCandidateProcessByProcessId(String processId) {
        List<ProcessInstance> instanceList = runtimeService
                .createProcessInstanceQuery()
                .processDefinitionKey(processId)
                .list();
        List<String> result = new ArrayList();
        instanceList.sort((ProcessInstance o1, ProcessInstance o2) ->
                (o1.getStartTime().getTime() - o2.getStartTime().getTime() == 0 ? 0 :
                        (o1.getStartTime().getTime() - o2.getStartTime().getTime() < 0 ? 1 : -1)));
        Gson gson = new Gson();
        for (ProcessInstance instance : instanceList) {
            SimpleDateFormat formatter =  new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
            HashMap object = new HashMap();
            object.put("ProcessId", instance.getId());
            object.put("StartTime", formatter.format(instance.getStartTime()));
            object.put("processInstanceId", instance.getProcessInstanceId());
            object.put("candidateTasks", queryCandidateTasksByProcessId(instance.getId()));
            result.add(gson.toJson(object));
        }
        return result;
    }

    /**
     * 查询正在进行中的Task
     *
     * @param processId
     * @return
     */
    public List<String> queryCandidateTasksByProcessId(String processId) {
        List<Task> taskList = taskService.createTaskQuery().processInstanceId(processId).list();

        List<String> result = new ArrayList();
        Gson gson = new Gson();
        for (Task task : taskList) {
            HashMap object = new HashMap();
            object.put("Id", task.getId());
            object.put("StartTime", task.getAssignee());
            object.put("processInstanceId", task.getProcessInstanceId());
            result.add(gson.toJson(object));

        }
        return result;
    }

    /***
     * 查询历史记录
     * @param processId 流程号
     * @return 流程信息结果
     */
    public List<HistoryInfoResp> queryHistoryInfo(String processId) {
        List<HistoryInfoResp> historyInfoRespList = new ArrayList<>();
        ProcessInstance pi = runtimeService.createProcessInstanceQuery().processInstanceId(processId).singleResult();
        if (pi == null) {
            HistoryInfoResp resp = new HistoryInfoResp();
            resp.setActivityId("1");
            resp.setActivityName("流程实例不存在");
            resp.setActivityType(null);
            historyInfoRespList.add(resp);
            return historyInfoRespList;
        }
        List<HistoricActivityInstance> historicActivityInstanceList = historyService.createHistoricActivityInstanceQuery().processInstanceId(pi.getId()).finished().orderByHistoricActivityInstanceEndTime().asc().list();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        historicActivityInstanceList.forEach(e -> {
            HistoryInfoResp info = new HistoryInfoResp();
            info.setActivityId(e.getId());
            info.setActivityType(e.getActivityType());
            info.setActivityName(("exclusiveGateway".equals(e.getActivityType()) && e.getActivityName() == null) ? "网关" : e.getActivityName());
            info.setStartTime(sdf.format(e.getStartTime()));
            info.setEndTime(sdf.format(e.getEndTime()));
            historyInfoRespList.add(info);
        });
        return historyInfoRespList;
    }


    /***
     * 生成在线流程图， 可以高亮显示历史流程节点和流向
     * @param response  servelet响应
     * @param processId 流程号
     * @throws Exception  流程异常
     */
    public void generateDiagram(HttpServletResponse response , String processId) throws Exception {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery().processInstanceId(processId).singleResult();
        if (pi == null) {
            return;
        }
        Task task = taskService.createTaskQuery().processInstanceId(pi.getId()).singleResult();
        String instanceId = task.getProcessInstanceId();
        List<Execution> executionList = runtimeService.createExecutionQuery().processInstanceId(instanceId).list();
        List<HistoricActivityInstance> historicActivityInstanceList = historyService.createHistoricActivityInstanceQuery().processInstanceId(pi.getId()).finished().orderByHistoricActivityInstanceEndTime().asc().list();

        List<String> activityIdLists = new ArrayList<>();
        historicActivityInstanceList.forEach(hs -> activityIdLists.add(hs.getActivityId()));

        List<FlowableHelper.HistoricActivityInstanceFlow> historyFlowIntancesList =  flowableHelper.getProcessFlow(processId);
        List<String> flows = new ArrayList<>();

        historyFlowIntancesList.forEach(e -> {
            if (e.getIncomingFlow() != null) {
                flows.add(e.getIncomingFlow().getId());
            }
        });

        BpmnModel model = repositoryService.getBpmnModel(pi.getProcessDefinitionId());
        ProcessEngineConfiguration configuration = processEngine.getProcessEngineConfiguration();
        ProcessDiagramGenerator generator = configuration.getProcessDiagramGenerator();
        InputStream is = generator.generateDiagram(model , "bmp" , activityIdLists , flows , processEngine.getProcessEngineConfiguration().getActivityFontName(),
                processEngine.getProcessEngineConfiguration().getLabelFontName() , processEngine.getProcessEngineConfiguration().getAnnotationFontName() ,
                processEngine.getProcessEngineConfiguration().getClassLoader() , 1 , false);
        response.setCharacterEncoding("utf8");
        OutputStream os = null;
        byte[] buf = new byte[1024];
        int len = 0;
        try {
            os = response.getOutputStream();
            while ((len = is.read(buf)) != -1) {
                os.write(buf , 0 , len);
            }
        } finally {
            if (is != null) {
                is.close();
            }
            if (os != null) {
                os.close();
            }
        }
    }

    public String checkUserRole(String level , String userId) {
//        initializeUserGroup();
        HashMap<String , String> resultMap = new HashMap<>();
        resultMap.put("auth" , "没有权限");
        List<Group> groupList = identityService.createGroupQuery().groupMember(userId).groupType("Audit").list();
        if (groupList.isEmpty()) {
            return resultMap.get("auth");
        }
        groupList.forEach(g -> {
            if (parseLevel(level).equals(g.getId())) {
                resultMap.put("auth" , "有权限");
            }
        });
        return resultMap.get("auth");
    }

    private void initializeUserGroup() {
        Group firstLevel = identityService.newGroup("firstLevel");
        firstLevel.setType("Audit");
        identityService.saveGroup(firstLevel);
        Group secondLevel = identityService.newGroup("secondLevel");
        secondLevel.setType("Audit");
        identityService.saveGroup(secondLevel);
        Group thirdLevel = identityService.newGroup("thirdLevel");
        thirdLevel.setType("Audit");
        identityService.saveGroup(thirdLevel);

        User user1 = identityService.newUser("1001");
        identityService.saveUser(user1);
        User user2 = identityService.newUser("1002");
        identityService.saveUser(user2);
        User user3 = identityService.newUser("1003");
        identityService.saveUser(user3);
        User user4 = identityService.newUser("1004");
        identityService.saveUser(user4);
        User user5 = identityService.newUser("1005");
        identityService.saveUser(user5);

        identityService.createMembership("1001" , "firstLevel");
        identityService.createMembership("1002" , "firstLevel");
        identityService.createMembership("1003" , "secondLevel");
        identityService.createMembership("1004" , "secondLevel");
        identityService.createMembership("1005" , "thirdLevel");
    }

    private String parseLevel(String level) {
        String lvRst = "";
        switch (level) {
            case "1": {
                lvRst = "firstLevel";
            }
            case "2" : {
                lvRst = "secondLevel";
            }
            case "3" : {
                lvRst = "thirdLevel";
            }
        }
        return lvRst;
    }
    public String uploadProcessFiles(String fileName,InputStream inputStream){

        for (String suffix : ResourceNameUtil.BPMN_RESOURCE_SUFFIXES) {
            if (!fileName.endsWith(suffix)) {
                return "Error. fileName should end with {\"bpmn20.xml\", \"bpmn\"}";
            }
        }

        try {
            Deployment deployment = repositoryService.createDeployment()
                    .addInputStream(fileName, inputStream).deploy();
            System.out.println(deployment);
        }catch (Exception e){
            e.printStackTrace();
            return "Error";
        }
        return "Success";
    }



    private boolean getRandomBooelean() {
        Random rand = new Random();

        int n = rand.nextInt(50);

        return n % 3 != 0 ? true : false;
    }
}

