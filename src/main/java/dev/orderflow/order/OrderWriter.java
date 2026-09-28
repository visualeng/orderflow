package dev.orderflow.order;

import dev.orderflow.common.InsufficientStockException;
import dev.orderflow.common.NotFoundException;
import dev.orderflow.product.Product;
import dev.orderflow.product.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Создание заказа целиком в одной транзакции. Вынесено отдельным компонентом
 * не ради красоты: {@code @Transactional} работает только через прокси, а
 * {@link OrderService#create} ловит нарушение уникального ключа уже вне
 * транзакции.
 */
@Component
public class OrderWriter {

	private final OrderRepository orders;
	private final ProductRepository products;

	public OrderWriter(OrderRepository orders, ProductRepository products) {
		this.orders = orders;
		this.products = products;
	}

	/**
	 * Порядок шагов важен: сначала занимаем уникальный ключ (flush), и только
	 * потом берём товары под блокировку. Если ключ уже занят, конфликт
	 * всплывает до того, как что-либо списано, и транзакция откатывается
	 * целиком.
	 *
	 * <p>Товары блокируются одним запросом и в порядке sku — так параллельные
	 * заказы с пересекающимися позициями не встают в тупик.
	 */
	@Transactional
	public OrderView create(String idempotencyKey, String requestHash, CreateOrderRequest request) {
		Order order = orders.saveAndFlush(new Order(UUID.randomUUID(), idempotencyKey, requestHash));

		List<OrderItemRequest> items = OrderItems.normalize(request.items());
		Map<String, Product> locked = lockProducts(items);

		for (OrderItemRequest item : items) {
			Product product = locked.get(item.sku());
			if (product.qty() < item.qty()) {
				throw new InsufficientStockException(item.sku(), product.qty(), item.qty());
			}
			product.decrease(item.qty());
			order.addItem(product.sku(), item.qty(), product.priceCents());
		}

		order.recalculateTotal();
		return OrderView.of(order);
	}

	private Map<String, Product> lockProducts(List<OrderItemRequest> items) {
		List<String> skus = items.stream().map(OrderItemRequest::sku).toList();

		Map<String, Product> locked = new HashMap<>();
		for (Product product : products.lockBySkus(skus)) {
			locked.put(product.sku(), product);
		}

		List<String> missing = skus.stream().filter(sku -> !locked.containsKey(sku)).toList();
		if (!missing.isEmpty()) {
			throw new NotFoundException("product_not_found", "товаров нет на складе: " + String.join(", ", missing));
		}
		return locked;
	}

}
