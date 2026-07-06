package com.oriole.wisepen.resource.domain.entity;

import com.oriole.wisepen.resource.constant.SearchConstants;
import com.oriole.wisepen.resource.enums.ResourceAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = SearchConstants.RESOURCE_INDEX_NAME, createIndex = true)
public class ESIndexEntity {

    @Id
    @Field(type = FieldType.Keyword)
    private String resourceId;

    @Field(type = FieldType.Keyword)
    private String resourceType;

    @Field(type = FieldType.Text,
            analyzer = SearchConstants.ANALYZER_IK_MAX_WORD,
            searchAnalyzer = SearchConstants.ANALYZER_IK_SMART)
    private String resourceName;

    @Field(type = FieldType.Text,
            analyzer = SearchConstants.ANALYZER_IK_MAX_WORD,
            searchAnalyzer = SearchConstants.ANALYZER_IK_SMART)
    private String content;

    @Field(type = FieldType.Keyword)
    private String ownerId;

    @Field(type = FieldType.Keyword)
    private List<String> specifiedDiscoverUsers;

    @Field(type = FieldType.Nested)
    private List<ComputedGroupAclProjection> computedGroupAcls;

    @Field(type = FieldType.Date,
            format = {},
            pattern = SearchConstants.ES_DATE_FORMAT_PATTERN)
    private LocalDateTime updateTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComputedGroupAclProjection {

        @Field(type = FieldType.Keyword)
        private String groupId;

        @Field(type = FieldType.Boolean)
        private Boolean isDiscover;

        @Field(type = FieldType.Keyword)
        @Builder.Default
        private List<String> specifiedUsers = new ArrayList<>();
    }

    public void setResourceName(String resourceName) {
        this.resourceName = HtmlUtils.htmlEscape(resourceName);
    }

    public void setContent(String content) {
        this.content = HtmlUtils.htmlEscape(content);
    }

    public ESIndexEntity(String resourceId, String content) {
        this.resourceId = resourceId;
        this.content = HtmlUtils.htmlEscape(content);
        this.updateTime = LocalDateTime.now();
    }

    public ESIndexEntity(ResourceItemEntity entity) {
        this.resourceId = entity.getResourceId();
        this.resourceType = entity.getResourceType() != null ? entity.getResourceType().getExtension() : null;
        this.resourceName = HtmlUtils.htmlEscape(entity.getResourceName());
        this.ownerId = entity.getOwnerId();
        this.updateTime = entity.getUpdateTime() != null ? entity.getUpdateTime() : LocalDateTime.now();

        Set<String> marketGroupIds = entity.getGroupBinds() == null ? Collections.emptySet() :
                entity.getGroupBinds().stream()
                        .filter(bind -> bind.getMarketSaleInfo() != null)
                        .map(bind -> bind.getGroupId())
                        .collect(Collectors.toSet());

        // 组 DISCOVER 权限
        this.computedGroupAcls = entity.getComputedGroupAcls() != null ?
                entity.getComputedGroupAcls().entrySet().stream()
                        .filter(entry -> !marketGroupIds.contains(entry.getKey())) // Market组不写入ACL以避免从普通搜索中获取到信息
                        .map(entry -> {
                            boolean isDiscover = ResourceAction.permissionCodeToActions(entry.getValue().getBaseMask()).contains(ResourceAction.DISCOVER);
                            List<String> specifiedUsers = entry.getValue().getUserMasks().entrySet().stream().filter(
                                    userMask -> ResourceAction.hasAction(userMask.getValue(), ResourceAction.DISCOVER) != isDiscover
                            ).map(Map.Entry::getKey).toList();

                            return ComputedGroupAclProjection.builder().groupId(entry.getKey())
                                    .isDiscover(isDiscover).specifiedUsers(specifiedUsers)
                                    .build();
                        }).toList() : null;

        // 资源级指定 DISCOVER 权限
        this.specifiedDiscoverUsers = entity.getSpecifiedUsersGrantedActionsMask() != null ?
                entity.getSpecifiedUsersGrantedActionsMask().entrySet().stream().filter(
                        userMask -> ResourceAction.hasAction(userMask.getValue(), ResourceAction.DISCOVER)
                ).map(Map.Entry::getKey).toList() : null;
    }
}
