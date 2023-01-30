package com.example.hxds.dr.service.impl;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.common.util.MicroAppUtil;
import com.example.hxds.common.util.PageUtils;
import com.example.hxds.dr.db.dao.DriverDao;
import com.example.hxds.dr.db.dao.DriverSettingsDao;
import com.example.hxds.dr.db.dao.WalletDao;
import com.example.hxds.dr.db.pojo.DriverSettingsEntity;
import com.example.hxds.dr.db.pojo.WalletEntity;
import com.example.hxds.dr.service.DriverService;

import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.iai.v20200303.IaiClient;
import com.tencentcloudapi.iai.v20200303.models.CreatePersonRequest;
import com.tencentcloudapi.iai.v20200303.models.CreatePersonResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class DriverServiceImpl implements DriverService {
    @Value("${tencent.cloud.secretId}")
    private String secretId;

    @Value("${tencent.cloud.secretKey}")
    private String secretKey;

    @Value("${tencent.cloud.face.groupName}")
    private String groupName;

    @Value("${tencent.cloud.face.region}")
    private String region;

    //临时授权对应到token字符串   common模块
    @Resource
    private MicroAppUtil microAppUtil;

    @Resource
    private DriverDao driverDao;

    @Resource
    private DriverSettingsDao settingsDao;

    @Resource
    private WalletDao walletDao;

    @Override
    @Transactional
    @LcnTransaction
    public String registerNewDriver(Map param) {
        String code = MapUtil.getStr(param, "code");//临时授权
        String openId = microAppUtil.getOpenId(code);//变为永久的

        HashMap tempParam = new HashMap() {{
            put("openId", openId);
        }};
        if (driverDao.hasDriver(tempParam) != 0) {
            throw new HxdsException("该微信无法注册");
        }
        param.put("openId", openId);
        //注册司机
        driverDao.registerNewDriver(param);
        String driverId = driverDao.searchDriverId(openId);

        //添加司机设置记录
        DriverSettingsEntity settingsEntity = new DriverSettingsEntity();
        settingsEntity.setDriverId(Long.parseLong(driverId));
        JSONObject json = new JSONObject();
        json.set("autoAccept", false);//自动抢单
        json.set("orientation", "");//定向接单
        json.set("listenService", true);//自动听单
        json.set("orderDistance", 0);//代驾订单预估里程不限制，司机不挑订单
        json.set("rangeDistance", 5);//接收距离司机5公里以内的订单

        settingsEntity.setSettings(json.toString());
        settingsDao.insertDriverSettings(settingsEntity);

        //添加司机钱包记录
        WalletEntity walletEntity = new WalletEntity();
        walletEntity.setDriverId(Long.parseLong(driverId));
        walletEntity.setBalance(new BigDecimal("0"));
        walletEntity.setPassword(null);
        walletDao.insert(walletEntity);
        return driverId;
    }

    @Override
    @Transactional
    @LcnTransaction
    public int updateDriverAuth(Map param) {
        int rows = driverDao.updateDriverAuth(param);
        return rows;
    }

    @Override
    @Transactional
    @LcnTransaction
    public String createDriverFaceModel(long driverId, String photo) {
        //查询员工的姓名和性别
        HashMap map = driverDao.searchDriverNameAndSex(driverId);
        String name = MapUtil.getStr(map, "name");
        String sex = MapUtil.getStr(map, "sex");

        //腾讯云端创建司机面部档案
        Credential cred = new Credential(secretId, secretKey);
        IaiClient client = new IaiClient(cred, region);
        try {
            CreatePersonRequest req = new CreatePersonRequest();
            req.setGroupId(groupName);//人员库ID
            req.setPersonId(driverId + "");//人员ID
            long gender = sex.equals("男") ? 1L : 2L;
            req.setGender(gender);
            req.setQualityControl(4L);//照片质量等级
            req.setUniquePersonControl(4L);//重复人员识别等级
            req.setPersonName(name);//姓名
            req.setImage(photo);//base64图片
            CreatePersonResponse resp = client.CreatePerson(req);
            if (StrUtil.isNotBlank(resp.getFaceId())) {
                //更新司机表archive字段
                int rows = driverDao.updateDriverArchive(driverId);
                if (rows != 1) {
                    return "更新司机归档字段失败";
                }
            }
        } catch (Exception e) {
            log.error("创建腾讯云端司机档案失败", e);
            return "创建腾讯云端司机档案失败";
        }
        //不是return null 因为在http里面如果传输的是json那么值为空的话这个属性直接被抹掉了
        return "";
    }

    @Override
    public HashMap login(String code, String phoneCode) {

        String openId = microAppUtil.getOpenId(code);
        HashMap result = driverDao.login(openId);
        if (result != null) {
            if (result.containsKey("archive")) {
                int temp = MapUtil.getInt(result, "archive");
                boolean archive = (temp == 1) ? true : false;
                result.replace("archive", archive);
            }
            String tel = MapUtil.getStr(result, "tel");
            String realTel = microAppUtil.getTel(phoneCode);
            if (!tel.equals(realTel)) {
                throw new HxdsException("当前手机号与注册手机号不一致");
            }
        }
        return result;
    }

    @Override
    public HashMap searchDriverBaseInfo(long driverId) {
        HashMap result = driverDao.searchDriverBaseInfo(driverId);
        JSONObject summary = JSONUtil.parseObj(MapUtil.getStr(result, "summary"));
        result.replace("summary", summary);
        return result;
    }

    @Override
    public PageUtils searchDriverByPage(Map param) {
        long count = driverDao.searchDriverCount(param);
        ArrayList<HashMap> list = null;
        if (count == 0) {
            list = new ArrayList<>();
        } else {
            list = driverDao.searchDriverByPage(param);
        }
        int start = (Integer) param.get("start");
        int length = (Integer) param.get("length");
        PageUtils pageUtils = new PageUtils(list, count, start, length);
        return pageUtils;
    }

    @Override
    public HashMap searchDriverAuth(long driverId) {
        HashMap result = driverDao.searchDriverAuth(driverId);
        return result;
    }

    @Override
    public HashMap searchDriverRealSummary(long driverId) {
        HashMap map = driverDao.searchDriverRealSummary(driverId);
        return map;
    }

    @Override
    @Transactional
    @LcnTransaction
    public int updateDriverRealAuth(Map param) {
        int rows = driverDao.updateDriverRealAuth(param);
        return rows;
    }

    @Override
    public HashMap searchDriverBriefInfo(long driverId) {
        HashMap map = driverDao.searchDriverBriefInfo(driverId);
        return map;
    }

    @Override
    public String searchDriverOpenId(long driverId) {
        String openId = driverDao.searchDriverOpenId(driverId);
        return openId;
    }


}
