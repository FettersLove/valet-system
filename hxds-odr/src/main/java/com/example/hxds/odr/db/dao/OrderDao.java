package com.example.hxds.odr.db.dao;


import com.example.hxds.odr.db.pojo.OrderEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public interface OrderDao {
    public HashMap searchDriverTodayBusinessData(long driverId);

    public int insert(OrderEntity entity);

    public String searchOrderIdByUUID(String uuid);

    public int acceptNewOrder(Map param);

    public HashMap searchDriverExecuteOrder(Map param);

    public Integer searchOrderStatus(Map param);

    public int deleteUnAcceptOrder(Map param);

    public HashMap searchDriverCurrentOrder(long driverId);

    public Long hasCustomerUnFinishedOrder(long customerId);

    public HashMap hasCustomerUnAcceptOrder(long customerId);

    public HashMap searchOrderForMoveById(Map param);

    public int updateOrderStatus(Map param);

    public ArrayList<String> searchOrderStartLocationIn30Days();

    public long searchOrderCount(Map param);

    public ArrayList<HashMap> searchOrderByPage(Map param);

    public HashMap searchOrderContent(long orderId);

    public int updateOrderMileageAndFee(Map param);

    public long validDriverOwnOrder(Map param);

    public HashMap searchSettlementNeedData(long orderId);

    public HashMap searchOrderById(Map param);

    public HashMap validCanPayOrder(Map param);

    public int updateOrderPrepayId(Map param);

    public HashMap searchOrderIdAndStatus(String uuid);

    public HashMap searchDriverIdAndIncentiveFee(String uuid);

    public int updateOrderPayIdAndStatus(Map param);

    public int finishOrder(String uuid);

    public HashMap searchUuidAndStatus(long orderId);

    public int updateOrderAboutPayment(Map param);

    public long validDriverAndCustomerOwnOrder(Map param);

    public ArrayList<HashMap> searchDriverOrderByPage(Map param);

    public long searchDriverOrderCount(Map param);

    public ArrayList<HashMap> searchCustomerOrderByPage(Map param);

    public long searchCustomerOrderCount(Map param);
}




