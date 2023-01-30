package com.example.hxds.dr.db.dao;

import com.example.hxds.dr.db.pojo.WalletEntity;

import java.util.HashMap;
import java.util.Map;

public interface WalletDao {

    public int insert(WalletEntity entity);

    public int updateWalletBalance(Map param);
}