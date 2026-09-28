package dev.orderflow;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Обвязка интеграционных тестов: поднимает приложение на живой базе.
 *
 * База (`orderflow_test`) должна существовать, схему накатывает Flyway.
 * Без `TEST_DATABASE_URL` такие тесты пропускаются — на CI переменная задана.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
public abstract class ApiIntegrationTest {

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected ObjectMapper json;

	@Autowired
	protected JdbcTemplate jdbc;

	@BeforeEach
	void cleanDatabase() {
		jdbc.execute("TRUNCATE order_items, orders, products RESTART IDENTITY CASCADE");
	}

	/** Ответ API: код и разобранное тело (пустое тело — null). */
	protected record Result(int status, JsonNode body) {
	}

	protected Result exchange(MockHttpServletRequestBuilder request, Object body) throws Exception {
		if (body != null) {
			request.contentType(MediaType.APPLICATION_JSON);
			request.content(write(body));
		}
		var response = mvc.perform(request).andReturn().getResponse();
		var text = response.getContentAsString(StandardCharsets.UTF_8);
		return new Result(response.getStatus(), text.isBlank() ? null : json.readTree(text));
	}

	protected String write(Object value) {
		try {
			return json.writeValueAsString(value);
		} catch (Exception e) {
			throw new IllegalStateException("не сериализовать тело запроса", e);
		}
	}

	/** Завести товар на складе и вернуть его sku. */
	protected String givenProduct(String sku, long priceCents, int qty) throws Exception {
		Result result = exchange(
				org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/products"),
				Map.of("sku", sku, "name", "товар " + sku, "priceCents", priceCents, "qty", qty));
		if (result.status() != 201) {
			throw new IllegalStateException("товар не создался: " + result.status() + " " + result.body());
		}
		return sku;
	}

	/** Создать заказ с ключом идемпотентности; состав задаётся картой sku → количество. */
	protected Result givenOrder(String idempotencyKey, Map<String, Integer> items) throws Exception {
		var body = Map.of("items", items.entrySet().stream()
				.map(entry -> Map.of("sku", entry.getKey(), "qty", entry.getValue()))
				.toList());
		return exchange(
				org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/orders")
						.header("Idempotency-Key", idempotencyKey),
				body);
	}

	protected int stockOf(String sku) {
		Integer qty = jdbc.queryForObject("SELECT qty FROM products WHERE sku = ?", Integer.class, sku);
		return qty == null ? 0 : qty;
	}

	protected int orderCount() {
		Integer count = jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class);
		return count == null ? 0 : count;
	}

}
