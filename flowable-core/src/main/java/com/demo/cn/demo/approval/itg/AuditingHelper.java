package com.demo.cn.demo.approval.itg;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;


@Component(value ="AuditingHelper")
public class AuditingHelper implements JavaDelegate {


    @Override
    public void execute(DelegateExecution execution) {
         execution.setVariable("result",Boolean.TRUE);
        System.out.println("需要审批");
    }
}
