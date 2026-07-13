package com.innowise.paymentservice.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.innowise.paymentservice.StubExternalPaymentApi;
import com.innowise.paymentservice.StubJwksUri;
import com.innowise.paymentservice.TestcontainersConfiguration;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexField;
import org.springframework.data.mongodb.core.index.IndexInfo;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@StubExternalPaymentApi
@StubJwksUri
class PaymentIndexMigrationTest {

	@Autowired
	private MongoTemplate mongoTemplate;

	@Test
	void paymentsCollectionShouldHaveIndexesOnUserIdOrderIdAndStatus() {
		List<IndexInfo> indexes = mongoTemplate.indexOps("payments").getIndexInfo();

		List<String> indexedFieldNames = indexes.stream()
				.flatMap(indexInfo -> indexInfo.getIndexFields().stream())
				.map(IndexField::getKey)
				.toList();

		assertThat(indexedFieldNames).contains("user_id", "order_id", "status");
	}

	@Test
	void paymentsCollectionShouldHaveCompoundIndexOnStatusAndEventPublished() {
		List<IndexInfo> indexes = mongoTemplate.indexOps("payments").getIndexInfo();

		boolean hasCompoundIndex = indexes.stream()
				.map(indexInfo -> indexInfo.getIndexFields().stream()
						.map(IndexField::getKey)
						.toList())
				.anyMatch(fieldNames -> fieldNames.contains("status") && fieldNames.contains("event_published"));

		assertThat(hasCompoundIndex)
				.as("expected a compound index containing both 'status' and 'event_published' fields")
				.isTrue();
	}

	@Test
	void liquibaseChangeLogShouldRecordTheAppliedMigration() {
		List<Document> changeLogEntries = mongoTemplate.findAll(Document.class, "DATABASECHANGELOG");

		assertThat(changeLogEntries).anySatisfy(entry -> {
			assertThat(entry.getString("id")).isEqualTo("001-create-payments-collection-and-indexes");
			assertThat(entry.getString("execType")).isEqualTo("EXECUTED");
		});
	}

}
