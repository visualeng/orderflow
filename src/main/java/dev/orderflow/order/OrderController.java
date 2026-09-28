package dev.orderflow.order;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final OrderService orders;

	public OrderController(OrderService orders) {
		this.orders = orders;
	}

	/** Первый запрос с ключом — 201, повтор с тем же ключом — 200 и тот же заказ. */
	@PostMapping
	public ResponseEntity<OrderView> create(
			@RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
			@Valid @RequestBody CreateOrderRequest request) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new IllegalArgumentException("нужен заголовок Idempotency-Key");
		}
		var creation = orders.create(idempotencyKey.trim(), request);
		return ResponseEntity.status(creation.created() ? HttpStatus.CREATED : HttpStatus.OK)
				.body(creation.order());
	}

	@GetMapping
	public PageView<OrderView> list(
			@RequestParam(name = "status", required = false) OrderStatus status,
			@RequestParam(name = "page", defaultValue = "1") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		if (page < 1 || size < 1 || size > 100) {
			throw new IllegalArgumentException("page от 1, size от 1 до 100");
		}
		return orders.list(status, page, size);
	}

	@GetMapping("/{id}")
	public OrderView get(@PathVariable UUID id) {
		return orders.get(id);
	}

	@PostMapping("/{id}/confirm")
	public OrderView confirm(@PathVariable UUID id) {
		return orders.confirm(id);
	}

	@PostMapping("/{id}/ship")
	public OrderView ship(@PathVariable UUID id) {
		return orders.ship(id);
	}

	@PostMapping("/{id}/cancel")
	public OrderView cancel(@PathVariable UUID id) {
		return orders.cancel(id);
	}

}
