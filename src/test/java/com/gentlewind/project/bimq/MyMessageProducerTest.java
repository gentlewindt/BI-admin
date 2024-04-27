package com.gentlewind.project.bimq;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MyMessageProducerTest {

    @Resource
    private MyMessageProducer myMessageProducer;

    @Test
    /**
     * 在测试方法中，调用消息生产者的 sendMessage 方法，发送一条消息
     *
     * 参数1：交换机名称为 "code_exchange"，表示将消息发送到该交换机
     * 参数2：路由键为 "my_routingKey"，表示消息将通过该路由键进行路由
     * 参数3：消息内容为 "你好呀"，即要发送的具体消息内容
     */
    void sendMessage() {
        // 调用消息生产者的 sendMessage 方法发送消息
        myMessageProducer.sendMessage("code_exchange", "my_routingKey", "你好呀");
    }
}