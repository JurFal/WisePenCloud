package com.oriole.wisepen.resource.domain.dto.res;

import com.oriole.wisepen.resource.domain.base.ResourceInlineCommentItemBase;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import lombok.*;
import lombok.experimental.SuperBuilder;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class InlineCommentItemResponse extends ResourceInlineCommentItemBase {
    private UserDisplayBase authorInfo;
}
