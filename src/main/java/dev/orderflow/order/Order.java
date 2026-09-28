package dev.orderflow.order;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Заказ. Ключ идемпотентности уникален: он же защищает от двойного
 * списания остатков при повторе запроса.
 */
@Entity
@Table(name = "orders")
public class Order {

	@Id
	@Column(name = "id", nullable = false)
	private UUID id;

	@Column(name = "idempotency_key", length = 200, nullable = false)
	private String idempotencyKey;

	/** Хеш каноничного состава заказа: по нему повтор отличается от другого заказа. */
	@Column(name = "request_hash", length = 64, nullable = false)
	private String requestHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", length = 20, nullable = false)
	private OrderStatus status;

	@Column(name = "total_cents", nullable = false)
	private long totalCents;

	/** Позиции лежат в order_items; составной ключ там — (order_id, sku). */
	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
	private Set<OrderItem> items = new LinkedHashSet<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Order() {
		// для JPA
	}

	public Order(UUID id, String idempotencyKey, String requestHash) {
		Instant now = Instant.now();
		this.id = id;
		this.idempotencyKey = idempotencyKey;
		this.requestHash = requestHash;
		this.status = OrderStatus.NEW;
		this.totalCents = 0;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void addItem(String sku, int qty, long priceCents) {
		items.add(new OrderItem(sku, qty, priceCents));
	}

	/** Сумма считается из позиций, а не приходит от клиента. */
	public void recalculateTotal() {
		totalCents = items.stream().mapToLong(item -> (long) item.qty() * item.priceCents()).sum();
		updatedAt = Instant.now();
	}

	public void transitionTo(OrderStatus next) {
		this.status = next;
		this.updatedAt = Instant.now();
	}

	public UUID id() {
		return id;
	}

	public String idempotencyKey() {
		return idempotencyKey;
	}

	public String requestHash() {
		return requestHash;
	}

	public OrderStatus status() {
		return status;
	}

	public long totalCents() {
		return totalCents;
	}

	public Set<OrderItem> items() {
		return items;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}

}
