package com.gentlewind.project.manager;

import com.gentlewind.project.common.ErrorCode;
import com.gentlewind.project.exception.BusinessException;
import com.yupi.yucongming.dev.client.YuCongMingClient;
import com.yupi.yucongming.dev.common.BaseResponse;
import com.yupi.yucongming.dev.model.DevChatRequest;
import com.yupi.yucongming.dev.model.DevChatResponse;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 *  用于对接AI平台
 */
@Service
public class AiManager {

    @Resource
    private YuCongMingClient yuCongMingClient;

    public String doChat(long modelId , String message){
        // 构造请求参数
        DevChatRequest devChatRequest =  new DevChatRequest();
        devChatRequest.setModelId(modelId);
        devChatRequest.setMessage(message);

        // 获取响应结果
        BaseResponse<DevChatResponse> response = yuCongMingClient.doChat(devChatRequest);

        // 如果响应为null，返回异常
        if(response == null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        // BaseResponse 是一个通用的响应容器类，通常包含对 API 调用结果的基本信息（如状态码、消息等）以及具体的业务数据。
        // 这里使用泛型 <DevChatResponse> 指定了 BaseResponse 包含的具体业务数据类型为 DevChatResponse，
        // 意味着 response.getData() 将返回一个 DevChatResponse 对象。
        return response.getData().getContent();

    }

}
