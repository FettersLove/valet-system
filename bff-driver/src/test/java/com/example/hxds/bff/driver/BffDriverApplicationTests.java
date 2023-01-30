package com.example.hxds.bff.driver;

import cn.dev33.satoken.stp.StpUtil;
import com.example.hxds.common.util.CosUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class BffDriverApplicationTests {
    @Resource
    private CosUtil cosUtil;
    @Test
    void contextLoads() {
        StpUtil.login(123456);
        String tokenValue = StpUtil.getTokenValue();

        System.out.println(tokenValue);
    }

}
