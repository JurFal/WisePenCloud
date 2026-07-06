package com.oriole.wisepen.ai.asset.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.ai.asset.service.impl.AgentVersionServiceImpl;
import com.oriole.wisepen.ai.asset.service.impl.SkillVersionServiceImpl;
import com.oriole.wisepen.file.storage.api.domain.mq.FileUploadedMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.plugins.kafka.asyncapi.annotations.KafkaAsyncOperationBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.oriole.wisepen.file.storage.api.constant.MqTopicConstants.TOPIC_FILE_UPLOADED;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileUploadedConsumer {

    private final SkillVersionServiceImpl skillVersionService;
    private final AgentVersionServiceImpl agentVersionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC_FILE_UPLOADED, groupId = "wisepen-ai-resource-upload-callback-group")
    @AsyncListener(operation = @AsyncOperation(
            channelName = TOPIC_FILE_UPLOADED,
            description = "消费文件上传完成事件，按上传场景推进技能或智能体资产版本处理。",
            payloadType = FileUploadedMessage.class,
            message = @AsyncMessage(name = "FileUploadedMessage", title = "文件上传完成事件")
    ))
    @KafkaAsyncOperationBinding(groupId = "wisepen-ai-resource-upload-callback-group")
    public void onFileUploaded(String payload) throws Exception {
        // 从兼容非Java微服务的发布者订阅，使用objectMapper显式转换
        FileUploadedMessage message = objectMapper.readValue(payload, FileUploadedMessage.class);
        log.info("skill asset file upload event received. topic={} objectKey={} scene={}",
                TOPIC_FILE_UPLOADED, message.getObjectKey(), message.getScene());
        try {
            switch (message.getScene()) {
                case PRIVATE_SKILL_ASSET -> skillVersionService.handleFileUploaded(message);
                case PRIVATE_AGENT_ASSET -> agentVersionService.handleFileUploaded(message);
                default -> {
                    log.debug("ai asset file upload event skipped. objectKey={} scene={} reason=\"scene mismatch\"",
                            message.getObjectKey(), message.getScene());
                    return;
                }
            }

            log.debug("skill asset file upload event consumed. topic={} objectKey={}",
                    TOPIC_FILE_UPLOADED, message.getObjectKey());
        } catch (Exception e) {
            log.error("skill asset file upload event consumption failed. topic={} objectKey={}",
                    TOPIC_FILE_UPLOADED, message.getObjectKey(), e);
            throw e;
        }
    }
}
