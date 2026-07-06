package com.oriole.wisepen.resource.repository;

import com.oriole.wisepen.resource.domain.entity.MarketESIndexEntity;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface MarketESIndexRepository extends ElasticsearchRepository<MarketESIndexEntity, String> {
}
