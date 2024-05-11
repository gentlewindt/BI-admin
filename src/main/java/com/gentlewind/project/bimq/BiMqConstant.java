package com.gentlewind.project.bimq;

/**
 * rabbitmq 常量
 *
 * 将常量存放在一个包的接口中(创建一个专门用于存放常量的包，并在该包中定义接口或类来存放相关常量)
 */
public interface BiMqConstant {
    String BI_EXCHANGE_NAME = "bi_exchange";

    String BI_QUEUE_NAME = "bi_queue";

    String BI_ROUTING_KEY = "bi_routingKey";
}
