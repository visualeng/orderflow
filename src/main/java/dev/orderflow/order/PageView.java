package dev.orderflow.order;

import org.springframework.data.domain.Page;

import java.util.List;

/** Обёртка страницы: 1-based номера снаружи, как их ждёт клиент. */
public record PageView<T>(List<T> items, long total, int page, int size) {

	public static <T> PageView<T> of(Page<T> page) {
		return new PageView<>(page.getContent(), page.getTotalElements(), page.getNumber() + 1, page.getSize());
	}

}
