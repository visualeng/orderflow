package dev.orderflow.product;

import dev.orderflow.common.ConflictException;
import dev.orderflow.common.NotFoundException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

	private final ProductRepository products;

	public ProductService(ProductRepository products) {
		this.products = products;
	}

	@Transactional
	public ProductView create(CreateProductRequest request) {
		if (products.existsById(request.sku())) {
			throw new ConflictException("product_exists", "товар " + request.sku() + " уже есть");
		}
		Product product = new Product(request.sku(), request.name(), request.priceCents(), request.qty());
		return ProductView.of(products.save(product));
	}

	@Transactional(readOnly = true)
	public List<ProductView> list() {
		return products.findAll(Sort.by("sku")).stream().map(ProductView::of).toList();
	}

	@Transactional(readOnly = true)
	public ProductView get(String sku) {
		return ProductView.of(find(sku));
	}

	/** Пополнить склад: остаток и версию меняем в одной транзакции. */
	@Transactional
	public ProductView restock(String sku, int amount) {
		Product product = find(sku);
		product.increase(amount);
		return ProductView.of(product);
	}

	private Product find(String sku) {
		return products.findById(sku)
				.orElseThrow(() -> new NotFoundException("product_not_found", "товара " + sku + " нет"));
	}

}
