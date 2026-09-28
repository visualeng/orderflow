package dev.orderflow.order;

import dev.orderflow.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Проверки на настоящей конкуренции: несколько запросов одновременно борются
 * за один и тот же остаток, за один и тот же ключ и пересекающиеся позиции.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
class ConcurrentOrdersTest extends ApiIntegrationTest {

	@Test
	void parallelOrdersShareLimitedStock() throws Exception {
		givenProduct("kb-1", 1_000L, 5);

		List<Integer> statuses = inParallel(10, i -> givenOrder("race-" + i, Map.of("kb-1", 1)).status());

		assertThat(statuses).filteredOn(status -> status == 201).hasSize(5);
		assertThat(statuses).filteredOn(status -> status == 409).hasSize(5);
		// остаток ушёл ровно под пять заказов и ни одним больше
		assertThat(stockOf("kb-1")).isZero();
		assertThat(orderCount()).isEqualTo(5);
	}

	@Test
	void parallelOrdersWithOverlappingItemsDoNotDeadlock() throws Exception {
		givenProduct("kb-1", 1_000L, 50);
		givenProduct("mouse-1", 2_000L, 50);
		givenProduct("pad-1", 3_000L, 50);

		// заказы пересекаются по составу и подаются в разном порядке —
		// при едином порядке блокировок дедлока не будет
		List<Integer> statuses = inParallel(6, i -> givenOrder("overlap-" + i, switch (i % 3) {
			case 0 -> Map.of("kb-1", 1, "mouse-1", 1, "pad-1", 1);
			case 1 -> Map.of("pad-1", 1, "kb-1", 1);
			default -> Map.of("mouse-1", 1, "kb-1", 1, "pad-1", 1);
		}).status());

		assertThat(statuses).allMatch(status -> status == 201);
		assertThat(orderCount()).isEqualTo(6);
		// kb-1 и pad-1 есть во всех трёх вариантах заказа, mouse-1 — в двух
		assertThat(stockOf("kb-1")).isEqualTo(44);
		assertThat(stockOf("pad-1")).isEqualTo(44);
		assertThat(stockOf("mouse-1")).isEqualTo(46);
	}

	@Test
	void parallelRequestsWithSameKeyCreateOneOrder() throws Exception {
		givenProduct("kb-1", 1_000L, 10);

		List<Integer> statuses = inParallel(6, i -> givenOrder("same-key", Map.of("kb-1", 2)).status());

		// победитель получает 201, остальные видят тот же заказ
		assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
		assertThat(statuses).allMatch(status -> status == 201 || status == 200);
		assertThat(orderCount()).isEqualTo(1);
		assertThat(stockOf("kb-1")).isEqualTo(8);
	}

	/** Задача, которую можно запустить из потока: индекс и результат. */
	@FunctionalInterface
	private interface IntTask {
		int run(int index) throws Exception;
	}

	/** Запускает задачи параллельно и собирает их результаты. */
	private List<Integer> inParallel(int count, IntTask task) throws Exception {
		var pool = Executors.newFixedThreadPool(count);
		try {
			var futures = new ArrayList<Future<Integer>>(count);
			for (int i = 0; i < count; i++) {
				int index = i;
				futures.add(pool.submit(() -> task.run(index)));
			}
			var results = new ArrayList<Integer>(count);
			for (var future : futures) {
				results.add(future.get(60, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			pool.shutdownNow();
		}
	}

}
