package com.example.hxds.odr.service.impl;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.codingapi.txlcn.tc.annotation.LcnTransaction;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.common.util.PageUtils;
import com.example.hxds.common.wxpay.MyWXPayConfig;
import com.example.hxds.common.wxpay.WXPay;
import com.example.hxds.common.wxpay.WXPayUtil;
import com.example.hxds.odr.controller.form.TransferForm;
import com.example.hxds.odr.db.dao.OrderBillDao;
import com.example.hxds.odr.db.dao.OrderDao;
import com.example.hxds.odr.db.pojo.OrderBillEntity;
import com.example.hxds.odr.db.pojo.OrderEntity;
import com.example.hxds.odr.feign.DrServiceApi;
import com.example.hxds.odr.quartz.QuartzUtil;
import com.example.hxds.odr.quartz.job.HandleProfitsharingJob;
import com.example.hxds.odr.service.OrderService;
import org.checkerframework.checker.units.qual.A;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class OrderServiceImpl implements OrderService {
    @Resource
    private OrderDao orderDao;

    @Resource
    private OrderBillDao orderBillDao;

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private DrServiceApi drServiceApi;

    @Resource
    private QuartzUtil quartzUtil;

    @Resource
    private MyWXPayConfig myWXPayConfig;

    @Override
    public HashMap searchDriverTodayBusinessData(long driverId) {
        HashMap result = orderDao.searchDriverTodayBusinessData(driverId);
        String duration = MapUtil.getStr(result, "duration");
        if (duration == null) {
            duration = "0";
        }
        result.replace("duration", duration);

        String income = MapUtil.getStr(result, "income");
        if (income == null) {
            income = "0.00";
        }
        result.replace("income", income);
        return result;
    }

    @Override
    @Transactional
    @LcnTransaction
    public String insertOrder(OrderEntity orderEntity, OrderBillEntity orderBillEntity) {
        //插入订单记录
        int rows = orderDao.insert(orderEntity);
        if (rows == 1) {
            String id = orderDao.searchOrderIdByUUID(orderEntity.getUuid());
            //插入订单消费记录
            orderBillEntity.setOrderId(Long.parseLong(id));
            rows = orderBillDao.insert(orderBillEntity);
            if (rows == 1) {
                //往redis中插入缓存，配合redis事务用于司机抢单，避免多个司机同时抢单成功
                redisTemplate.opsForValue().set("order#" + id, "none");
                //缓存16分钟
                redisTemplate.expire("order#" + id, 16, TimeUnit.MINUTES);
                return id;
            } else {
                throw new HxdsException("保存新订单费用失败");
            }
        } else {
            throw new HxdsException("保存新订单失败");
        }
    }

    @Override
    @Transactional
    @LcnTransaction
    public String acceptNewOrder(long driverId, long orderId) {
        if (!redisTemplate.hasKey("order#" + orderId)) {
            return "抢单失败";
        }
        redisTemplate.execute(new SessionCallback() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                //获取新订单记录的Version
                operations.watch("order#" + orderId);
                //本地缓存redis的操作
                operations.multi();
                //把新订单缓存的Value设置成抢单司机的ID
                operations.opsForValue().set("order#" + orderId, driverId);
                //执行redis事务，如果事务提交失败会抛出异常
                return operations.exec();
            }
        });
        //抢单成功之后，删除redis中的新订单，避免让其他司机参与抢单
        redisTemplate.delete("order#" + orderId);
        //更新订单记录，添加上接单司机ID和接单事件
        HashMap param = new HashMap() {{
            put("driverId", driverId);
            put("orderId", orderId);
        }};
        int rows = orderDao.acceptNewOrder(param);
        if (rows != 1) {
            throw new HxdsException("接单失败，无法更新订单记录");
        }
        return "接单成功";
    }

    @Override
    public HashMap searchDriverExecuteOrder(Map param) {
        HashMap map = orderDao.searchDriverExecuteOrder(param);
        return map;
    }

    @Override
    public Integer searchOrderStatus(Map param) {
        Integer status = orderDao.searchOrderStatus(param);
        if (status == null) {
//            throw new HxdsException("没有查询到数据，请核对查询条件");
            status = 0;
        }
        return status;
    }

    @Override
    @Transactional
    @LcnTransaction
    public String deleteUnAcceptOrder(Map param) {
        long orderId = MapUtil.getLong(param, "orderId");
        if (!redisTemplate.hasKey("order#" + orderId)) {
            return "订单取消失败";
        }
        redisTemplate.execute(new SessionCallback() {
            @Override
            public Object execute(RedisOperations operations) throws DataAccessException {
                operations.watch("order#" + orderId);
                operations.multi();
                operations.opsForValue().set("order#" + orderId, "none");
                return operations.exec();
            }
        });

        redisTemplate.delete("order#" + orderId);
        int rows = orderDao.deleteUnAcceptOrder(param);
        if (rows != 1) {
            return "订单取消失败";
        }
        rows = orderBillDao.deleteUnAcceptOrderBill(orderId);
        if (rows != 1) {
            return "账单取消失败";
        }
        return "订单取消成功";
    }

    @Override
    public HashMap searchDriverCurrentOrder(long driverId) {
        HashMap map = orderDao.searchDriverCurrentOrder(driverId);
        return map;
    }

    @Override
    public HashMap hasCustomerCurrentOrder(long customerId) {
        HashMap result = new HashMap();
        HashMap map = orderDao.hasCustomerUnAcceptOrder(customerId);
        result.put("hasCustomerUnAcceptOrder", map != null);
        result.put("unAcceptOrder", map);
        Long id = orderDao.hasCustomerUnFinishedOrder(customerId);
        result.put("hasCustomerUnFinishedOrder", id != null);
        result.put("unFinishedOrder", id);
        return result;
    }

    @Override
    public HashMap searchOrderForMoveById(Map param) {
        HashMap map = orderDao.searchOrderForMoveById(param);
        return map;
    }


    @Override
    @Transactional
    @LcnTransaction
    public int arriveStartPlace(Map param) {
        long orderId = MapUtil.getLong(param, "orderId");
        // //乘客端确认司机到达上车点
        redisTemplate.opsForValue().set("order_driver_arrivied#" + orderId, "1");
        int rows = orderDao.updateOrderStatus(param);
        if (rows != 1) {
            throw new HxdsException("更新订单状态失败");
        }
        return rows;
    }

    @Override
    public boolean confirmArriveStartPlace(long orderId) {
        String key = "order_driver_arrivied#" + orderId;
        if (redisTemplate.hasKey(key) && redisTemplate.opsForValue().get(key).toString().equals("1")) {
            redisTemplate.opsForValue().set(key, "2");
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    @LcnTransaction
    public int startDriving(Map param) {
        long orderId = MapUtil.getLong(param, "orderId");
        String key = "order_driver_arrivied#" + orderId;
//        if(redisTemplate.hasKey(key)&&redisTemplate.opsForValue().get(key).toString().equals("2")){
//            redisTemplate.delete(key);
        int rows = orderDao.updateOrderStatus(param);
        if (rows != 1) {
            throw new HxdsException("更新订单状态失败");
        }
        return rows;
//        }
//        return 0;
    }

    @Override
    @Transactional
    @LcnTransaction
    public int updateOrderStatus(Map param) {
        int rows = orderDao.updateOrderStatus(param);
        if (rows != 1) {
            throw new HxdsException("更新取消订单记录失败");
        }
        return rows;
    }

    @Override
    public ArrayList<HashMap> searchOrderStartLocationIn30Days() {
        ArrayList<String> list = orderDao.searchOrderStartLocationIn30Days();
        ArrayList<HashMap> result = new ArrayList<>();
        list.forEach(location -> {
            JSONObject json = JSONUtil.parseObj(location);
            String latitude = json.getStr("latitude");
            String longitude = json.getStr("longitude");
            latitude = latitude.substring(0, latitude.length() - 4);
            latitude += "0001";
            longitude = longitude.substring(0, longitude.length() - 4);
            longitude += "0001";
            HashMap map = new HashMap();
            map.put("latitude", latitude);
            map.put("longitude", longitude);
            result.add(map);
        });
        return result;
    }


    @Override
    public PageUtils searchOrderByPage(Map param) {
        long count = orderDao.searchOrderCount(param);
        ArrayList<HashMap> list = null;
        if (count == 0) {
            list = new ArrayList<>();
        } else {
            list = orderDao.searchOrderByPage(param);
        }
        int start = MapUtil.getInt(param, "start");
        int length = MapUtil.getInt(param, "length");
        PageUtils pageUtils = new PageUtils(list, count, start, length);
        return pageUtils;
    }

    @Override
    public HashMap searchOrderContent(long orderId) {
        HashMap map = orderDao.searchOrderContent(orderId);
        JSONObject startPlaceLocation = JSONUtil.parseObj(MapUtil.getStr(map, "startPlaceLocation"));
        JSONObject endPlaceLocation = JSONUtil.parseObj(MapUtil.getStr(map, "endPlaceLocation"));
        map.replace("startPlaceLocation", startPlaceLocation);
        map.replace("endPlaceLocation", endPlaceLocation);
        return map;
    }

    @Override
    public boolean validDriverOwnOrder(Map param) {
        long count = orderDao.validDriverOwnOrder(param);
        return count == 1 ? true : false;
    }

    @Override
    public HashMap searchSettlementNeedData(long orderId) {
        HashMap map = orderDao.searchSettlementNeedData(orderId);
        return map;
    }

    @Override
    public HashMap searchOrderById(Map param) {
        HashMap map = orderDao.searchOrderById(param);
        String startPlaceLocation = MapUtil.getStr(map, "startPlaceLocation");
        String endPlaceLocation = MapUtil.getStr(map, "endPlaceLocation");
        map.replace("startPlaceLocation", JSONUtil.parseObj(startPlaceLocation));
        map.replace("endPlaceLocation", JSONUtil.parseObj(endPlaceLocation));
        return map;
    }

    @Override
    public HashMap validCanPayOrder(Map param) {
        HashMap map = orderDao.validCanPayOrder(param);
        if (map == null || map.size() == 0) {
            throw new HxdsException("订单无法支付");
        }
        return map;
    }

    @Override
    @Transactional
    @LcnTransaction
    public int updateOrderPrepayId(Map param) {
        int rows = orderDao.updateOrderPrepayId(param);
        if (rows != 1) {
            throw new HxdsException("更新预支付订单ID失败");
        }
        return rows;
    }

    @Override
    @Transactional
    @LcnTransaction
    public void handlePayment(String uuid, String payId, String driverOpenId, String payTime) {
        HashMap map = orderDao.searchOrderIdAndStatus(uuid);
        int status = MapUtil.getInt(map, "status");
        /**
         * 如果微信平台的消息队列出现故障导致没有收到通知消息
         * 我们的代码是乘客付款成功后，主动发起ajax请求去微信支付平台查询结果
         * 后面微信平台恢复之后就会去查询
         */
        if (status == 7) {
            return;
        }

        HashMap param = new HashMap() {{
            put("uuid", uuid);
            put("payId", payId);
            put("payTime", payTime);
        }};
        //更新订单记录的payid、状态和付款时间
        int rows = orderDao.updateOrderPayIdAndStatus(param);
        if (rows != 1) {
            throw new HxdsException("更新支付订单ID失败");
        }

        //查询系统奖励费
        map = orderDao.searchDriverIdAndIncentiveFee(uuid);
        String incentiveFee = MapUtil.getStr(map, "incentiveFee");
        long driverId = MapUtil.getLong(map, "driverId");
        //如果大于0
        if (new BigDecimal(incentiveFee).compareTo(new BigDecimal("0.00")) == 1) {
            TransferForm form = new TransferForm();
            form.setUuid(IdUtil.simpleUUID());
            form.setAmount(incentiveFee);
            form.setDriverId(driverId);
            form.setType((byte) 2);
            form.setRemark("系统奖励费");
            drServiceApi.transfer(form);
        }
        //先判断是否有这个定时器，如果存在就不需要再次创建了
        //因为我们不能百分之百收到定时器消息，乘客端发起ajax请求
        if (quartzUtil.checkExists(uuid, "代驾单分账任务组")
                || quartzUtil.checkExists(uuid, "查询代驾单分账任务组")) {
            return;
        }
        JobDetail jobDetail = JobBuilder.newJob(HandleProfitsharingJob.class).build();
        Map dataMap = jobDetail.getJobDataMap();
        dataMap.put("uuid", uuid);
        dataMap.put("driverOpenId", driverOpenId);
        dataMap.put("payId", payId);
        //2min钟后执行定时器
        Date executeDate = new DateTime().offset(DateField.MINUTE, 2);
        quartzUtil.addJob(jobDetail, uuid, "代驾单分账任务组", executeDate);

        rows = orderDao.finishOrder(uuid);
        if (rows != 1) {
            throw new HxdsException("更新订单结束状态失败");
        }
    }

    @Override
    @Transactional
    @LcnTransaction
    public String updateOrderAboutPayment(Map param) {
        long orderId = MapUtil.getLong(param, "orderId");
        /**
         * 查询订单状态
         * 因为有可能web方法先收到了付款结果通知消息，把订单状态改成了7、8状态
         * 所以我们要先查询订单状态
         */
        HashMap map = orderDao.searchUuidAndStatus(orderId);
        String uuid = MapUtil.getStr(map, "uuid");
        int status = MapUtil.getInt(map, "status");
        if (status == 7 || status == 8) {
            return "付款成功";
        }

        //查询支付结果的参数
        map.clear();
        map.put("appid", myWXPayConfig.getAppID());
        map.put("mch_id", myWXPayConfig.getMchID());
        map.put("out_trade_no", uuid);
        map.put("nonce_str", WXPayUtil.generateNonceStr());

        try {
            String sign = WXPayUtil.generateSignature(map, myWXPayConfig.getKey());
            map.put("sign", sign);

            WXPay wxPay = new WXPay(myWXPayConfig);
            Map<String, String> result = wxPay.orderQuery(map);
            String returnCode = result.get("return_code");
            String resultCode = result.get("result_code");
            if ("SUCCESS".equals(returnCode) && "SUCCESS".equals(resultCode)) {
                String tradeState = result.get("trade_state");
                if ("SUCCESS".equals(tradeState)) {
                    String driverOpenId = result.get("attach");
                    String payId = result.get("transaction_id");
                    String payTime = new DateTime(result.get("time_end"), "yyyyMMddHHmmss").toString("yyyy-MM-dd HH:mm:ss");
                    //更新订单相关付款信息和状态
                    param.put("payId", payId);
                    param.put("payTime", payTime);

                    int rows = orderDao.updateOrderAboutPayment(param);
                    if (rows != 1) {
                        throw new HxdsException("更新订单相关付款信息失败");
                    }

                    //查询系统奖励费用
                    map = orderDao.searchDriverIdAndIncentiveFee(uuid);
                    String incentiveFee = MapUtil.getStr(map, "incentiveFee");
                    long driverId = MapUtil.getLong(map, "driverId");
                    if (new BigDecimal(incentiveFee).compareTo(new BigDecimal("0.00")) == 1) {
                        TransferForm form = new TransferForm();
                        form.setUuid(IdUtil.simpleUUID());
                        form.setAmount(incentiveFee);
                        form.setDriverId(driverId);
                        form.setType((byte) 2);
                        form.setRemark("系统奖励费");
                        drServiceApi.transfer(form);
                    }
                    //执行分账
                    if (quartzUtil.checkExists(uuid, "代驾单分账任务组")
                            || quartzUtil.checkExists(uuid, "查询代驾单分账任务组")) {
                        return "付款成功";
                    }
                    JobDetail jobDetail = JobBuilder.newJob(HandleProfitsharingJob.class).build();
                    Map dataMap = jobDetail.getJobDataMap();
                    dataMap.put("uuid", uuid);
                    dataMap.put("driverOpenId", driverOpenId);
                    dataMap.put("payId", payId);
                    Date executeDate = new DateTime().offset(DateField.MINUTE, 2);
                    quartzUtil.addJob(jobDetail, uuid, "代驾单分账任务组", executeDate);

                    rows = orderDao.finishOrder(uuid);
                    if (rows != 1) {
                        throw new HxdsException("更新订单结束状态失败");
                    }

                    return "付款成功";
                } else {
                    return "付款异常";
                }
            } else {
                return "付款异常";
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new HxdsException("更新订单相关付款信息失败");
        }
    }

    @Override
    public PageUtils searchDriverOrderByPage(Map param) {
        long count = orderDao.searchDriverOrderCount(param);
        ArrayList<HashMap> list = null;
        if (count > 0) {
            list = orderDao.searchDriverOrderByPage(param);
        } else {
            list = new ArrayList<>();
        }
        int start = MapUtil.getInt(param, "start");
        int length = MapUtil.getInt(param, "length");
        PageUtils pageUtils = new PageUtils(list, count, start, length);
        return pageUtils;
    }

    @Override
    public PageUtils searchCustomerOrderByPage(Map param) {
        long count = orderDao.searchCustomerOrderCount(param);
        ArrayList<HashMap> list = null;
        if (count > 0) {
            list = orderDao.searchCustomerOrderByPage(param);
        } else {
            list = new ArrayList<>();
        }
        int start = MapUtil.getInt(param, "start");
        int length = MapUtil.getInt(param, "length");
        PageUtils pageUtils = new PageUtils(list, count, start, length);
        return pageUtils;
    }




}
