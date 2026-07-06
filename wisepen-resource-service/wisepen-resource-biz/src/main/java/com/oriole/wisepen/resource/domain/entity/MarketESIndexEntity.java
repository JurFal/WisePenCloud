package com.oriole.wisepen.resource.domain.entity;

import cn.hutool.core.bean.BeanUtil;
import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.resource.constant.SearchConstants;
import com.oriole.wisepen.resource.domain.GroupTagBind;
import com.oriole.wisepen.resource.domain.MarketSaleInfo;
import com.oriole.wisepen.resource.domain.base.MarketSaleInfoBase;
import com.oriole.wisepen.resource.exception.ResourceError;
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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = SearchConstants.MARKET_RESOURCE_INDEX_NAME, createIndex = true)
public class MarketESIndexEntity {

    @Id
    @Field(type = FieldType.Keyword)
    private String id;

    @Field(type = FieldType.Keyword)
    private String resourceId;

    @Field(type = FieldType.Keyword)
    private String marketGroupId;

    @Field(type = FieldType.Keyword)
    private String resourceType;

    @Field(type = FieldType.Text,
            analyzer = SearchConstants.ANALYZER_IK_MAX_WORD,
            searchAnalyzer = SearchConstants.ANALYZER_IK_SMART)
    private String resourceName;

    @Field(type = FieldType.Keyword)
    private String ownerId;

    @Field(type = FieldType.Object)
    private MarketSaleInfoBase marketSaleInfo;

    @Field(type = FieldType.Keyword)
    private String marketSaleStatus;

    @Field(type = FieldType.Text,
            analyzer = SearchConstants.ANALYZER_IK_MAX_WORD,
            searchAnalyzer = SearchConstants.ANALYZER_IK_SMART)
    private String previewContent;

    @Field(type = FieldType.Text,
            analyzer = SearchConstants.ANALYZER_IK_MAX_WORD,
            searchAnalyzer = SearchConstants.ANALYZER_IK_SMART)
    private String searchContent;

    @Field(type = FieldType.Date,
            format = {},
            pattern = SearchConstants.ES_DATE_FORMAT_PATTERN)
    private LocalDateTime updateTime;

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName == null ? null : HtmlUtils.htmlEscape(resourceName);
    }

    public void setPreviewContent(String previewContent) {
        this.previewContent = previewContent == null ? null : HtmlUtils.htmlEscape(previewContent);
    }

    public void setSearchContent(String searchContent) {
        this.searchContent = searchContent == null ? null : HtmlUtils.htmlEscape(searchContent);
    }

    public MarketESIndexEntity(ResourceItemEntity entity, String marketGroupId, String previewContent, String searchContent) {
        GroupTagBind marketGroupBind = entity.getGroupBinds().stream()
                .filter(bind -> marketGroupId.equals(bind.getGroupId()))
                .findFirst()
                .orElse(null);
        MarketSaleInfo marketSaleInfo = marketGroupBind == null ? null : marketGroupBind.getMarketSaleInfo();
        if (marketSaleInfo == null) throw new ServiceException(ResourceError.MARKET_SALE_INFO_NOT_FOUND);

        this.id = entity.getResourceId() + ":" + marketGroupId + ":" + marketSaleInfo.getOfferVersion();
        this.resourceId = entity.getResourceId();
        this.marketGroupId = marketGroupId;
        this.resourceType = entity.getResourceType().getExtension();
        this.ownerId = entity.getOwnerId();
        this.marketSaleInfo = BeanUtil.copyProperties(marketSaleInfo, MarketSaleInfoBase.class);
        this.marketSaleStatus = marketSaleInfo.getStatus().getValue();
        this.updateTime = entity.getUpdateTime() != null ? entity.getUpdateTime() : LocalDateTime.now();

        this.setResourceName(entity.getResourceName());
        this.setPreviewContent(previewContent);
        this.setSearchContent(searchContent);
    }
}
