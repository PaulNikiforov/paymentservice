package com.innowise.paymentservice.repository;

import com.innowise.paymentservice.document.PaymentDocument;
import com.innowise.paymentservice.document.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
public class PaymentRepositoryCustomImpl implements PaymentRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public PaymentDocument findOrCreatePending(String orderId, String userId, BigDecimal amount) {
        Instant now = Instant.now();
        Query query = Query.query(Criteria.where("orderId").is(orderId));
        Update update = new Update()
                .setOnInsert("orderId", orderId)
                .setOnInsert("userId", userId)
                .setOnInsert("status", PaymentStatus.PENDING)
                .setOnInsert("paymentAmount", amount)
                .setOnInsert("eventPublished", false)
                .setOnInsert("timestamp", now);
        FindAndModifyOptions options = FindAndModifyOptions.options().upsert(true).returnNew(true);
        return mongoTemplate.findAndModify(query, update, options, PaymentDocument.class);
    }

    @Override
    public Page<PaymentDocument> findByFilters(String userId, String orderId, PaymentStatus status,
                                                Pageable pageable) {
        Query query = new Query();
        if (userId != null) {
            query.addCriteria(Criteria.where("userId").is(userId));
        }
        if (orderId != null) {
            query.addCriteria(Criteria.where("orderId").is(orderId));
        }
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }

        long total = mongoTemplate.count(query, PaymentDocument.class);
        List<PaymentDocument> content = mongoTemplate.find(query.with(pageable), PaymentDocument.class);

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public BigDecimal sumSuccessfulPaymentsForUser(String userId, Instant from, Instant to) {
        return sumPaymentAmount(Criteria.where("userId").is(userId)
                .and("status").is(PaymentStatus.SUCCESS)
                .and("timestamp").gte(from).lte(to));
    }

    @Override
    public BigDecimal sumSuccessfulPaymentsForAllUsers(Instant from, Instant to) {
        return sumPaymentAmount(Criteria.where("status").is(PaymentStatus.SUCCESS)
                .and("timestamp").gte(from).lte(to));
    }

    private BigDecimal sumPaymentAmount(Criteria criteria) {
        Aggregation aggregation = Aggregation.newAggregation(PaymentDocument.class,
                Aggregation.match(criteria),
                Aggregation.group().sum("paymentAmount").as("total"));

        String collectionName = mongoTemplate.getCollectionName(PaymentDocument.class);
        Document result = mongoTemplate.aggregate(aggregation, collectionName, Document.class).getUniqueMappedResult();
        if (result == null) {
            return BigDecimal.ZERO;
        }
        return result.get("total", Decimal128.class).bigDecimalValue();
    }
}
