package com.oriole.wisepen.resource.domain.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceInlineCommentBase {
    private String resourceId;

    private Integer applicableFromVersion; // 适用的起始版本
    private Integer applicableToVersion; // 适用的终结版本

    private String creatorId;

    private AnchorRef anchorRef; // 锚点

    @Builder.Default
    private Boolean resolved = false;

    private String resolvedBy;
    private LocalDateTime resolvedAt;

    /** null 表示未软删除 */
    private LocalDateTime deletedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnchorRef {
        private String externalAnchorId;
        private String quoteText;

        @Builder.Default
        private Map<String, Object> anchorPayload = new HashMap<>();
    }
}
