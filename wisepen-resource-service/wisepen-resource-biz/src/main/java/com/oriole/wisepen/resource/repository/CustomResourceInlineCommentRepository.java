package com.oriole.wisepen.resource.repository;

import com.oriole.wisepen.resource.domain.base.ResourceInlineCommentItemBase;
import com.oriole.wisepen.resource.domain.entity.ResourceInlineCommentEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class CustomResourceInlineCommentRepository {

    private final MongoTemplate mongoTemplate;

    public CustomResourceInlineCommentRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<ResourceInlineCommentEntity> listInlineComments(String resourceId,
                                                                Integer contentVersion,
                                                                Boolean resolved) {
        Criteria criteria = Criteria.where("resourceId").is(resourceId)
                .and("deletedAt").is(null);
        if (contentVersion != null) {
            criteria.andOperator(
                    new Criteria().orOperator(
                            Criteria.where("applicableFromVersion").is(null),
                            Criteria.where("applicableFromVersion").lte(contentVersion)
                    ),
                    new Criteria().orOperator(
                            Criteria.where("applicableToVersion").is(null),
                            Criteria.where("applicableToVersion").gte(contentVersion)
                    )
            );
        }
        if (resolved != null) {
            criteria.and("resolved").is(resolved);
        }

        Query query = Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "updateTime"));
        return mongoTemplate.find(query, ResourceInlineCommentEntity.class);
    }

    public void appendItem(String resourceId, String inlineCommentId, ResourceInlineCommentItemBase item) {
        Query query = Query.query(Criteria.where("_id").is(inlineCommentId)
                .and("resourceId").is(resourceId)
                .and("deletedAt").is(null));
        Update update = new Update()
                .push("items", item)
                .set("updateTime", LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, ResourceInlineCommentEntity.class);
    }

    public void softDeleteItem(String resourceId, String inlineCommentId, String itemId) {
        Criteria itemCriteria = Criteria.where("itemId").is(itemId).and("deletedAt").is(null);
        Query query = Query.query(Criteria.where("_id").is(inlineCommentId)
                .and("resourceId").is(resourceId)
                .and("deletedAt").is(null)
                .and("items").elemMatch(itemCriteria));
        Update update = new Update()
                .set("items.$.deletedAt", LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, ResourceInlineCommentEntity.class);
    }

    public void resolveInlineComment(String resourceId, String inlineCommentId, String resolvedBy) {
        Query query = Query.query(Criteria.where("_id").is(inlineCommentId)
                .and("resourceId").is(resourceId)
                .and("deletedAt").is(null));
        Update update = new Update()
                .set("resolved", true)
                .set("resolvedBy", resolvedBy)
                .set("resolvedAt", LocalDateTime.now())
                .set("updateTime", LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, ResourceInlineCommentEntity.class);
    }

    public void unresolveInlineComment(String resourceId, String inlineCommentId) {
        Query query = Query.query(Criteria.where("_id").is(inlineCommentId)
                .and("resourceId").is(resourceId)
                .and("deletedAt").is(null));
        Update update = new Update()
                .set("resolved", false)
                .unset("resolvedBy")
                .unset("resolvedAt")
                .set("updateTime", LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, ResourceInlineCommentEntity.class);
    }
}
