package com.example.hxds.snm.db.pojo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.util.Date;

@Data
@Document(collection = "message")//保存在message集合里面
public class MessageEntity implements Serializable {
    @Id
    private String _id;

    @Indexed
    private Long senderId;

    private String senderIdentity;

    private String senderPhoto = "http://static-12583863851.cos.ap-beijing.myqcloud.com/img/System.jpg";

    private String senderName;

    private String msg;

    @Indexed
    private Date sendTime;
}
