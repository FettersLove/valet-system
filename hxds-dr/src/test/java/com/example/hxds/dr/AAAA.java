package com.example.hxds.dr;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.qcloud.cos.model.JSONOutput;

import java.util.HashMap;

public class AAAA {
    public static void main(String[] args) {
        HttpRequest request = HttpUtil.createRequest(Method.GET,
                "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=wx4cb8e9621950da45&secret=9d80f4125c50e1cf8bc6031c9319b2df"
        );
        HttpResponse response = request.execute();
        String access_token=JSONUtil.parseObj(response.body()).getStr("access_token");
        System.out.println(access_token);


        JSONObject param=new JSONObject();
//        param.set("access_token",access_token);
        param.set("code","4fa8e2578f8e8ae0fe792f4fab899a713504682b450300b88749f662c07285e2");

        HttpRequest post = HttpUtil.createPost("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + access_token + "&");
        post.body(param.toString(),"application/json");
        response=post.execute();
        String tel= JSONUtil.parseObj(response.body()).getJSONObject("phone_info").getStr("purePhoneNumber");
        System.out.println(tel);
    }
}
