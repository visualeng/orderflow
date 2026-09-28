package dev.orderflow.common;

/** Товара не хватило: остаток известен, и клиенту полезно его увидеть. */
public class InsufficientStockException extends RuntimeException {

	private final String sku;
	private final int available;
	private final int requested;

	public InsufficientStockException(String sku, int available, int requested) {
		super("по товару " + sku + " осталось " + available + ", просят " + requested);
		this.sku = sku;
		this.available = available;
		this.requested = requested;
	}

	public String sku() {
		return sku;
	}

	public int available() {
		return available;
	}

	public int requested() {
		return requested;
	}

}
