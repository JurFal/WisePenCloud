package com.oriole.wisepen.note.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.note.api.domain.mq.NoteOperationLogMessage;
import com.oriole.wisepen.note.service.INoteOperationLogService;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.plugins.kafka.asyncapi.annotations.KafkaAsyncOperationBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static com.oriole.wisepen.note.api.constant.MqTopicConstants.TOPIC_NOTE_OPLOG;

@Slf4j
@Component
@RequiredArgsConstructor
public class NoteOperationLogConsumer {

    private final INoteOperationLogService noteOperationLogService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = TOPIC_NOTE_OPLOG,
            groupId = "wisepen-note-oplog-group",
            properties = {
                    "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"
            }
    )
    @AsyncListener(operation = @AsyncOperation(
            channelName = TOPIC_NOTE_OPLOG,
            description = "消费协同编辑操作日志事件，批量落库保存笔记操作历史。",
            payloadType = NoteOperationLogMessage.class,
            message = @AsyncMessage(name = "NoteOperationLogMessage", title = "笔记操作日志事件")
    ))
    @KafkaAsyncOperationBinding(groupId = "wisepen-note-oplog-group")
    public void onOperationLog(String payload) throws Exception {
        // 从非Java微服务（NodeJS）的发布者订阅，使用objectMapper显式转换
        NoteOperationLogMessage msg = objectMapper.readValue(payload, NoteOperationLogMessage.class);
        log.info("note operation log event received. topic={} resourceId={} count={}",
                TOPIC_NOTE_OPLOG, msg.getResourceId(), msg.getEntries().size());
        try {
            noteOperationLogService.batchSave(msg);
            log.debug("note operation log event consumed. topic={} resourceId={} count={}",
                    TOPIC_NOTE_OPLOG, msg.getResourceId(), msg.getEntries().size());
        } catch (Exception e) {
            log.error("note operation log event consumption failed. topic={} resourceId={} count={}",
                    TOPIC_NOTE_OPLOG, msg.getResourceId(), msg.getEntries().size(), e);
            throw e;
        }
    }
}
