package com.example.hxds.vhr;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.Resource;

@SpringBootTest
class HxdsVhrApplicationTests {
    @Resource
    private RedisTemplate redisTemplate;
    @Test
    void contextLoads() {
        boolean bool=redisTemplate.hasKey("voucher_"+"ce67d1aff6f4475aaba228f2366f1c16");
        System.out.println(bool);

    }

}
