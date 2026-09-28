package dev.orderflow.order;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record OrderView(
		String id,
		String status,
		long totalCents,
		List<OrderItemView> items,
		Instant createdAt,
		Instant updatedAt) {

	public static OrderView of(Order order) {
		var items = order.items().stream()
				.map(OrderItemView::of)
				.sorted(Comparator.comparing(OrderItemView::sku))
				.toList();
		return new OrderView(
				order.id().toString(),
				order.status().name(),
				order.totalCents(),
				items,
				order.createdAt(),
				order.updatedAt());
	}

}
