package dev.orderflow.order;

import dev.orderflow.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class OrderApiTest extends ApiIntegrationTest {

	@Test
	void createsOrderAndDeductsStock() throws Exception {
		givenProduct("kb-1", 49_900L, 3);

		var result = givenOrder("order-1", Map.of("kb-1", 2));

		assertThat(result.status()).isEqualTo(201);
		assertThat(result.body().get("status").asText()).isEqualTo("NEW");
		assertThat(result.body().get("totalCents").asLong()).isEqualTo(99_800L);
		assertThat(result.body().get("items")).hasSize(1);
		assertThat(result.body().get("items").get(0).get("sku").asText()).isEqualTo("kb-1");
		assertThat(result.body().get("items").get(0).get("priceCents").asLong()).isEqualTo(49_900L);
		assertThat(stockOf("kb-1")).isEqualTo(1);
	}

	@Test
	void mergesRepeatedSkuInsideOneOrder() throws Exception {
		givenProduct("kb-1", 1_000L, 5);

		var result = exchange(MockMvcRequestBuilders.post("/api/orders").header("Idempotency-Key", "order-merge"),
				Map.of("items", java.util.List.of(
						Map.of("sku", "kb-1", "qty", 1),
						Map.of("sku", "kb-1", "qty", 2))));

		assertThat(result.status()).isEqualTo(201);
		assertThat(result.body().get("items")).hasSize(1);
		assertThat(result.body().get("items").get(0).get("qty").asInt()).isEqualTo(3);
		assertThat(stockOf("kb-1")).isEqualTo(2);
	}

	@Test
	void rejectsOrderWithoutStock() throws Exception {
		givenProduct("kb-1", 1_000L, 1);

		var result = givenOrder("order-2", Map.of("kb-1", 2));

		assertThat(result.status()).isEqualTo(409);
		assertThat(result.body().get("code").asText()).isEqualTo("insufficient_stock");
		assertThat(result.body().get("sku").asText()).isEqualTo("kb-1");
		assertThat(result.body().get("available").asInt()).isEqualTo(1);
		assertThat(result.body().get("requested").asInt()).isEqualTo(2);
		// остаток не тронут, заказ не создан — транзакция откатилась целиком
		assertThat(stockOf("kb-1")).isEqualTo(1);
		assertThat(orderCount()).isZero();
	}

	@Test
	void rejectsUnknownSku() throws Exception {
		givenProduct("kb-1", 1_000L, 5);

		var result = givenOrder("order-3", Map.of("ghost-1", 1));

		assertThat(result.status()).isEqualTo(404);
		assertThat(result.body().get("code").asText()).isEqualTo("product_not_found");
		assertThat(orderCount()).isZero();
	}

	@Test
	void requiresIdempotencyKey() throws Exception {
		givenProduct("kb-1", 1_000L, 5);

		var result = exchange(MockMvcRequestBuilders.post("/api/orders"),
				Map.of("items", java.util.List.of(Map.of("sku", "kb-1", "qty", 1))));

		assertThat(result.status()).isEqualTo(400);
		assertThat(result.body().get("code").asText()).isEqualTo("bad_request");
		assertThat(stockOf("kb-1")).isEqualTo(5);
	}

	@Test
	void rejectsEmptyItems() throws Exception {
		var result = exchange(MockMvcRequestBuilders.post("/api/orders").header("Idempotency-Key", "order-4"),
				Map.of("items", java.util.List.of()));

		assertThat(result.status()).isEqualTo(400);
		assertThat(result.body().get("code").asText()).isEqualTo("validation_failed");
	}

	@Test
	void returnsOrderById() throws Exception {
		givenProduct("kb-1", 1_000L, 5);
		var created = givenOrder("order-5", Map.of("kb-1", 1));
		var id = created.body().get("id").asText();

		var found = exchange(MockMvcRequestBuilders.get("/api/orders/" + id), null);

		assertThat(found.status()).isEqualTo(200);
		assertThat(found.body().get("id").asText()).isEqualTo(id);
		assertThat(found.body().get("items").get(0).get("sku").asText()).isEqualTo("kb-1");
	}

	@Test
	void returnsNotFoundForUnknownOrder() throws Exception {
		var result = exchange(MockMvcRequestBuilders.get("/api/orders/" + UUID.randomUUID()), null);

		assertThat(result.status()).isEqualTo(404);
		assertThat(result.body().get("code").asText()).isEqualTo("order_not_found");
	}

	@Test
	void listsOrdersWithFilterAndPagination() throws Exception {
		givenProduct("kb-1", 1_000L, 10);
		givenOrder("list-1", Map.of("kb-1", 1));
		givenOrder("list-2", Map.of("kb-1", 1));
		var third = givenOrder("list-3", Map.of("kb-1", 1));
		exchange(MockMvcRequestBuilders.post("/api/orders/" + third.body().get("id").asText() + "/confirm"), null);

		var all = exchange(MockMvcRequestBuilders.get("/api/orders").param("size", "2"), null);
		assertThat(all.status()).isEqualTo(200);
		assertThat(all.body().get("total").asInt()).isEqualTo(3);
		assertThat(all.body().get("items")).hasSize(2);
		assertThat(all.body().get("page").asInt()).isEqualTo(1);
		assertThat(all.body().get("size").asInt()).isEqualTo(2);

		var secondPage = exchange(MockMvcRequestBuilders.get("/api/orders").param("page", "2")
				.param("size", "2"), null);
		assertThat(secondPage.body().get("items")).hasSize(1);
		assertThat(secondPage.body().get("page").asInt()).isEqualTo(2);

		var confirmed = exchange(MockMvcRequestBuilders.get("/api/orders").param("status", "CONFIRMED"), null);
		assertThat(confirmed.body().get("total").asInt()).isEqualTo(1);
		assertThat(confirmed.body().get("items").get(0).get("status").asText()).isEqualTo("CONFIRMED");
	}

	@Test
	void rejectsBadPagination() throws Exception {
		var result = exchange(MockMvcRequestBuilders.get("/api/orders").param("size", "1000"), null);

		assertThat(result.status()).isEqualTo(400);
	}

	@Test
	void walksThroughConfirmAndShip() throws Exception {
		givenProduct("kb-1", 1_000L, 5);
		var id = givenOrder("flow-1", Map.of("kb-1", 1)).body().get("id").asText();

		var confirmed = exchange(MockMvcRequestBuilders.post("/api/orders/" + id + "/confirm"), null);
		assertThat(confirmed.status()).isEqualTo(200);
		assertThat(confirmed.body().get("status").asText()).isEqualTo("CONFIRMED");

		var shipped = exchange(MockMvcRequestBuilders.post("/api/orders/" + id + "/ship"), null);
		assertThat(shipped.status()).isEqualTo(200);
		assertThat(shipped.body().get("status").asText()).isEqualTo("SHIPPED");

		// из SHIPPED ничего не сделать
		var again = exchange(MockMvcRequestBuilders.post("/api/orders/" + id + "/confirm"), null);
		assertThat(again.status()).isEqualTo(409);
		assertThat(again.body().get("code").asText()).isEqualTo("invalid_transition");
	}

	@Test
	void cannotShipBeforeConfirm() throws Exception {
		givenProduct("kb-1", 1_000L, 5);
		var id = givenOrder("flow-2", Map.of("kb-1", 1)).body().get("id").asText();

		var result = exchange(MockMvcRequestBuilders.post("/api/orders/" + id + "/ship"), null);

		assertThat(result.status()).isEqualTo(409);
	}

	@Test
	void cancelReturnsStock() throws Exception {
		givenProduct("kb-1", 1_000L, 5);
		var id = givenOrder("cancel-1", Map.of("kb-1", 2)).body().get("id").asText();
		assertThat(stockOf("kb-1")).isEqualTo(3);

		var cancelled = exchange(MockMvcRequestBuilders.post("/api/orders/" + id + "/cancel"), null);

		assertThat(cancelled.status()).isEqualTo(200);
		assertThat(cancelled.body().get("status").asText()).isEqualTo("CANCELLED");
		assertThat(stockOf("kb-1")).isEqualTo(5);

		var twice = exchange(MockMvcRequestBuilders.post("/api/orders/" + id + "/cancel"), null);
		assertThat(twice.status()).isEqualTo(409);
		assertThat(stockOf("kb-1")).isEqualTo(5);
	}

	@Test
	void cannotCancelShippedOrder() throws Exception {
		givenProduct("kb-1", 1_000L, 5);
		var id = givenOrder("cancel-2", Map.of("kb-1", 1)).body().get("id").asText();
		exchange(MockMvcRequestBuilders.post("/api/orders/" + id + "/confirm"), null);
		exchange(MockMvcRequestBuilders.post("/api/orders/" + id + "/ship"), null);

		var result = exchange(MockMvcRequestBuilders.post("/api/orders/" + id + "/cancel"), null);

		assertThat(result.status()).isEqualTo(409);
		assertThat(stockOf("kb-1")).isEqualTo(4);
	}

}
