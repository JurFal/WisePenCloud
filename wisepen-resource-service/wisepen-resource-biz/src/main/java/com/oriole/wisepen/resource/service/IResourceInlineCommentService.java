package com.oriole.wisepen.resource.service;

import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentCreateRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentItemCreateRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentItemDeleteRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentResolveRequest;
import com.oriole.wisepen.resource.domain.dto.res.ResourceInlineCommentResponse;

import java.util.List;

public interface IResourceInlineCommentService {

    String createInlineComment(InlineCommentCreateRequest request,
                               String operatorUserId);

    String addInlineCommentItem(InlineCommentItemCreateRequest request,
                                String operatorUserId);

    void deleteInlineCommentItem(InlineCommentItemDeleteRequest request,
                                 String operatorUserId,
                                 IdentityType operatorIdentityType);

    void changeInlineCommentResolveStatus(InlineCommentResolveRequest request,
                                     String operatorUserId,
                                     IdentityType operatorIdentityType,
                                     boolean operatorHasEditAction);

    List<ResourceInlineCommentResponse> listInlineComments(String resourceId,
                                                           Integer contentVersion,
                                                           Boolean resolved);
}
