package com.oriole.wisepen.document.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.common.mq.ReliablePublisher;
import com.oriole.wisepen.document.api.domain.mq.DocumentParseTaskMessage;
import com.oriole.wisepen.document.api.domain.mq.DocumentReadyMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.core.asyncapi.annotations.AsyncPublisher;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.oriole.wisepen.document.api.constant.MqTopicConstants.TOPIC_DOCUMENT_PARSE;
import static com.oriole.wisepen.document.api.constant.MqTopicConstants.TOPIC_DOCUMENT_READY;
import static com.oriole.wisepen.common.core.util.LogIdUtils.summarizeIds;
import static com.oriole.wisepen.file.storage.api.constant.MqTopicConstants.TOPIC_FILE_DELETE;

/**
 * 文档服务 Kafka 事件发布器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaDocumentEventPublisher {

    @Resource
    ReliablePublisher reliablePublisher;
    private final ObjectMapper objectMapper;

    // 发布文档解析任务（内部削峰）
    @AsyncPublisher(operation = @AsyncOperation(
            channelName = TOPIC_DOCUMENT_PARSE,
            description = "文档上传完成后发布解析任务，由文档服务异步转换、解析并生成预览。",
            payloadType = DocumentParseTaskMessage.class,
            message = @AsyncMessage(name = "DocumentParseTaskMessage", title = "文档解析任务")
    ))
    public void publishParseTask(DocumentParseTaskMessage msg) {
        try {
            String documentId = msg.getDocumentId();
            reliablePublisher.publish(TOPIC_DOCUMENT_PARSE, documentId, msg, documentId);
            log.debug("document parse event publish requested. topic={} documentId={}",
                    TOPIC_DOCUMENT_PARSE, documentId);
        } catch (Exception e) {
            log.error("document parse event publish request failed. topic={} documentId={}",
                    TOPIC_DOCUMENT_PARSE, msg.getDocumentId(), e);
        }
    }

    // 发布文档处理就绪事件
    @AsyncPublisher(operation = @AsyncOperation(
            channelName = TOPIC_DOCUMENT_READY,
            description = "文档转换解析完成后发布就绪事件，供资源服务同步可检索内容。",
            payloadType = DocumentReadyMessage.class,
            message = @AsyncMessage(name = "DocumentReadyMessage", title = "文档就绪事件")
    ))
    public void publishReadyEvent(DocumentReadyMessage msg) {
        try {
            String resourceId = msg.getResourceId();
            reliablePublisher.publish(TOPIC_DOCUMENT_READY, resourceId, msg, resourceId);
            log.debug("document ready event publish requested. topic={} resourceId={} version={}",
                    TOPIC_DOCUMENT_READY, resourceId, msg.getVersion());
        } catch (Exception e) {
            log.error("document ready event publish request failed. topic={} resourceId={}",
                    TOPIC_DOCUMENT_READY, msg.getResourceId(), e);
        }
    }

    // 发布文件删除事件
    @AsyncPublisher(operation = @AsyncOperation(
            channelName = TOPIC_FILE_DELETE,
            description = "文档版本取消、删除或补偿时发布对象存储清理请求。",
            payloadType = String.class,
            message = @AsyncMessage(name = "FileDeleteObjectKeys", title = "待删除对象 Key 列表")
    ))
    public void publishFileDeleteEvent(List<String> allObjectKeys) {
        try {
            // 发布至兼容非Java微服务的订阅者，统一使用 Jackson 序列化
            String jsonPayload = objectMapper.writeValueAsString(allObjectKeys);
            int count = allObjectKeys.size();
            reliablePublisher.publish(TOPIC_FILE_DELETE, null, jsonPayload, null);
            log.debug("file delete event publish requested. topic={} count={} objectKeys={}",
                    TOPIC_FILE_DELETE, count, summarizeIds(allObjectKeys));
        } catch (Exception e) {
            log.error("file delete event publish request failed. topic={} count={} objectKeys={}",
                    TOPIC_FILE_DELETE, allObjectKeys.size(), summarizeIds(allObjectKeys), e);
        }
    }
}
