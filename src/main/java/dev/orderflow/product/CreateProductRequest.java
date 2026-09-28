package dev.orderflow.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateProductRequest(

		@NotBlank
		@Size(max = 64)
		String sku,

		@NotBlank
		@Size(max = 200)
		String name,

		@PositiveOrZero
		long priceCents,

		@PositiveOrZero
		int qty) {
}
