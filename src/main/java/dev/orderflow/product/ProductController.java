package dev.orderflow.product;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final ProductService products;

	public ProductController(ProductService products) {
		this.products = products;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductView create(@Valid @RequestBody CreateProductRequest request) {
		return products.create(request);
	}

	@GetMapping
	public List<ProductView> list() {
		return products.list();
	}

	@GetMapping("/{sku}")
	public ProductView get(@PathVariable String sku) {
		return products.get(sku);
	}

	/** Пополнить склад: остаток меняется в одной транзакции. */
	@PostMapping("/{sku}/restock")
	public ProductView restock(@PathVariable String sku, @Valid @RequestBody RestockRequest request) {
		return products.restock(sku, request.amount());
	}

}
