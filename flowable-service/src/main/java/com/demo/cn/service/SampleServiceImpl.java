package com.demo.cn.service;


import com.demo.cn.endpoint.SampleService;
import org.springframework.stereotype.Component;

@Component
public class SampleServiceImpl implements SampleService {

    @Override
    public String message() {
        return "Hello, Service slitecore";
    }
}
