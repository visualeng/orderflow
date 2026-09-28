package dev.orderflow.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {

	/**
	 * Берёт товары под пессимистическую блокировку (SELECT ... FOR UPDATE).
	 *
	 * Сортировка по sku — не украшение: заказы блокируют строки всегда в одном
	 * и том же порядке, поэтому два параллельных заказа с пересекающимися
	 * позициями не встанут в тупик.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Product p where p.sku in :skus order by p.sku")
	List<Product> lockBySkus(Collection<String> skus);

	/** То же, но для одного товара — возвращение остатка при отмене заказа. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Product p where p.sku = :sku")
	Optional<Product> lockBySku(@Param("sku") String sku);

}
