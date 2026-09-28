package dev.orderflow.product;

import dev.orderflow.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductApiTest extends ApiIntegrationTest {

	@Test
	void createsProduct() throws Exception {
		var result = exchange(MockMvcRequestBuilders.post("/api/products"),
				Map.of("sku", "kb-1", "name", "клавиатура", "priceCents", 499_00L, "qty", 10));

		assertThat(result.status()).isEqualTo(201);
		assertThat(result.body().get("sku").asText()).isEqualTo("kb-1");
		assertThat(result.body().get("priceCents").asLong()).isEqualTo(49900L);
		assertThat(result.body().get("qty").asInt()).isEqualTo(10);
	}

	@Test
	void rejectsDuplicateSku() throws Exception {
		givenProduct("kb-1", 1000, 5);

		var result = exchange(MockMvcRequestBuilders.post("/api/products"),
				Map.of("sku", "kb-1", "name", "ещё раз", "priceCents", 1, "qty", 1));

		assertThat(result.status()).isEqualTo(409);
		assertThat(result.body().get("code").asText()).isEqualTo("product_exists");
	}

	@Test
	void validatesBody() throws Exception {
		var negativePrice = exchange(MockMvcRequestBuilders.post("/api/products"),
				Map.of("sku", "kb-1", "name", "клавиатура", "priceCents", -1, "qty", 1));
		assertThat(negativePrice.status()).isEqualTo(400);
		assertThat(negativePrice.body().get("code").asText()).isEqualTo("validation_failed");
		assertThat(negativePrice.body().get("errors").toString()).contains("priceCents");

		var blankSku = exchange(MockMvcRequestBuilders.post("/api/products"),
				Map.of("sku", "  ", "name", "клавиатура", "priceCents", 1, "qty", 1));
		assertThat(blankSku.status()).isEqualTo(400);
	}

	@Test
	void returnsNotFoundForUnknownSku() throws Exception {
		var result = exchange(MockMvcRequestBuilders.get("/api/products/nope"), null);

		assertThat(result.status()).isEqualTo(404);
		assertThat(result.body().get("code").asText()).isEqualTo("product_not_found");
	}

	@Test
	void returnsNotFoundForMalformedJson() throws Exception {
		var request = MockMvcRequestBuilders.post("/api/products")
				.contentType("application/json")
				.content("{ битый");
		var response = mvc.perform(request).andReturn().getResponse();

		assertThat(response.getStatus()).isEqualTo(400);
	}

	@Test
	void restockIncreasesQuantity() throws Exception {
		givenProduct("kb-1", 1000, 5);

		var result = exchange(MockMvcRequestBuilders.post("/api/products/kb-1/restock"), Map.of("amount", 7));

		assertThat(result.status()).isEqualTo(200);
		assertThat(result.body().get("qty").asInt()).isEqualTo(12);
		assertThat(stockOf("kb-1")).isEqualTo(12);
	}

	@Test
	void restockRejectsNonPositiveAmount() throws Exception {
		givenProduct("kb-1", 1000, 5);

		var result = exchange(MockMvcRequestBuilders.post("/api/products/kb-1/restock"), Map.of("amount", 0));

		assertThat(result.status()).isEqualTo(400);
		assertThat(stockOf("kb-1")).isEqualTo(5);
	}

	@Test
	void listsProductsSortedBySku() throws Exception {
		givenProduct("mouse-1", 2500, 3);
		givenProduct("kb-1", 49900, 2);

		var result = exchange(MockMvcRequestBuilders.get("/api/products"), null);

		assertThat(result.status()).isEqualTo(200);
		assertThat(result.body()).hasSize(2);
		assertThat(result.body().get(0).get("sku").asText()).isEqualTo("kb-1");
		assertThat(result.body().get(1).get("sku").asText()).isEqualTo("mouse-1");
	}

}
