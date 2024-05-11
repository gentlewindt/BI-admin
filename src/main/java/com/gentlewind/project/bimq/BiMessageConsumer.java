package com.gentlewind.project.bimq;

import com.gentlewind.project.common.ErrorCode;
import com.gentlewind.project.constant.CommonConstant;
import com.gentlewind.project.exception.BusinessException;
import com.gentlewind.project.manager.AiManager;
import com.gentlewind.project.model.entity.Chart;
import com.gentlewind.project.service.ChartService;
import com.gentlewind.project.utils.ExcelUtils;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;

@Component
@Slf4j
public class BiMessageConsumer {

    @Resource
    private ChartService chartService;

    @Resource
    private AiManager aiManager;
    

    /**
     * 接收消息的方法（消费消息）
     *
     * @param message     接收到的消息内容，是一个字符串类型
     * @param channel     消息所在的通道，可以通过该通道与 RabbitMQ 进行交互，例如手动确认消息、拒绝消息等，是与Rabbit服务器通信的通道
     * @param deliveryTag 消息的投递标签，用于唯一标识一条消息
     */
    // 使用@SneakyThrows注解简化异常处理
    @SneakyThrows
    // 使用@RabbitListener注解指定要监听的队列名称为"code_queue"，并设置消息的确认机制为手动确认
    @RabbitListener(queues = {BiMqConstant.BI_QUEUE_NAME}, ackMode = "MANUAL")
    // @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag是一个方法参数注解,用于从消息头中获取投递标签(deliveryTag),
    // 在RabbitMQ中,每条消息都会被分配一个唯一的投递标签，用于标识该消息在通道中的投递状态和顺序。通过使用@Header(AmqpHeaders.DELIVERY_TAG)注解,可以从消息头中提取出该投递标签,并将其赋值给long deliveryTag参数。
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("receiveMessage message = {}", message);
        if (StringUtils.isBlank(message)) {
            /**
             * basicNack(deliveryTag, false, false);:
             * deliveryTag: 参数传递了要拒绝的消息的deliveryTag。
             * false: 第二个参数表示是否批量拒绝。如果为true，则会拒绝从当前deliveryTag开始的所有未确认消息。由于这里是false，所以只拒绝当前deliveryTag所对应的消息。
             * false: 第三个参数表示是否重新投递消息。如果为true，RabbitMQ会尝试将消息重新放入队列，让其他消费者有机会处理。这里是false，意味着消息不会被重新投递，而是被废弃。
             */
            channel.basicNack(deliveryTag, false, false); // 如果更新失败，拒绝当前消息，让消息重新进入队列
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "消息为空");
        }
        long chartId = Long.parseLong(message);
        Chart chart = chartService.getById(chartId);
        if (chart == null) {
            // 如果图表为空，拒绝消息并抛出业务异常
            channel.basicNack(deliveryTag, false, false);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图表为空");
        }

        Chart updateChart = new Chart();
        updateChart.setId(chart.getId());
        updateChart.setStatus("running");
        boolean b = chartService.updateById(updateChart);
        if (!b) {
            // 如果更新图表执行中状态失败，拒绝消息并处理图表更新错误
            channel.basicNack(deliveryTag, false, false);
            handleChartUpdateError(chart.getId(), "更新图表执行中状态失败");
            return;
        }

        String result = aiManager.doChat(CommonConstant.BI_MODEL_ID, buildUserInput(chart));
        String[] splits = result.split("【【【【【");
        if (splits.length < 3) {
            channel.basicNack(deliveryTag, false, false);
            handleChartUpdateError(chart.getId(), "AI 生成错误");
            return;
        }
        String genChart = splits[1].trim();
        String genResult = splits[2].trim();
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chart.getId());
        updateChartResult.setGenChart(genChart);
        updateChartResult.setGenResult(genResult);
//        todo 建议定义状态为枚举值
        updateChartResult.setStatus("succeed");
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            // 如果更新图表成功状态失败，拒绝消息并处理图表更新错误
            channel.basicNack(deliveryTag, false, false);
            handleChartUpdateError(chart.getId(), "更新图表成功状态失败");
        }
        // 消息确认，向RabbitMQ发送信号，表明其已经成功处理了对应deliveryTag的消息，可以安全地将其从队列中移除。
        channel.basicAck(deliveryTag, false);
    }

    /**
     * 构建用户输入
     *
     * @param chart 图表对象
     * @return 用户输入字符串
     */
    private String buildUserInput(Chart chart) {
        // 获取图表的目标、类型和数据
        String goal = chart.getGoal();
        String chartType = chart.getChartType();
        String csvData = chart.getChartData();

        // 构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        // 拼接分析目标
        String userGoal = goal;
        if (StringUtils.isNotBlank(chartType)) {
            userGoal += "，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        userInput.append(csvData).append("\n");
        // 将StringBuilder转换为String并返回
        return userInput.toString();
    }

  

    /**
     * 处理智能分析接口的常见异常
     * @param chartId
     * @param execMessage
     */
    public void handleChartUpdateError(long chartId , String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus("failed");
        updateChartResult.setExecMessage(execMessage);
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }

}
