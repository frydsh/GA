package com.google.analytics.tracking.android;

import android.text.TextUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Transaction {
	private final String mTransactionId;
	private final String mAffiliation;
	private final long mTotalCostInMicros;
	private final long mTotalTaxInMicros;
	private final long mShippingCostInMicros;
	private final String mCurrencyCode;
	private final Map<String, Item> mItems;

	private Transaction(Builder builder) {
		this.mTransactionId = builder.mTransactionId;
		this.mTotalCostInMicros = builder.mTotalCostInMicros;
		this.mAffiliation = builder.mAffiliation;
		this.mTotalTaxInMicros = builder.mTotalTaxInMicros;
		this.mShippingCostInMicros = builder.mShippingCostInMicros;
		this.mCurrencyCode = builder.mCurrencyCode;
		this.mItems = new HashMap<String, Item>();
	}

	public String getTransactionId() {
		return this.mTransactionId;
	}

	public String getAffiliation() {
		return this.mAffiliation;
	}

	public long getTotalCostInMicros() {
		return this.mTotalCostInMicros;
	}

	public long getTotalTaxInMicros() {
		return this.mTotalTaxInMicros;
	}

	public long getShippingCostInMicros() {
		return this.mShippingCostInMicros;
	}

	public String getCurrencyCode() {
		return this.mCurrencyCode;
	}

	public void addItem(Item item) {
		this.mItems.put(item.getSKU(), item);
	}

	public List<Item> getItems() {
		return new ArrayList<Item>(this.mItems.values());
	}

	public static final class Item {
		private final String mSKU;
		private final String mName;
		private final String mCategory;
		private final long mPriceInMicros;
		private final long mQuantity;

		private Item(Builder builder) {
			this.mSKU = builder.mSKU;
			this.mPriceInMicros = builder.mPriceInMicros;
			this.mQuantity = builder.mQuantity;
			this.mName = builder.mName;
			this.mCategory = builder.mCategory;
		}

		public String getSKU() {
			return this.mSKU;
		}

		public String getName() {
			return this.mName;
		}

		public String getCategory() {
			return this.mCategory;
		}

		public long getPriceInMicros() {
			return this.mPriceInMicros;
		}

		public long getQuantity() {
			return this.mQuantity;
		}

		public static final class Builder {
			private final String mSKU;
			private final long mPriceInMicros;
			private final long mQuantity;
			private final String mName;
			private String mCategory = null;

			public Builder(String SKU, String name, long priceInMicros, long quantity) {
				if (TextUtils.isEmpty(SKU)) {
					throw new IllegalArgumentException("SKU must not be empty or null");
				}
				if (TextUtils.isEmpty(name)) {
					throw new IllegalArgumentException("name must not be empty or null");
				}
				this.mSKU = SKU;
				this.mName = name;
				this.mPriceInMicros = priceInMicros;
				this.mQuantity = quantity;
			}

			public Builder setProductCategory(String productCategory) {
				this.mCategory = productCategory;
				return this;
			}

			public Transaction.Item build() {
				return new Transaction.Item(this);
			}
		}
	}

	public static final class Builder {
		private final String mTransactionId;
		private String mAffiliation = null;
		private final long mTotalCostInMicros;
		private long mTotalTaxInMicros = 0;
		private long mShippingCostInMicros = 0;
		private String mCurrencyCode = null;

		public Builder(String transactionId, long totalCostInMicros) {
			if (TextUtils.isEmpty(transactionId)) {
				throw new IllegalArgumentException("orderId must not be empty or null");
			}
			this.mTransactionId = transactionId;
			this.mTotalCostInMicros = totalCostInMicros;
		}

		public Builder setAffiliation(String affiliation) {
			this.mAffiliation = affiliation;
			return this;
		}

		public Builder setTotalTaxInMicros(long totalTaxInMicros) {
			this.mTotalTaxInMicros = totalTaxInMicros;
			return this;
		}

		public Builder setShippingCostInMicros(long shippingCostInMicros) {
			this.mShippingCostInMicros = shippingCostInMicros;
			return this;
		}

		public Builder setCurrencyCode(String currencyCode) {
			this.mCurrencyCode = currencyCode;
			return this;
		}

		public Transaction build() {
			return new Transaction(this);
		}
	}
}