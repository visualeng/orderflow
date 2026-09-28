package dev.orderflow.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * Позиция заказа. Цена здесь — снимок на момент заказа: правка прайса в
 * каталоге не должна переписывать историю.
 */
@Embeddable
public class OrderItem {

	@Column(name = "sku", length = 64, nullable = false)
	private String sku;

	@Column(name = "qty", nullable = false)
	private int qty;

	@Column(name = "price_cents", nullable = false)
	private long priceCents;

	protected OrderItem() {
		// для JPA
	}

	public OrderItem(String sku, int qty, long priceCents) {
		this.sku = sku;
		this.qty = qty;
		this.priceCents = priceCents;
	}

	public String sku() {
		return sku;
	}

	public int qty() {
		return qty;
	}

	public long priceCents() {
		return priceCents;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof OrderItem other)) {
			return false;
		}
		return qty == other.qty && priceCents == other.priceCents && Objects.equals(sku, other.sku);
	}

	@Override
	public int hashCode() {
		return Objects.hash(sku, qty, priceCents);
	}

}
