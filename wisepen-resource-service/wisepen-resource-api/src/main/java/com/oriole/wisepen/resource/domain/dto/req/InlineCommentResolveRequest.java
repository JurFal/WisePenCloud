package com.oriole.wisepen.resource.domain.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InlineCommentResolveRequest {
    @NotBlank
    private String resourceId;

    @NotBlank
    private String inlineCommentId;

    @NotNull
    private boolean resolved;

    private Integer contentVersion;
}
