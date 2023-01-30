package com.example.hxds.dr.db.dao;

import com.example.hxds.dr.db.pojo.DriverSettingsEntity;

import java.util.Map;

public interface DriverSettingsDao {
    public int insertDriverSettings(DriverSettingsEntity entity);

    public String searchDriverSettings(long driverId);
}




