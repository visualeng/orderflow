package dev.orderflow.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Товар и его складской остаток. Остаток меняется только под
 * пессимистической блокировкой строки — об этом методах ниже.
 */
@Entity
@Table(name = "products")
public class Product {

	@Id
	@Column(name = "sku", length = 64, nullable = false)
	private String sku;

	@Column(name = "name", length = 200, nullable = false)
	private String name;

	/** Цена в копейках: деньги в float не храним. */
	@Column(name = "price_cents", nullable = false)
	private long priceCents;

	@Column(name = "qty", nullable = false)
	private int qty;

	@Version
	@Column(name = "version", nullable = false)
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Product() {
		// для JPA
	}

	public Product(String sku, String name, long priceCents, int qty) {
		Instant now = Instant.now();
		this.sku = sku;
		this.name = name;
		this.priceCents = priceCents;
		this.qty = qty;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/** Пополнение остатка. */
	public void increase(int amount) {
		if (amount <= 0) {
			throw new IllegalArgumentException("количество должно быть положительным");
		}
		this.qty = Math.addExact(this.qty, amount);
		this.updatedAt = Instant.now();
	}

	/**
	 * Списание остатка под заказ. Вызывать только под блокировкой строки,
	 * иначе два заказа могут списать один и тот же остаток.
	 */
	public void decrease(int amount) {
		if (amount <= 0 || amount > qty) {
			throw new IllegalStateException("недостаточно остатка по " + sku);
		}
		this.qty -= amount;
		this.updatedAt = Instant.now();
	}

	public String sku() {
		return sku;
	}

	public String name() {
		return name;
	}

	public long priceCents() {
		return priceCents;
	}

	public int qty() {
		return qty;
	}

	public long version() {
		return version;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}

}
