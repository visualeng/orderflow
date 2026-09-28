package dev.orderflow.order;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Нормализация позиций — чистая функция, база здесь не нужна. */
class OrderItemsTest {

	@Test
	void mergesDuplicatesAndSortsBySku() {
		var normalized = OrderItems.normalize(List.of(
				new OrderItemRequest("mouse-1", 1),
				new OrderItemRequest("kb-1", 2),
				new OrderItemRequest("mouse-1", 3)));

		assertThat(normalized).containsExactly(
				new OrderItemRequest("kb-1", 2),
				new OrderItemRequest("mouse-1", 4));
	}

	@Test
	void canonicalFormDoesNotDependOnItemOrder() {
		var first = List.of(new OrderItemRequest("kb-1", 2), new OrderItemRequest("mouse-1", 1));
		var second = List.of(new OrderItemRequest("mouse-1", 1), new OrderItemRequest("kb-1", 2));

		assertThat(OrderItems.canonical(first)).isEqualTo("kb-1x2,mouse-1x1");
		assertThat(OrderItems.hash(first)).isEqualTo(OrderItems.hash(second));
	}

	@Test
	void hashChangesWithQuantity() {
		var one = List.of(new OrderItemRequest("kb-1", 1));
		var two = List.of(new OrderItemRequest("kb-1", 2));

		assertThat(OrderItems.hash(one)).isNotEqualTo(OrderItems.hash(two));
		assertThat(OrderItems.hash(one)).hasSize(64);
	}

	@Test
	void rejectsQuantityOverflow() {
		var huge = List.of(
				new OrderItemRequest("kb-1", Integer.MAX_VALUE),
				new OrderItemRequest("kb-1", Integer.MAX_VALUE));

		assertThatThrownBy(() -> OrderItems.normalize(huge))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("kb-1");
	}

}
