package com.oriole.wisepen.user.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.common.mq.ReliablePublisher;
import com.oriole.wisepen.extension.fudan.constant.MqTopicConstants;
import com.oriole.wisepen.extension.fudan.domain.mq.FudanUISAuthRequestMessage;
import com.oriole.wisepen.user.exception.UserError;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaUserEventPublisher {

    @Resource
    ReliablePublisher reliablePublisher;

    private final ObjectMapper objectMapper;

    @AsyncPublisher(operation = @AsyncOperation(
            channelName = MqTopicConstants.FUDAN_UIS_AUTH_REQ,
            description = "用户发起复旦 UIS 认证后发布认证请求，由复旦扩展服务异步抓取认证状态。",
            payloadType = FudanUISAuthRequestMessage.class,
            message = @AsyncMessage(name = "FudanUISAuthRequestMessage", title = "复旦 UIS 认证请求")
    ))
    public void publishUisAuthRequest(Long userId, FudanUISAuthRequestMessage message) {
        try {
            reliablePublisher.publish(MqTopicConstants.FUDAN_UIS_AUTH_REQ, null,objectMapper.writeValueAsString(message), null);
            log.debug("fudan uis auth publish requested. topic={} userId={}",
                    MqTopicConstants.FUDAN_UIS_AUTH_REQ, userId);
        } catch (Exception e) {
            log.error("fudan uis auth publish request failed. topic={} userId={}",
                    MqTopicConstants.FUDAN_UIS_AUTH_REQ, userId, e);
            throw new ServiceException(UserError.VERIFICATION_FUDAN_UIS_REQUEST_FAILED);
        }
    }
}
