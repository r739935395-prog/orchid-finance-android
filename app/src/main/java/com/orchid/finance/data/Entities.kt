package com.orchid.finance.data

import androidx.room.*

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "full_name") val fullName: String = "",
    @ColumnInfo(name = "password_hash") val passwordHash: String,
    @ColumnInfo(name = "role") val role: String = "admin",
    @ColumnInfo(name = "active") val active: Int = 1,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey val token: String,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "created_at") val createdAt: String,
    @ColumnInfo(name = "expires_at") val expiresAt: String
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name_ar") val nameAr: String,
    @ColumnInfo(name = "name_en") val nameEn: String
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sku: String? = null,
    val barcode: String? = null,
    @ColumnInfo(name = "name_ar") val nameAr: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "purchase_cost") val purchaseCost: Double = 0.0,
    @ColumnInfo(name = "selling_price") val sellingPrice: Double = 0.0,
    val quantity: Double = 0.0,
    @ColumnInfo(name = "avg_cost") val avgCost: Double = 0.0,
    @ColumnInfo(name = "min_stock") val minStock: Double = 0.0,
    @ColumnInfo(name = "image_path") val imagePath: String? = null,
    val active: Int = 1,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(tableName = "inventory_movements")
data class InventoryMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "product_id") val productId: Long,
    val ts: String,
    @ColumnInfo(name = "movement_type") val movementType: String,
    val quantity: Double,
    @ColumnInfo(name = "unit_cost") val unitCost: Double = 0.0,
    @ColumnInfo(name = "balance_qty") val balanceQty: Double,
    @ColumnInfo(name = "balance_value") val balanceValue: Double,
    @ColumnInfo(name = "source_type") val sourceType: String? = null,
    @ColumnInfo(name = "source_id") val sourceId: Long? = null,
    val reason: String? = null
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name_ar") val nameAr: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    @ColumnInfo(name = "opening_balance") val openingBalance: Double = 0.0,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name_ar") val nameAr: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    @ColumnInfo(name = "opening_balance") val openingBalance: Double = 0.0,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    @ColumnInfo(name = "name_ar") val nameAr: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "group_name") val groupName: String,
    val type: String, // asset, liability, equity, income, expense
    @ColumnInfo(name = "normal_balance") val normalBalance: String, // debit/credit
    val active: Int = 1
)

@Entity(tableName = "cash_accounts")
data class CashAccount(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name_ar") val nameAr: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "opening_balance") val openingBalance: Double = 0.0,
    val active: Int = 1
)

@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val reference: String,
    val description: String,
    @ColumnInfo(name = "source_type") val sourceType: String? = null,
    @ColumnInfo(name = "source_id") val sourceId: Long? = null,
    @ColumnInfo(name = "reversed_by") val reversedBy: Long? = null,
    @ColumnInfo(name = "created_by") val createdBy: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(tableName = "journal_lines")
data class JournalLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "journal_id") val journalId: Long,
    @ColumnInfo(name = "account_id") val accountId: Long,
    val debit: Double = 0.0,
    val credit: Double = 0.0,
    val currency: String = "YER",
    @ColumnInfo(name = "original_amount") val originalAmount: Double = 0.0,
    @ColumnInfo(name = "exchange_rate") val exchangeRate: Double = 1.0
)

@Entity(tableName = "sales_invoices")
data class SalesInvoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "invoice_no") val invoiceNo: String,
    val date: String,
    @ColumnInfo(name = "customer_id") val customerId: Long? = null,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    @ColumnInfo(name = "payment_status") val paymentStatus: String,
    val currency: String = "YER",
    @ColumnInfo(name = "exchange_rate") val exchangeRate: Double = 1.0,
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,
    val paid: Double = 0.0,
    val status: String = "confirmed",
    @ColumnInfo(name = "journal_id") val journalId: Long? = null,
    val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(tableName = "sale_items")
data class SaleItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "invoice_id") val invoiceId: Long,
    @ColumnInfo(name = "product_id") val productId: Long,
    val quantity: Double,
    @ColumnInfo(name = "unit_price") val unitPrice: Double,
    val discount: Double = 0.0,
    val cost: Double = 0.0,
    @ColumnInfo(name = "line_total") val lineTotal: Double
)

@Entity(tableName = "purchase_invoices")
data class PurchaseInvoice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "invoice_no") val invoiceNo: String,
    val date: String,
    @ColumnInfo(name = "supplier_id") val supplierId: Long? = null,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    @ColumnInfo(name = "payment_status") val paymentStatus: String,
    val currency: String = "YER",
    @ColumnInfo(name = "exchange_rate") val exchangeRate: Double = 1.0,
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,
    val paid: Double = 0.0,
    val status: String = "confirmed",
    @ColumnInfo(name = "journal_id") val journalId: Long? = null,
    val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(tableName = "purchase_items")
data class PurchaseItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "invoice_id") val invoiceId: Long,
    @ColumnInfo(name = "product_id") val productId: Long,
    val quantity: Double,
    @ColumnInfo(name = "unit_cost") val unitCost: Double,
    val discount: Double = 0.0,
    @ColumnInfo(name = "line_total") val lineTotal: Double
)

@Entity(tableName = "payments")
data class Payment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val direction: String, // in/out
    @ColumnInfo(name = "party_type") val partyType: String, // customer/supplier
    @ColumnInfo(name = "party_id") val partyId: Long,
    @ColumnInfo(name = "invoice_id") val invoiceId: Long? = null,
    val amount: Double,
    val currency: String = "YER",
    @ColumnInfo(name = "exchange_rate") val exchangeRate: Double = 1.0,
    val method: String,
    val reference: String? = null,
    @ColumnInfo(name = "journal_id") val journalId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(tableName = "cash_transactions")
data class CashTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    @ColumnInfo(name = "account_id") val cashAccountId: Long,
    val direction: String,
    val amount: Double,
    val description: String,
    val reference: String? = null,
    @ColumnInfo(name = "journal_id") val journalId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(tableName = "expense_categories")
data class ExpenseCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name_ar") val nameAr: String,
    @ColumnInfo(name = "name_en") val nameEn: String
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "account_id") val accountId: Long,
    val amount: Double,
    val currency: String = "YER",
    @ColumnInfo(name = "exchange_rate") val exchangeRate: Double = 1.0,
    val description: String,
    @ColumnInfo(name = "account_paid_from") val accountPaidFrom: Long? = null,
    @ColumnInfo(name = "journal_id") val journalId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(tableName = "income_categories")
data class IncomeCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name_ar") val nameAr: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "account_id") val accountId: Long
)

@Entity(tableName = "cash_closings")
data class CashClosing(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    @ColumnInfo(name = "account_id") val accountId: Long,
    val opening: Double = 0.0,
    val receipts: Double = 0.0,
    val payments: Double = 0.0,
    val closing: Double = 0.0,
    val notes: String? = null,
    @ColumnInfo(name = "created_by") val createdBy: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(tableName = "reconciliations")
data class Reconciliation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "ledger_balance") val ledgerBalance: Double = 0.0,
    @ColumnInfo(name = "statement_balance") val statementBalance: Double = 0.0,
    val difference: Double = 0.0,
    val notes: String? = null,
    @ColumnInfo(name = "created_by") val createdBy: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(tableName = "accounting_periods")
data class AccountingPeriod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date_from") val dateFrom: String,
    @ColumnInfo(name = "date_to") val dateTo: String,
    val locked: Int = 1,
    @ColumnInfo(name = "locked_by") val lockedBy: Long? = null,
    @ColumnInfo(name = "locked_at") val lockedAt: String? = null,
    val notes: String? = null
)

@Entity(tableName = "exchange_rates")
data class ExchangeRate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val currency: String,
    val rate: Double,
    @ColumnInfo(name = "effective_date") val effectiveDate: String,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: String,
    @ColumnInfo(name = "user_id") val userId: Long? = null,
    val action: String,
    val entity: String,
    @ColumnInfo(name = "entity_id") val entityId: Long? = null,
    val details: String? = null
)

@Entity(tableName = "settings")
data class Setting(
    @PrimaryKey val key: String,
    val value: String
)

// Helper views / tuples
data class AccountBalance(
    val id: Long, val code: String, val nameAr: String, val nameEn: String,
    val groupName: String, val type: String, val normalBalance: String,
    val debit: Double, val credit: Double
)

data class ProductRow(
    val id: Long, val sku: String?, val barcode: String?,
    val nameAr: String, val nameEn: String, val categoryId: Long?,
    val categoryAr: String?, val categoryEn: String?,
    val purchaseCost: Double, val sellingPrice: Double,
    val quantity: Double, val avgCost: Double, val minStock: Double, val imagePath: String?
)
