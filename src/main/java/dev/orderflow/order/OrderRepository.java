package dev.orderflow.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

	Optional<Order> findByIdempotencyKey(String idempotencyKey);

	/**
	 * Позиции тянутся вместе с заказом одним запросом: коллекция по
	 * умолчанию ленивая, и без графа на список ушёл бы запрос на заказ.
	 */
	@Override
	@EntityGraph(attributePaths = "items")
	Page<Order> findAll(Pageable pageable);

	@EntityGraph(attributePaths = "items")
	Page<Order> findByStatus(OrderStatus status, Pageable pageable);

}
