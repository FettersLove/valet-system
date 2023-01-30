package com.example.hxds.odr;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.common.wxpay.MyWXPayConfig;
import com.example.hxds.common.wxpay.WXPay;
import com.example.hxds.common.wxpay.WXPayConstants;
import com.example.hxds.common.wxpay.WXPayUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest
class HxdsOdrApplicationTests {
    @Resource
    private MyWXPayConfig myWXPayConfig;

    @Test
    void contextLoads() {
        try {
            WXPay wxPay = new WXPay(myWXPayConfig);
            HashMap param = new HashMap() {{
                put("appid", myWXPayConfig.getAppID());
                put("mch_id", myWXPayConfig.getMchID());
                put("nonce_str", WXPayUtil.generateNonceStr());
                put("out_order_no", "51502158dc454217a35c4d11f558476e");
                put("transaction_id", "4200001468202205308527802821");
            }};

            JSONArray receivers = new JSONArray();
            JSONObject json = new JSONObject();
            json.set("type", "PERSONAL_OPENID");
            json.set("account", "oNlSI5C2Ih9Qw6kjPjl7TnKrAEws");
            //分账金额从元转换成分
//            int amount = Integer.parseInt(NumberUtil.mul(driverIncome, "100").setScale(0, RoundingMode.FLOOR).toString());
//            json.set("amount", amount);
            json.set("amount", 1); //设置分账金额为1分钱（测试阶段）
            json.set("description", "给司机的分账");
            receivers.add(json);
            param.put("receivers", receivers.toString());
            String sign = WXPayUtil.generateSignature(param, myWXPayConfig.getKey(), WXPayConstants.SignType.HMACSHA256);
            param.put("sign", sign);

            String url = "/secapi/pay/profitsharing";
            String response = wxPay.requestWithCert(url, param, 3000, 3000);
            System.out.println(response);


        } catch (Exception e) {
           e.printStackTrace();
        }
    }

}
