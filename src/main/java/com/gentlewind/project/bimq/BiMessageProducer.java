package com.gentlewind.project.bimq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component // 标记该类为一个组件，让Spring能够扫描并将其注册为bean
public class BiMessageProducer {

    // 使用@Resource注解对rabbitTemplate进行依赖注入
    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送消息
     *
     * @param message  消息内容，要发送的具体消息
     */
    public void sendMessage(String message){
        // convertAndSend方法：将消息内容转换为字节数组并发送到指定的交换机和路由键
        // 参数1：交换机名称
        // 参数2：路由键
        // 参数3：消息内容
        rabbitTemplate.convertAndSend(BiMqConstant.BI_EXCHANGE_NAME , BiMqConstant.BI_ROUTING_KEY , message);
    }




}
