package com.oriole.wisepen.resource.service;

import com.oriole.wisepen.resource.domain.entity.ResourceItemEntity;
import com.oriole.wisepen.resource.enums.UpsertField;

import java.util.EnumSet;

public interface ISearchSyncService {

    void syncResourceMetadata(ResourceItemEntity entity, EnumSet<UpsertField> fields);

    void syncResourceContent(String resourceId, String content);

    void deleteResourceIndex(String resourceId);

    void syncMarketResourceIndex(ResourceItemEntity entity, String marketGroupId);

    void deleteMarketResourceIndex(String resourceId, String marketGroupId, Integer offerVersion);

    void deleteMarketResourceIndexesByResourceIdAndMarketGroupId(String resourceId, String marketGroupId);

    void deleteMarketResourceIndexesByResourceId(String resourceId);
}
