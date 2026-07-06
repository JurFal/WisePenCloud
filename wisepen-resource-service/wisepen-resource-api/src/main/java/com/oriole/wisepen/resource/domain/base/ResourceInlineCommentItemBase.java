package com.oriole.wisepen.resource.domain.base;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceInlineCommentItemBase {
    private String itemId;
    private String authorId;
    private String content;

    @Builder.Default
    private List<String> imageUrls = new ArrayList<>();

    @Builder.Default
    private List<String> mentionUserIds = new ArrayList<>();

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** null 表示未软删除 */
    private LocalDateTime deletedAt;

    public String getContent() {
        return this.deletedAt != null ? null : content;
    }

    public List<String> getImageUrls() {
        return this.deletedAt != null ? null : imageUrls;
    }
}