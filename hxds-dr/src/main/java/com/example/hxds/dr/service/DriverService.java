package com.example.hxds.dr.service;

import com.example.hxds.common.util.PageUtils;

import java.util.HashMap;
import java.util.Map;

public interface DriverService {

    //注册新司机
    public String registerNewDriver(Map param);

    public int updateDriverAuth(Map param);

    public String createDriverFaceModel(long driverId, String photo);

    HashMap login(String code, String phoneCode);

    public HashMap searchDriverBaseInfo(long driverId);

    public PageUtils searchDriverByPage(Map param);

    public HashMap searchDriverAuth(long driverId);

    public HashMap searchDriverRealSummary(long driverId);

    public int updateDriverRealAuth(Map param);

    public HashMap searchDriverBriefInfo(long driverId);

    public String searchDriverOpenId(long driverId);

}
