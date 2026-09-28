package dev.orderflow.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record OrderItemRequest(

		@NotBlank
		@Size(max = 64)
		String sku,

		@Positive
		int qty) {
}
