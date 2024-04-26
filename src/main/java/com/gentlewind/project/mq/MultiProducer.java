package com.gentlewind.project.mq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;

import java.util.Scanner;

public class MultiProducer {
    // 定义要使用的队列名称
  private static final String TASK_QUEUE_NAME = "multi_queue";

  public static void main(String[] argv) throws Exception {
    // 创建一个连接工厂
    ConnectionFactory factory = new ConnectionFactory();
    // 设置RabbitMQ服务的主机名
    factory.setHost("localhost");
    try (Connection connection = factory.newConnection();
         // 创建一个新的频道
         Channel channel = connection.createChannel()) {
        // 声明队列，如果队列不存在，则创建队列
        // 参数1：队列名称
        // 参数2：是否持久化队列，false表示不持久化，MQ停掉数据就丢失
        // 参数3：是否私有队列，false表示允许多个 consumer 向该队列投递消息，true表示独占
        // 参数4：是否自动删除队列，false表示连接停掉后不自动删除队列
        // 参数5：其他参数
        channel.queueDeclare(TASK_QUEUE_NAME, true, false, false, null);

        // 创建一个输入扫描器，用于读取控制台输入
        Scanner scanner = new Scanner(System.in);
        // 使用循环，每当用户在控制台输入一行文本，就将其作为消息发送到队列中
        while (scanner.hasNext()){
            // 读取用户在控制台输入的下一行文本
            String message = scanner.nextLine();
            // 发布消息到队列，设置消息持久化
            channel.basicPublish("", TASK_QUEUE_NAME,
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    message.getBytes("UTF-8"));
            // 输出到控制台，表示消息已发送
            System.out.println(" [x] Sent '" + message + "'");
        }

        // 接收用户输入作为发送的消息
        String message = String.join(" ", argv);

        // 信息持久化，系统重启后消息也不会丢失
        channel.basicPublish("", TASK_QUEUE_NAME,
                MessageProperties.PERSISTENT_TEXT_PLAIN,
                message.getBytes("UTF-8"));
        System.out.println(" [x] Sent '" + message + "'");
    }
  }

}