package com.oriole.wisepen.file.storage.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oriole.wisepen.file.storage.service.IStorageService;
import io.github.springwolf.core.asyncapi.annotations.AsyncListener;
import io.github.springwolf.core.asyncapi.annotations.AsyncMessage;
import io.github.springwolf.core.asyncapi.annotations.AsyncOperation;
import io.github.springwolf.plugins.kafka.asyncapi.annotations.KafkaAsyncOperationBinding;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.oriole.wisepen.common.core.util.LogIdUtils.summarizeIds;
import static com.oriole.wisepen.file.storage.api.constant.MqTopicConstants.TOPIC_FILE_DELETE;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileDeleteConsumer {

    private final IStorageService storageService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC_FILE_DELETE, groupId = "wisepen-storage-file-delete-group")
    @AsyncListener(operation = @AsyncOperation(
            channelName = TOPIC_FILE_DELETE,
            description = "消费对象删除请求，批量删除对象存储中的文件。",
            payloadType = String.class,
            message = @AsyncMessage(name = "FileDeleteObjectKeys", title = "待删除对象 Key 列表")
    ))
    @KafkaAsyncOperationBinding(groupId = "wisepen-storage-file-delete-group")
    public void onObjectDeleted(String payload) throws Exception {
        // 可能从非Java微服务订阅，使用objectMapper显式转换
        List<String> objectKeys = objectMapper.readValue(payload, new TypeReference<List<String>>() {});
        log.info("file delete event received. topic={} count={} objectKeys={}",
                TOPIC_FILE_DELETE, objectKeys.size(), summarizeIds(objectKeys));
        try {
            storageService.deleteFiles(objectKeys);
            log.debug("file delete event consumed. topic={} count={} objectKeys={}",
                    TOPIC_FILE_DELETE, objectKeys.size(), summarizeIds(objectKeys));
        } catch (Exception e) {
            log.error("file delete event consumption failed. topic={} count={} objectKeys={}",
                    TOPIC_FILE_DELETE, objectKeys.size(), summarizeIds(objectKeys), e);
            throw e;
        }
    }
}
