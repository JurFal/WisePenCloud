package com.oriole.wisepen.resource.domain.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class InlineCommentCreateRequest {
    @NotBlank
    private String resourceId;

    // 行内评论锚点
    @NotBlank
    private String externalAnchorId;

    private String quoteText;

    private Map<String, Object> anchorPayload = new HashMap<>();

    // 当前版本与可用范围
    private Integer contentVersion;

    private Integer applicableFromVersion;

    private Integer applicableToVersion;

    // 首条评论内容
    @NotBlank
    private String content;

    private List<String> imageUrls = new ArrayList<>();

    private List<String> mentionUserIds = new ArrayList<>();
}
