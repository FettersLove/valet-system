package com.example.hxds.odr.quartz;


import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;

@Component
@Slf4j
public class QuartzUtil {

    @Resource
    private Scheduler scheduler;

    /**
     * 添加定时器
     * @param jobDetail 定时器任务对象
     * @param jobName 任务名字
     * @param jobGroupName 任务组名字
     * @param start 开始日期时间
     */
    public void addJob(JobDetail jobDetail, String jobName, String jobGroupName, Date start) {
        try {
            Trigger trigger = TriggerBuilder.newTrigger().withIdentity(jobName, jobGroupName)
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()//普通的定时器执行完就自动退出，不会执行第二遍
                            .withMisfireHandlingInstructionFireNow())//比如定时器设置的是中午12：00执行，11点把Java程序听了，再次开始程序的时候之前没有执行的定时器立即执行
                    .startAt(start).build();
            scheduler.scheduleJob(jobDetail, trigger);
        } catch (SchedulerException e) {
            log.error("定时器添加失败", e);
        }
    }

    /**
     * 查询是否存在定时器
     * @param jobName
     * @param jobGroupName
     * @return
     */
    public boolean checkExists(String jobName, String jobGroupName) {
        TriggerKey key = new TriggerKey(jobName, jobGroupName);
        try {
            boolean bool = scheduler.checkExists(key);
            return bool;
        } catch (Exception e) {
            log.error("定时器查询失败", e);
            return false;
        }
    }

    /**
     * 删除定时器
     * @param jobName
     * @param jobGroupName
     */
    public void deleteJob(String jobName, String jobGroupName) {
        TriggerKey key = new TriggerKey(jobName, jobGroupName);
        try {
            scheduler.resumeTrigger(key);
            scheduler.unscheduleJob(key);
            scheduler.deleteJob(JobKey.jobKey(jobName, jobGroupName));
            log.debug("成功删除" + jobName + "定时器");
        } catch (SchedulerException e) {
            log.error("定时器删除失败", e);
        }
    }

}
