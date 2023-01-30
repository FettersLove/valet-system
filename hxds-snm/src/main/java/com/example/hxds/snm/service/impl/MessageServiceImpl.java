package com.example.hxds.snm.service.impl;

import com.example.hxds.snm.service.MessageService;
import com.example.hxds.snm.db.dao.MessageDao;
import com.example.hxds.snm.db.dao.MessageRefDao;
import com.example.hxds.snm.db.pojo.MessageEntity;
import com.example.hxds.snm.db.pojo.MessageRefEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;

@Service
public class MessageServiceImpl implements MessageService {
    @Resource
    private MessageDao messageDao;

    @Resource
    private MessageRefDao messageRefDao;

    /**
     * 发送者插入一条消息
     * @param entity
     * @return
     */
    @Override
    public String insertMessage(MessageEntity entity) {
        String id = messageDao.insert(entity);
        return id;
    }

    /**
     * 根据id查询消息正文
     * @param id
     * @return
     */
    @Override
    public HashMap searchMessageById(String id) {
        HashMap map = messageDao.searchMessageById(id);
        return map;
    }

    /**
     * 接收者插入一条接收消息
     * @param entity
     * @return
     */
    @Override
    public String insertRef(MessageRefEntity entity) {
        String id = messageRefDao.insert(entity);
        return id;
    }

    /**
     * 接收者查询未读取的记录条数
     * @param userId
     * @param identity
     * @return
     */
    @Override
    public long searchUnreadCount(long userId, String identity) {
        long count = messageRefDao.searchUnreadCount(userId, identity);
        return count;
    }

    /**
     * 接收者查询
     * @param userId
     * @param identity
     * @return
     */
    @Override
    public long searchLastCount(long userId, String identity) {
        long count = messageRefDao.searchLastCount(userId, identity);
        return count;
    }

    /**
     * 更新未读取条数
     * @param id
     * @return
     */
    @Override
    public long updateUnreadMessage(String id) {
        long rows = messageRefDao.updateUnreadMessage(id);
        return rows;
    }

    /**
     * 删除消息
     * @param id
     * @return
     */
    @Override
    public long deleteMessageRefById(String id) {
        long rows = messageRefDao.deleteMessageRefById(id);
        return rows;
    }

    /**
     * 删除具体某个接收者的消息
     * @param userId
     * @param identity
     * @return
     */
    @Override
    public long deleteUserMessageRef(long userId, String identity) {
        long rows = messageRefDao.deleteUserMessageRef(userId, identity);
        return rows;
    }
}

