package com.demo.cn.endpoint;

import com.demo.cn.utils.SpringUtil;
import com.demo.cn.demo.approval.BaseService;
import com.demo.cn.demo.approval.dto.HistoryInfoResp;
import com.demo.cn.demo.approval.itg.AuditingHelper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;


@RestController
public class DemoMVC {

    @Autowired
    private BaseService baseService;


    @GetMapping(path = "/start")
    public String start(@RequestParam("processId") String processId) {
        String processInstanceId = "";
        try {
            processInstanceId = baseService.startAuditingProcessByProcessID(processId);
        } catch (Exception e) {
            e.printStackTrace();
            return "False";
        }
        return "Success. Current Time is " + getCurrentTime() + "; ProcessInstanceId is :" + processInstanceId;
    }

    @PostMapping(path = "/start", consumes = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public String startWithArgs(@RequestBody String request) {
        String processInstanceId = "";
        ObjectMapper mapper = new ObjectMapper();
        HashMap params;
        try {
            params = mapper.readValue(request, new TypeReference<Map<String, String>>() {
            });
        } catch (IOException e) {
            e.printStackTrace();
            return "Error. Convert request to json error";
        }
        try {
            processInstanceId = baseService.startAuditingProcessByProcessID(params.get("processId").toString(), params);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error";
        }
        return "Success. Current Time is " + getCurrentTime() + "; ProcessInstanceId is :" + processInstanceId;
    }


    @PostMapping(path = "/task/accept", consumes = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public String accpetTask(@RequestBody String value) {
        Gson gson = new Gson();
        Map params = gson.fromJson(value, Map.class);
        String taskId = params.get("taskId").toString();
        String userId = params.get("userId").toString();
        try {
            baseService.accpectTaskByTaskId(taskId, userId);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error";
        }
        return "Success";
    }

    @PostMapping(path = "/task/complete", consumes = MediaType.APPLICATION_JSON_UTF8_VALUE)
    public String completeTask(@RequestBody String value) {
        Gson gson = new Gson();
        Map params = gson.fromJson(value, Map.class);
        String taskId = params.get("taskId").toString();
        try {
            baseService.completeTaskByTaskId(taskId);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error";
        }
        return "Success";
    }

    @RequestMapping(path = "/process/candidate/{processId}", method = {RequestMethod.GET, RequestMethod.POST})
    @ResponseBody
    public List<String> getCandidateProcessByProcessId(@PathVariable("processId") String processId) {
        AuditingHelper test = SpringUtil.getBean("AuditingHelper", AuditingHelper.class);
        List<String> result = new ArrayList<>();
        try {
            result = baseService.queryCandidateProcessByProcessId(processId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @GetMapping(path = "/task/candidate")
    @ResponseBody
    public List<String> getCandidateTaskByProcessId(@RequestParam("processId") String processId) {
        List<String> result = new ArrayList<>();
        try {
            result = baseService.queryCandidateTasksByProcessId(processId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @GetMapping(path = "/process/history/{processId}")
    @ResponseBody
    public List<HistoryInfoResp> getHistoryInfo(@PathVariable("processId") String processId) {
        List<HistoryInfoResp> result = new ArrayList<>();
        try {
            result = baseService.queryHistoryInfo(processId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @GetMapping(path = "/process/role/{level}/{userId}")
    @ResponseBody
    public String checkUserRole(@PathVariable("level") String level, @PathVariable("userId") String userId) {
        String result = "";
        try {
            result = baseService.checkUserRole(level, userId);
        } catch (Exception e) {

            e.printStackTrace();
        }
        return result;
    }

    @PostMapping(path = "/process/upload/")
    public String uploadProcessDefinition(@RequestParam("file") MultipartFile file,
                                          RedirectAttributes redirectAttributes) {

        String result = "";
        try {

            result = baseService.uploadProcessFiles(file.getName(),file.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    @GetMapping(path = "/process/diagram/{processId}")
    @ResponseBody
    public void generateDiagram(@PathVariable("processId") String processId, HttpServletResponse response) {
        try {
            baseService.generateDiagram(response, processId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getCurrentTime() {
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSS");
        return formatter.format(date);
    }

}
