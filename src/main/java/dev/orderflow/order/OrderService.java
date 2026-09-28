package dev.orderflow.order;

import dev.orderflow.common.ConflictException;
import dev.orderflow.common.NotFoundException;
import dev.orderflow.product.Product;
import dev.orderflow.product.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.UUID;

@Service
public class OrderService {

	private final OrderRepository orders;
	private final ProductRepository products;
	private final OrderWriter writer;

	public OrderService(OrderRepository orders, ProductRepository products, OrderWriter writer) {
		this.orders = orders;
		this.products = products;
		this.writer = writer;
	}

	/** created=false — это повтор по ключу, а не новый заказ. */
	public record Creation(OrderView order, boolean created) {
	}

	/**
	 * Создаёт заказ идемпотентно: повтор с тем же ключом и тем же составом
	 * возвращает тот же заказ, но остатки второй раз не списываются.
	 */
	public Creation create(String idempotencyKey, CreateOrderRequest request) {
		String hash = OrderItems.hash(request.items());

		var existing = orders.findByIdempotencyKey(idempotencyKey);
		if (existing.isPresent()) {
			return replay(existing.get(), hash);
		}

		try {
			return new Creation(writer.create(idempotencyKey, hash, request), true);
		} catch (DataIntegrityViolationException e) {
			// Параллельный повтор того же ключа выиграл гонку за UNIQUE:
			// отдаём его результат, а не роняем запрос.
			Order winner = orders.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> e);
			return replay(winner, hash);
		}
	}

	@Transactional(readOnly = true)
	public OrderView get(UUID id) {
		return OrderView.of(find(id));
	}

	@Transactional(readOnly = true)
	public PageView<OrderView> list(OrderStatus status, int page, int size) {
		Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		Page<Order> found = status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable);
		return PageView.of(found.map(OrderView::of));
	}

	@Transactional
	public OrderView confirm(UUID id) {
		return move(id, OrderStatus.NEW, OrderStatus.CONFIRMED, "подтверждение");
	}

	@Transactional
	public OrderView ship(UUID id) {
		return move(id, OrderStatus.CONFIRMED, OrderStatus.SHIPPED, "отгрузка");
	}

	/** Отмена возвращает товар на склад в той же транзакции, что и смена статуса. */
	@Transactional
	public OrderView cancel(UUID id) {
		Order order = find(id);
		if (order.status() == OrderStatus.SHIPPED || order.status() == OrderStatus.CANCELLED) {
			throw new ConflictException("invalid_transition", "отменить нельзя: заказ в статусе " + order.status());
		}

		// Сортировка по sku — тот же порядок блокировок, что и при создании.
		order.items().stream()
				.sorted(Comparator.comparing(OrderItem::sku))
				.forEach(item -> {
					Product product = products.lockBySku(item.sku()).orElseThrow(
							() -> new NotFoundException("product_not_found", "товара " + item.sku() + " нет"));
					product.increase(item.qty());
				});

		order.transitionTo(OrderStatus.CANCELLED);
		return OrderView.of(order);
	}

	private OrderView move(UUID id, OrderStatus from, OrderStatus to, String action) {
		Order order = find(id);
		if (order.status() != from) {
			throw new ConflictException("invalid_transition",
					action + " возможна только из статуса " + from + ", а заказ в " + order.status());
		}
		order.transitionTo(to);
		return OrderView.of(order);
	}

	private Order find(UUID id) {
		return orders.findById(id)
				.orElseThrow(() -> new NotFoundException("order_not_found", "заказа " + id + " нет"));
	}

	private Creation replay(Order existing, String hash) {
		if (!existing.requestHash().equals(hash)) {
			throw new ConflictException("idempotency_key_reused",
					"этот Idempotency-Key уже использовали с другим составом заказа");
		}
		return new Creation(OrderView.of(existing), false);
	}

}
