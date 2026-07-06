package com.oriole.wisepen.resource.domain.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InlineCommentItemDeleteRequest {
    @NotBlank
    private String resourceId;

    @NotBlank
    private String inlineCommentId;

    @NotBlank
    private String itemId;
}
