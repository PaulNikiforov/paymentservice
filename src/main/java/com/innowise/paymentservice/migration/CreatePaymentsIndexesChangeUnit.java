package com.innowise.paymentservice.migration;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.api.annotations.Execution;
import io.mongock.api.annotations.RollbackExecution;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

@ChangeUnit(id = "create-payments-indexes", order = "001", author = "paymentservice")
public class CreatePaymentsIndexesChangeUnit {

    private static final String COLLECTION = "payments";

    private static final String IDX_USER_ID = "idx_userId";
    private static final String IDX_ORDER_ID = "idx_orderId";
    private static final String IDX_STATUS = "idx_status";
    private static final String IDX_STATUS_EVENT_PUBLISHED = "idx_status_eventPublished";

    private final MongoTemplate mongoTemplate;

    public CreatePaymentsIndexesChangeUnit(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Execution
    public void createPaymentIndexes() {
        IndexOperations indexOps = mongoTemplate.indexOps(COLLECTION);
        indexOps.createIndex(new Index().on("userId", Direction.ASC).named(IDX_USER_ID));
        indexOps.createIndex(new Index().on("orderId", Direction.ASC).named(IDX_ORDER_ID));
        indexOps.createIndex(new Index().on("status", Direction.ASC).named(IDX_STATUS));
        indexOps.createIndex(new Index().on("status", Direction.ASC).on("eventPublished", Direction.ASC)
                .named(IDX_STATUS_EVENT_PUBLISHED));
    }

    @RollbackExecution
    public void dropPaymentIndexes() {
        IndexOperations indexOps = mongoTemplate.indexOps(COLLECTION);
        indexOps.dropIndex(IDX_USER_ID);
        indexOps.dropIndex(IDX_ORDER_ID);
        indexOps.dropIndex(IDX_STATUS);
        indexOps.dropIndex(IDX_STATUS_EVENT_PUBLISHED);
    }
}
