package com.gentlewind.project.bimq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component // 标记该类为一个组件，让Spring能够扫描并将其注册为bean
public class MyMessageProducer {

    // 使用@Resource注解对rabbitTemplate进行依赖注入
    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送消息
     *
     * @param exchange      交换机名称，指定消息要发送的交换机
     * @param routingKey    路由键，指定消息要根据什么规则路由到目标队列
     * @param message       消息内容，要发送的具体消息
     */
    public void sendMessage(String exchange , String routingKey , String message){
        rabbitTemplate.convertAndSend(exchange , routingKey , message);
    }




}
