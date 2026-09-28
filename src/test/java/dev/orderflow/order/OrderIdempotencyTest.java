package dev.orderflow.order;

import dev.orderflow.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class OrderIdempotencyTest extends ApiIntegrationTest {

	@Test
	void replayWithSameKeyReturnsSameOrder() throws Exception {
		givenProduct("kb-1", 1_000L, 5);

		var first = givenOrder("key-1", Map.of("kb-1", 2));
		var second = givenOrder("key-1", Map.of("kb-1", 2));

		assertThat(first.status()).isEqualTo(201);
		assertThat(second.status()).isEqualTo(200);
		assertThat(second.body().get("id").asText()).isEqualTo(first.body().get("id").asText());
		// главное: остаток списан один раз
		assertThat(stockOf("kb-1")).isEqualTo(3);
		assertThat(orderCount()).isEqualTo(1);
	}

	@Test
	void sameKeyWithDifferentItemsIsConflict() throws Exception {
		givenProduct("kb-1", 1_000L, 5);
		givenProduct("mouse-1", 2_000L, 5);
		givenOrder("key-2", Map.of("kb-1", 1));

		var result = givenOrder("key-2", Map.of("mouse-1", 1));

		assertThat(result.status()).isEqualTo(409);
		assertThat(result.body().get("code").asText()).isEqualTo("idempotency_key_reused");
		assertThat(stockOf("kb-1")).isEqualTo(4);
		assertThat(stockOf("mouse-1")).isEqualTo(5);
	}

	@Test
	void sameItemsInDifferentOrderAreSameRequest() throws Exception {
		givenProduct("kb-1", 1_000L, 10);
		givenProduct("mouse-1", 2_000L, 10);

		var first = exchange(MockMvcRequestBuilders.post("/api/orders").header("Idempotency-Key", "key-3"),
				Map.of("items", List.of(Map.of("sku", "kb-1", "qty", 1), Map.of("sku", "mouse-1", "qty", 2))));
		var second = exchange(MockMvcRequestBuilders.post("/api/orders").header("Idempotency-Key", "key-3"),
				Map.of("items", List.of(Map.of("sku", "mouse-1", "qty", 2), Map.of("sku", "kb-1", "qty", 1))));

		assertThat(first.status()).isEqualTo(201);
		assertThat(second.status()).isEqualTo(200);
		assertThat(second.body().get("id").asText()).isEqualTo(first.body().get("id").asText());
		assertThat(stockOf("kb-1")).isEqualTo(9);
		assertThat(stockOf("mouse-1")).isEqualTo(8);
	}

	@Test
	void differentKeysCreateDifferentOrders() throws Exception {
		givenProduct("kb-1", 1_000L, 5);

		var first = givenOrder("key-a", Map.of("kb-1", 1));
		var second = givenOrder("key-b", Map.of("kb-1", 1));

		assertThat(first.status()).isEqualTo(201);
		assertThat(second.status()).isEqualTo(201);
		assertThat(second.body().get("id").asText()).isNotEqualTo(first.body().get("id").asText());
		assertThat(stockOf("kb-1")).isEqualTo(3);
	}

	@Test
	void priceSnapshotSurvivesCatalogChange() throws Exception {
		givenProduct("kb-1", 1_000L, 5);
		var id = givenOrder("key-4", Map.of("kb-1", 1)).body().get("id").asText();

		// меняем цену в каталоге и пополняем склад
		jdbc.update("UPDATE products SET price_cents = 2000, qty = 9 WHERE sku = 'kb-1'");

		var order = exchange(MockMvcRequestBuilders.get("/api/orders/" + id), null);

		assertThat(order.body().get("totalCents").asLong()).isEqualTo(1_000L);
		assertThat(order.body().get("items").get(0).get("priceCents").asLong()).isEqualTo(1_000L);
	}

}
