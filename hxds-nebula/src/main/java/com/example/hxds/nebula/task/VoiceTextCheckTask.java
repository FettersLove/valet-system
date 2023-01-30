package com.example.hxds.nebula.task;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.example.hxds.common.exception.HxdsException;
import com.example.hxds.nebula.db.dao.OrderMonitoringDao;
import com.example.hxds.nebula.db.dao.OrderVoiceTextDao;
import com.example.hxds.nebula.db.pojo.OrderMonitoringEntity;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.ciModel.auditing.AuditingJobsDetail;
import com.qcloud.cos.model.ciModel.auditing.SectionInfo;
import com.qcloud.cos.model.ciModel.auditing.TextAuditingRequest;
import com.qcloud.cos.model.ciModel.auditing.TextAuditingResponse;
import com.qcloud.cos.region.Region;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.tms.v20201229.TmsClient;
import com.tencentcloudapi.tms.v20201229.models.TextModerationRequest;
import com.tencentcloudapi.tms.v20201229.models.TextModerationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.swing.plaf.ListUI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Component
@Slf4j
public class VoiceTextCheckTask {
    @Value("${tencent.cloud.appId}")
    private String appId;

    @Value("${tencent.cloud.secretId}")
    private String secretId;

    @Value("${tencent.cloud.secretKey}")
    private String secretKey;

    @Value("${tencent.cloud.bucket-public}")
    private String bucketPublic;

    @Resource
    private OrderVoiceTextDao orderVoiceTextDao;

    @Resource
    private OrderMonitoringDao orderMonitoringDao;

    @Async
    @Transactional                      //审核文本
    public void checkText(long orderId, String content, String uuid) {
        String label = "Normal"; //审核结果
        String suggestion = "Pass"; //后续建议

        //后续建议模板
        HashMap<String, String> template = new HashMap() {{
            put("0", "Pass");
            put("1", "Block");
            put("2", "Review");
        }};

        if(StrUtil.isNotBlank(content)){
            COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
            Region region = new Region("ap-nanjing");
            ClientConfig config=new ClientConfig(region);
            COSClient client = new COSClient(cred, config);
            //1.创建任务请求对象
            TextAuditingRequest request = new TextAuditingRequest();
            //2.添加请求参数 参数详情请见api接口文档
            request.setBucketName(bucketPublic);
            //2.1.2或直接设置请求内容,文本内容的Base64编码
            request.getInput().setContent(Base64.encode(content));
            //2.2设置审核类型参数
            request.getConf().setDetectType("all");

            //3.调用接口,获取任务响应对象
            TextAuditingResponse response=client.createAuditingTextJobs(request);
            AuditingJobsDetail detail = response.getJobsDetail();
            String state = detail.getState();//成功或者失败
            ArrayList keywords=new ArrayList();
            if("Success".equals(state)){
                label=detail.getLabel();//检测结果
                String result = detail.getResult();//后续建议
                suggestion=template.get(result);
                List<SectionInfo> list=detail.getSectionList();//违规关键字
                for (SectionInfo info:list){
                    String keywords_1=info.getPornInfo().getKeywords();
                    String keywords_2=info.getIllegalInfo().getKeywords();
                    String keywords_3=info.getAbuseInfo().getKeywords();
                    if(keywords_1.length()>0){
                        List temp=Arrays.asList(keywords_1.split(","));
                        keywords.addAll(temp);
                    }
                    if(keywords_2.length()>0){
                        List temp=Arrays.asList(keywords_2.split(","));
                        keywords.addAll(temp);
                    }
                    if(keywords_3.length()>0){
                        List temp=Arrays.asList(keywords_3.split(","));
                        keywords.addAll(temp);
                    }
                }
            }
            Long id = orderVoiceTextDao.searchIdByUuid(uuid);
            if(id==null){
                throw new HxdsException("没有找到代驾语音文本记录");
            }
            HashMap param=new HashMap();
            param.put("id", id);
            param.put("label", label);
            param.put("suggestion", suggestion);
                                    //拼接成字符串
            param.put("keywords", ArrayUtil.join(keywords.toArray(), ","));

            //更新数据表中该文本的审核结果
            int rows = orderVoiceTextDao.updateCheckResult(param);
            if(rows!=1){
                throw new HxdsException("更新内容检查结果失败");
            }

            //查询该订单中有多少个录音文本和需要人工审核的文本
            HashMap map = orderMonitoringDao.searchOrderRecordsAndReviews(orderId);
            id = MapUtil.getLong(map, "id");
            Integer records = MapUtil.getInt(map, "records");
            Integer reviews = MapUtil.getInt(map, "reviews");

            OrderMonitoringEntity entity=new OrderMonitoringEntity();
            entity.setId(id);
            entity.setOrderId(orderId);
            entity.setRecords(records+1);
            if(suggestion.equals("Review")){
                entity.setReviews(reviews+1);
            }
            if(suggestion.equals("Block")){
                entity.setSafety("danger");
            }
            rows=orderMonitoringDao.updateOrderMonitoring(entity);
            if(rows!=1){
                throw new HxdsException("更新订单监控记录失败");
            }
        }

    }
}
