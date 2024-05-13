package com.gentlewind.project.bimq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 用于创建测试程序用到的交换机和队列（只用在程序启动前执行一次）
 */
public class MqInitMain {

    public static void main(String[] args) {
        try {
            // 创建连接工厂
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
            // 创建连接
            Connection connection = factory.newConnection();
            // 创建通道
            Channel channel = connection.createChannel();
            // 定义交换机的名称为"code_exchange"
            String EXCHANGE_NAME = "code_exchange";
            // 定义死信交换机
            String EXCHANGE_DEAD = "dead_exchange";

            // 声明交换机，指定交换机类型为 direct
            channel.exchangeDeclare(EXCHANGE_NAME, "direct");
            channel.exchangeDeclare(EXCHANGE_DEAD, "direct");

            // 创建队列，随机分配一个队列名称
            String queueName = "code_queue";
            Map<String, Object> queueArgs = new HashMap<>();
            queueArgs.put("x-message-ttl", 60000); // 过期时间为 60 秒
            // 声明队列,参数解释：queueDeclare(String queue, boolean durable, boolean exclusive, boolean autoDelete, Map<String, Object> arguments)
            // durable: 持久化队列（重启后依然存在）
            // exclusive: 排他性队列（仅限此连接可见，连接关闭后队列删除）
            // autoDelete: 自动删除队列（无消费者时自动删除）
            channel.queueDeclare(queueName, true, false, false, queueArgs);
            String deadLetterRoutingKey = ""; // 空字符串，表示所有过期消息都会路由到死信交换机
            Map<String, Object> deadArgs = new HashMap<>();
            deadArgs.put("x-dead-letter-exchange", EXCHANGE_DEAD);
            deadArgs.put("x-dead-letter-routing-key", deadLetterRoutingKey);
            // 将队列与交换机进行绑定,如果消息在原始队列中无法处理，它们会根据deadArgs被路由到死信队列。
            channel.queueBind(queueName, EXCHANGE_NAME, "my_routingKey",deadArgs);

            // 创建一个死信队列
            String queueDeadName = "dead_queue";
            // 声明死信队列，并将其绑定到死信交换机。
            channel.queueDeclare(queueDeadName,true,false,false,null);
            channel.queueBind(queueDeadName,EXCHANGE_DEAD,"");
            // 为什么绑定""?
            // 直连交换机需要消息的路由键与队列绑定的绑定键完全匹配，才能将消息路由到相应的队列。
            // 但当绑定键为空字符串时，这个队列实际上会接收所有发送到该交换机且没有指定特定路由键的消息。
            // 这是因为空字符串可以与任何路由键匹配，相当于一个“通配符”。

        } catch (Exception e) {
            // 异常处理
        }
    }
}