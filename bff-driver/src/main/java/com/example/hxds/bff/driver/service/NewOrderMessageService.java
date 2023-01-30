package com.example.hxds.bff.driver.service;

import com.example.hxds.bff.driver.controller.form.ClearNewOrderQueueForm;
import com.example.hxds.bff.driver.controller.form.ReceiveNewOrderMessageForm;

import java.util.ArrayList;
import java.util.List;

public interface NewOrderMessageService {
    public void clearNewOrderQueue(ClearNewOrderQueueForm form);

    public ArrayList receiveNewOrderMessage(ReceiveNewOrderMessageForm form);
}
