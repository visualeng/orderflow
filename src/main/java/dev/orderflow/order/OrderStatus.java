package dev.orderflow.order;

/** Жизненный цикл заказа: NEW → CONFIRMED → SHIPPED, отмена из NEW/CONFIRMED. */
public enum OrderStatus {
	NEW,
	CONFIRMED,
	SHIPPED,
	CANCELLED
}
