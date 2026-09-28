package dev.orderflow.order;

public record OrderItemView(String sku, int qty, long priceCents) {

	public static OrderItemView of(OrderItem item) {
		return new OrderItemView(item.sku(), item.qty(), item.priceCents());
	}

}
