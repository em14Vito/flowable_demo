package com.demo.cn.demo.approval.itg;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;


@Component(value = "PassService")
public class PassCallBackService implements JavaDelegate {
    @Override
    public void execute(DelegateExecution execution) {
        System.out.println("审核通过，回调通过接口");
    }
}
