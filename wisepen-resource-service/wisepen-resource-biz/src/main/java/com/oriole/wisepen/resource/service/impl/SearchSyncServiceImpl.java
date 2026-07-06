package com.oriole.wisepen.resource.service.impl;

import com.oriole.wisepen.common.core.exception.ServiceException;
import com.oriole.wisepen.document.api.domain.dto.res.DocumentSearchTextResponse;
import com.oriole.wisepen.document.api.feign.RemoteDocumentService;
import com.oriole.wisepen.note.api.domain.dto.res.NoteSearchTextResponse;
import com.oriole.wisepen.note.api.feign.RemoteNoteService;
import com.oriole.wisepen.resource.constant.SearchConstants;
import com.oriole.wisepen.resource.domain.GroupTagBind;
import com.oriole.wisepen.resource.domain.MarketSaleInfo;
import com.oriole.wisepen.resource.domain.entity.ESIndexEntity;
import com.oriole.wisepen.resource.domain.entity.MarketESIndexEntity;
import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.enums.ResourceType;
import com.oriole.wisepen.resource.enums.UpsertField;
import com.oriole.wisepen.resource.exception.ResourceError;
import com.oriole.wisepen.resource.repository.ESIndexRepository;
import com.oriole.wisepen.resource.repository.MarketESIndexRepository;
import com.oriole.wisepen.resource.service.ISearchSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchSyncServiceImpl implements ISearchSyncService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ESIndexRepository esIndexRepository;
    private final MarketESIndexRepository marketESIndexRepository;
    private final RemoteDocumentService remoteDocumentService;
    private final RemoteNoteService remoteNoteService;

    private static final IndexCoordinates INDEX = IndexCoordinates.of(SearchConstants.RESOURCE_INDEX_NAME);
    private static final IndexCoordinates MARKET_INDEX = IndexCoordinates.of(SearchConstants.MARKET_RESOURCE_INDEX_NAME);

    @Override
    public void syncResourceMetadata(ResourceItemEntity entity, EnumSet<UpsertField> fields) {
        executeESUpsert(new ESIndexEntity(entity), fields);
    }

    @Override
    public void syncResourceContent(String resourceId, String content) {
        executeESUpsert(new ESIndexEntity(resourceId, content), EnumSet.of(UpsertField.CONTENT));
    }

    @Override
    public void deleteResourceIndex(String resourceId) {
        try {
            esIndexRepository.deleteById(resourceId);
            log.info("search document deleted. resourceId={}", resourceId);
        } catch (Exception e) {
            log.warn("search document delete failed. resourceId={}", resourceId, e);
        }
    }

    private void executeESUpsert(ESIndexEntity entity, EnumSet<UpsertField> fields) {
        // 确保索引库存在
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(ESIndexEntity.class);
            if (!indexOps.exists()) {
                indexOps.create();
                indexOps.putMapping();
                log.info("search index created. indexName={}", SearchConstants.RESOURCE_INDEX_NAME);
            }
        } catch (Exception e) {
            log.warn("search index ensure failed. indexName={}", SearchConstants.RESOURCE_INDEX_NAME, e);
        }

        try {
            Map<String, Object> doc = new HashMap<>();
            doc.put("resourceId", entity.getResourceId());
            doc.put("updateTime", entity.getUpdateTime().truncatedTo(ChronoUnit.MILLIS)
                    .format(DateTimeFormatter.ofPattern(SearchConstants.ES_DATE_FORMAT_PATTERN)));

            if (fields.contains(UpsertField.RESOURCE_TYPE)) {
                doc.put("resourceType", entity.getResourceType());
            }

            if (fields.contains(UpsertField.RESOURCE_NAME) && entity.getResourceName() != null) {
                doc.put("resourceName", entity.getResourceName());
            }
            if (fields.contains(UpsertField.CONTENT) && entity.getContent() != null) {
                doc.put("content", entity.getContent());
            }
            if (fields.contains(UpsertField.ACL)) {
                doc.put("ownerId", entity.getOwnerId());
                doc.put("specifiedDiscoverUsers", entity.getSpecifiedDiscoverUsers());
                doc.put("computedGroupAcls", entity.getComputedGroupAcls());
            }

            UpdateQuery updateQuery = UpdateQuery.builder(entity.getResourceId())
                    .withDocument(Document.from(doc))
                    .withDocAsUpsert(true)
                    .build();

            elasticsearchOperations.update(updateQuery, INDEX);
            log.debug("search document upsert succeeded. resourceId={} fields={}", entity.getResourceId(), fields);
        } catch (Exception e) {
            log.error("search document upsert failed. resourceId={} fields={}", entity.getResourceId(), fields, e);
            throw new ServiceException(ResourceError.RESOURCE_SEARCH_FAILED);
        }
    }

    @Override
    public void syncMarketResourceIndex(ResourceItemEntity entity, String marketGroupId) {
        // 找到当前 marketGroupId 对应的 MarketSaleInfo
        GroupTagBind marketGroupBind = entity.getGroupBinds().stream()
                .filter(bind -> marketGroupId.equals(bind.getGroupId()))
                .findFirst()
                .orElse(null);
        MarketSaleInfo marketSaleInfo = marketGroupBind == null ? null : marketGroupBind.getMarketSaleInfo();
        if (marketSaleInfo == null) return;
        if (marketSaleInfo.getOfferVersion() == null) return;

        String fullText = getVersionSearchText(entity.getResourceId(), entity.getResourceType(), marketSaleInfo.getOfferVersion());
        String previewContent = truncatePreviewContent(fullText, marketSaleInfo.getReviewContentPercentage());
        executeMarketESUpsert(new MarketESIndexEntity(entity, marketGroupId, previewContent, fullText));
    }

    // 获取对应版本的搜索文本
    private String getVersionSearchText(String resourceId, ResourceType resourceType, Integer offerVersion) {
        if (resourceType == ResourceType.NOTE || resourceType == ResourceType.DRAWIO) {
            NoteSearchTextResponse response = remoteNoteService.getNoteSearchText(resourceId, offerVersion).getData();
            return response == null ? null : response.getSearchText();
        }
        if (resourceType == ResourceType.PDF || resourceType == ResourceType.DOC || resourceType == ResourceType.DOCX
                || resourceType == ResourceType.PPT || resourceType == ResourceType.PPTX
                || resourceType == ResourceType.XLS || resourceType == ResourceType.XLSX) {
            DocumentSearchTextResponse response = remoteDocumentService.getDocumentSearchText(resourceId, offerVersion).getData();
            return response == null ? null : response.getSearchText();
        }
        return null;
    }

    // 裁切预览文本
    private String truncatePreviewContent(String fullText, int reviewContentPercentage) {
        if (fullText == null || reviewContentPercentage <= 0) {
            return null;
        }
        if (reviewContentPercentage >= 100) {
            return fullText;
        }
        int previewLength = Math.max(0, fullText.length() * reviewContentPercentage / 100);
        return previewLength == 0 ? null : fullText.substring(0, previewLength);
    }

    private void executeMarketESUpsert(MarketESIndexEntity entity) {
        // 确保索引库存在
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(MarketESIndexEntity.class);
            if (!indexOps.exists()) {
                indexOps.create();
                indexOps.putMapping();
                log.info("market search index created. indexName={}", SearchConstants.MARKET_RESOURCE_INDEX_NAME);
            }
        } catch (Exception e) {
            log.warn("market search index ensure failed. indexName={}", SearchConstants.MARKET_RESOURCE_INDEX_NAME, e);
        }

        try {
            marketESIndexRepository.save(entity);
            log.debug("market search document upsert succeeded. resourceId={} marketGroupId={} offerVersion={}",
                    entity.getResourceId(), entity.getMarketGroupId(), entity.getMarketSaleInfo().getOfferVersion());
        } catch (Exception e) {
            log.error("market search document upsert failed. resourceId={} marketGroupId={} offerVersion={}",
                    entity.getResourceId(), entity.getMarketGroupId(), entity.getMarketSaleInfo().getOfferVersion(), e);
            throw new ServiceException(ResourceError.RESOURCE_SEARCH_FAILED);
        }
    }

    @Override
    public void deleteMarketResourceIndex(String resourceId, String marketGroupId, Integer offerVersion) {
        try {
            String marketIndexId = resourceId + ":" + marketGroupId + ":" + offerVersion;
            marketESIndexRepository.deleteById(marketIndexId);
            log.info("market search document deleted. resourceId={} marketGroupId={} offerVersion={}",
                    resourceId, marketGroupId, offerVersion);
        } catch (Exception e) {
            log.warn("market search document delete failed. resourceId={} marketGroupId={} offerVersion={}",
                    resourceId, marketGroupId, offerVersion, e);
        }
    }

    @Override
    public void deleteMarketResourceIndexesByResourceIdAndMarketGroupId(String resourceId, String marketGroupId) {
        try {
            CriteriaQuery query = new CriteriaQuery(new Criteria("resourceId").is(resourceId).and("marketGroupId").is(marketGroupId));
            elasticsearchOperations.delete(query, MarketESIndexEntity.class, MARKET_INDEX);
            log.info("market search documents deleted. resourceId={} marketGroupId={}", resourceId, marketGroupId);
        } catch (Exception e) {
            log.warn("market search documents delete failed. resourceId={} marketGroupId={}",
                    resourceId, marketGroupId, e);
        }
    }

    @Override
    public void deleteMarketResourceIndexesByResourceId(String resourceId) {
        try {
            CriteriaQuery query = new CriteriaQuery(new Criteria("resourceId").is(resourceId));
            elasticsearchOperations.delete(query, MarketESIndexEntity.class, MARKET_INDEX);
            log.info("market search documents deleted. resourceId={}", resourceId);
        } catch (Exception e) {
            log.warn("market search documents delete failed. resourceId={}", resourceId, e);
        }
    }
}
