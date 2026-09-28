package dev.orderflow.order;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Приводит позиции заказа к каноническому виду: без дублей и отсортированно
 * по sku. Из канона считается хеш запроса, поэтому «kb-1,2 + мышь» и
 * «мышь + kb-1,2» — это один и тот же заказ для идемпотентности.
 */
final class OrderItems {

	private OrderItems() {
	}

	static List<OrderItemRequest> normalize(List<OrderItemRequest> items) {
		Map<String, Integer> merged = new TreeMap<>();
		for (OrderItemRequest item : items) {
			try {
				merged.merge(item.sku(), item.qty(), Math::addExact);
			} catch (ArithmeticException e) {
				throw new IllegalArgumentException("слишком много единиц по товару " + item.sku());
			}
		}
		return merged.entrySet().stream()
				.map(entry -> new OrderItemRequest(entry.getKey(), entry.getValue()))
				.toList();
	}

	static String canonical(List<OrderItemRequest> items) {
		return normalize(items).stream()
				.map(item -> item.sku() + "x" + item.qty())
				.collect(Collectors.joining(","));
	}

	static String hash(List<OrderItemRequest> items) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(canonical(items).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(bytes);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("в JVM нет SHA-256", e);
		}
	}

}
