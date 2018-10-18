package com.demo.cn.demo.approval.itg;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;


@Component(value = "RejectService")
public class RejectCallbackService implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        System.out.println("审核驳回，回调驳回接口");
    }
}
