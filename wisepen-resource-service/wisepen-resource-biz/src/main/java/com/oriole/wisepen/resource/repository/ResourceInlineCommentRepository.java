package com.oriole.wisepen.resource.repository;

import com.oriole.wisepen.resource.domain.entity.ResourceInlineCommentEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Optional;

public interface ResourceInlineCommentRepository extends MongoRepository<ResourceInlineCommentEntity, String> {

    @Query("{ '_id': ?0, 'resourceId': ?1, 'deletedAt': null }")
    Optional<ResourceInlineCommentEntity> findByIdAndResourceIdAndDeletedAtIsNull(String inlineCommentId, String resourceId);
}
