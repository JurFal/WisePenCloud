package com.oriole.wisepen.resource.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.oriole.wisepen.common.core.domain.enums.IdentityType;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.resource.domain.base.ResourceInlineCommentItemBase;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentCreateRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentItemCreateRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentItemDeleteRequest;
import com.oriole.wisepen.resource.domain.dto.req.InlineCommentResolveRequest;
import com.oriole.wisepen.resource.domain.dto.res.InlineCommentItemResponse;
import com.oriole.wisepen.resource.domain.dto.res.ResourceInlineCommentResponse;
import com.oriole.wisepen.resource.domain.entity.ResourceInlineCommentEntity;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.exception.ResourceError;
import com.oriole.wisepen.resource.repository.CustomResourceInlineCommentRepository;
import com.oriole.wisepen.resource.repository.ResourceInlineCommentRepository;
import com.oriole.wisepen.resource.service.IResourceInlineCommentService;
import com.oriole.wisepen.resource.service.IResourceService;
import com.oriole.wisepen.user.api.domain.base.UserDisplayBase;
import com.oriole.wisepen.user.api.feign.RemoteUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResourceInlineCommentServiceImpl implements IResourceInlineCommentService {

    private final IResourceService resourceService;
    private final ResourceInlineCommentRepository inlineCommentRepository;
    private final CustomResourceInlineCommentRepository customInlineCommentRepository;
    private final RemoteUserService remoteUserService;

    @Override
    public String createInlineComment(InlineCommentCreateRequest request,
                                      String operatorUserId) {
        String resourceId = request.getResourceId();

        LocalDateTime now = LocalDateTime.now();
        // 构建 InlineCommentItem
        ResourceInlineCommentItemBase commentItem = ResourceInlineCommentItemBase.builder()
                .itemId(IdUtil.fastSimpleUUID()).authorId(operatorUserId)
                .createTime(now).updateTime(now).build();
        BeanUtil.copyProperties(commentItem, request);

        // 构建锚点
        ResourceInlineCommentEntity.AnchorRef anchorRef = BeanUtil.copyProperties(request, ResourceInlineCommentEntity.AnchorRef.class);

        // 构建 InlineComment
        ResourceInlineCommentEntity inlineComment = ResourceInlineCommentEntity.builder()
                .resourceId(resourceId)
                .applicableFromVersion(request.getApplicableFromVersion()).applicableToVersion(request.getApplicableToVersion())
                .creatorId(operatorUserId)
                .anchorRef(anchorRef)
                .items(new ArrayList<>(List.of(commentItem)))
                .resolved(false)
                .createTime(now).updateTime(now).build();
        inlineComment = inlineCommentRepository.save(inlineComment);

        log.info("inline comment created. resourceId={} inlineCommentId={} creatorId={}", resourceId, inlineComment.getInlineCommentId(), operatorUserId);
        return inlineComment.getInlineCommentId();
    }

    @Override
    public String addInlineCommentItem(InlineCommentItemCreateRequest request,
                                       String operatorUserId) {
        String resourceId = request.getResourceId();
        ResourceInlineCommentEntity inlineComment = getInlineComment(request.getInlineCommentId(), resourceId);
        // 检查是否仍然适用当前版本
        if (!isApplicable(inlineComment, request.getContentVersion())) {
            throw new ServiceException(ResourceError.COMMENT_NOT_FOUND);
        }

        // 构建 InlineComment 并直接插入
        LocalDateTime now = LocalDateTime.now();
        ResourceInlineCommentItemBase commentItem = ResourceInlineCommentItemBase.builder()
                .itemId(IdUtil.fastSimpleUUID()).authorId(operatorUserId)
                .createTime(now).updateTime(now).build();
        BeanUtil.copyProperties(commentItem, request);
        customInlineCommentRepository.appendItem(resourceId, request.getInlineCommentId(), commentItem);

        log.info("inline comment item created. resourceId={} inlineCommentId={} itemId={} authorId={}", resourceId, request.getInlineCommentId(), commentItem.getItemId(), operatorUserId);
        return commentItem.getItemId();
    }

    @Override
    public void deleteInlineCommentItem(InlineCommentItemDeleteRequest request, String operatorUserId, IdentityType operatorIdentityType) {
        String resourceId = request.getResourceId();
        ResourceItemEntity resource = resourceService.getResourceEntity(resourceId);
        ResourceInlineCommentEntity inlineComment = getInlineComment(request.getInlineCommentId(), resourceId);

        ResourceInlineCommentItemBase commentItem = inlineComment.getItems().stream()
                .filter(candidate -> Objects.equals(candidate.getItemId(), request.getItemId()))
                .filter(candidate -> candidate.getDeletedAt() == null)
                .findFirst()
                .orElseThrow(() -> new ServiceException(ResourceError.COMMENT_NOT_FOUND));

        // 管理员，资源所有者，评论者本人可以删除评论
        if (!commentItem.getAuthorId().equals(operatorUserId)
                && operatorIdentityType != IdentityType.ADMIN
                && !resource.getOwnerId().equals(operatorUserId)) {
            throw new ServiceException(ResourceError.COMMENT_DELETE_ACCESS_DENIED);
        }

        customInlineCommentRepository.softDeleteItem(resourceId, request.getInlineCommentId(), request.getItemId());
        log.info("inline comment item deleted. resourceId={} inlineCommentId={} itemId={} operatorUserId={}",
                resourceId, request.getInlineCommentId(), request.getItemId(), operatorUserId);
    }

    @Override
    public void changeInlineCommentResolveStatus(InlineCommentResolveRequest request,
                                                 String operatorUserId, IdentityType operatorIdentityType, boolean operatorHasEditAction) {
        String resourceId = request.getResourceId();
        ResourceItemEntity resource = resourceService.getResourceEntity(resourceId);
        ResourceInlineCommentEntity inlineComment = getInlineComment(request.getInlineCommentId(), resourceId);
        if (!isApplicable(inlineComment, request.getContentVersion())) {
            throw new ServiceException(ResourceError.COMMENT_NOT_FOUND);
        }

        // 管理员，资源所有者，评论者创建者（首条评论作者）本人，有编辑权限的用户可以解决评论
        if (!inlineComment.getCreatorId().equals(operatorUserId)
                && operatorIdentityType != IdentityType.ADMIN
                && !resource.getOwnerId().equals(operatorUserId)
                && !operatorHasEditAction) {
            throw new ServiceException(ResourceError.COMMENT_RESOLVE_ACCESS_DENIED);
        }

        if (request.isResolved()) {
            customInlineCommentRepository.resolveInlineComment(resourceId, request.getInlineCommentId(), operatorUserId);
        } else {
            customInlineCommentRepository.unresolveInlineComment(resourceId, request.getInlineCommentId());
        }

        log.info("inline comment resolved. resourceId={} inlineCommentId={} operatorUserId={}",
                resourceId, request.getInlineCommentId(), operatorUserId);
    }

    @Override
    public List<ResourceInlineCommentResponse> listInlineComments(String resourceId,
                                                                  Integer contentVersion,
                                                                  Boolean resolved) {
        resourceService.getResourceEntity(resourceId);

        List<ResourceInlineCommentEntity> inlineComments = customInlineCommentRepository.listInlineComments(
                resourceId, contentVersion, resolved
        );
        // 收集 UserId
        Set<Long> userIds = new LinkedHashSet<>();
        for (ResourceInlineCommentEntity inlineComment : inlineComments) {
            if (StringUtils.hasText(inlineComment.getCreatorId())) userIds.add(Long.valueOf(inlineComment.getCreatorId()));
            if (StringUtils.hasText(inlineComment.getResolvedBy())) userIds.add(Long.valueOf(inlineComment.getResolvedBy()));
            if (inlineComment.getItems() != null) {
                for (ResourceInlineCommentItemBase item : inlineComment.getItems()) {
                    if (StringUtils.hasText(item.getAuthorId())) userIds.add(Long.valueOf(item.getAuthorId()));
                }
            }
        }

        // 远程拉取用户信息
        Map<Long, UserDisplayBase> userMap = Collections.emptyMap();
        if (!userIds.isEmpty()) {
            try {
                Map<Long, UserDisplayBase> fetched = remoteUserService.getUserDisplayInfo(userIds.stream().toList()).getData();
                userMap = fetched == null ? Collections.emptyMap() : fetched;
            } catch (Exception e) {
                log.warn("inline comment user info batch degraded. userCount={}", userIds.size(), e);
            }
        }

        List<ResourceInlineCommentResponse> responses = new ArrayList<>();
        for (ResourceInlineCommentEntity entity : inlineComments) {

            List<InlineCommentItemResponse> commentItemResponses = new ArrayList<>();
            if (entity.getItems() != null) {
                for (ResourceInlineCommentItemBase commentItem : entity.getItems()) {
                    InlineCommentItemResponse commentItemResponse = BeanUtil.copyProperties(commentItem, InlineCommentItemResponse.class);
                    if (commentItemResponse.getDeletedAt() != null) {
                        commentItemResponse.setContent(null);
                        commentItemResponse.setImageUrls(null);
                    }
                    commentItemResponse.setAuthorInfo(userMap.get(Long.valueOf(commentItem.getAuthorId())));
                    commentItemResponses.add(commentItemResponse);
                }
            }
            ResourceInlineCommentResponse commentResponse =  BeanUtil.copyProperties(entity, ResourceInlineCommentResponse.class);
            commentResponse.setCreatorInfo(userMap.get(Long.valueOf(entity.getCreatorId())));
            commentResponse.setItems(commentItemResponses);
            if (StringUtils.hasText(entity.getResolvedBy())) { // 仅处理已被解决的 Comment
                commentResponse.setResolvedByInfo(userMap.get(Long.valueOf(entity.getResolvedBy())));
            }
        }
        return responses;
    }

    private ResourceInlineCommentEntity getInlineComment(String inlineCommentId, String resourceId) {
        return inlineCommentRepository.findByIdAndResourceIdAndDeletedAtIsNull(inlineCommentId, resourceId)
                .orElseThrow(() -> new ServiceException(ResourceError.COMMENT_NOT_FOUND));
    }

    private boolean isApplicable(ResourceInlineCommentEntity inlineComment, Integer contentVersion) {
        if (contentVersion == null) {
            return true;
        }
        Integer from = inlineComment.getApplicableFromVersion();
        Integer to = inlineComment.getApplicableToVersion();
        return (from == null || from <= contentVersion) && (to == null || to >= contentVersion);
    }
}
