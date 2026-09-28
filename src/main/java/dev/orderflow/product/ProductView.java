package dev.orderflow.product;

import java.time.Instant;

public record ProductView(
		String sku,
		String name,
		long priceCents,
		int qty,
		Instant createdAt,
		Instant updatedAt) {

	public static ProductView of(Product product) {
		return new ProductView(
				product.sku(),
				product.name(),
				product.priceCents(),
				product.qty(),
				product.createdAt(),
				product.updatedAt());
	}

}
