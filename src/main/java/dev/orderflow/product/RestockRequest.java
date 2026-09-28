package dev.orderflow.product;

import jakarta.validation.constraints.Positive;

public record RestockRequest(@Positive int amount) {
}
