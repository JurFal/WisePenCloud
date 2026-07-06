package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.domain.base.ResourceInlineCommentBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceInlineCommentResponse extends ResourceInlineCommentBase {
    private String inlineCommentId;
    private UserDisplayBase creatorInfo;
    private UserDisplayBase resolvedByInfo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Builder.Default
    private List<InlineCommentItemResponse> items = new ArrayList<>();
}
