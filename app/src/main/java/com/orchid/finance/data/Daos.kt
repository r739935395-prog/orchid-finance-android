package com.orchid.finance.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT COUNT(*) FROM users") suspend fun count(): Int
    @Query("SELECT * FROM users WHERE username = :username AND active = 1 LIMIT 1") suspend fun findByUsername(username: String): User?
    @Query("SELECT * FROM users WHERE id = :id") suspend fun findById(id: Long): User?
    @Query("SELECT * FROM users ORDER BY id") fun allFlow(): Flow<List<User>>
    @Insert suspend fun insert(user: User): Long
    @Update suspend fun update(user: User)
    @Query("UPDATE users SET password_hash = :hash WHERE id = :id") suspend fun updatePassword(id: Long, hash: String)
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(s: Session)
    @Query("SELECT * FROM sessions WHERE token = :token LIMIT 1") suspend fun get(token: String): Session?
    @Query("DELETE FROM sessions WHERE token = :token") suspend fun delete(token: String)
    @Query("DELETE FROM sessions WHERE expires_at <= :now") suspend fun cleanup(now: String)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id") fun allFlow(): Flow<List<Category>>
    @Query("SELECT * FROM categories ORDER BY id") suspend fun all(): List<Category>
    @Insert suspend fun insert(c: Category): Long
}

@Dao
interface ProductDao {
    @Query("""
        SELECT p.id, p.sku, p.barcode, p.name_ar as nameAr, p.name_en as nameEn, p.category_id as categoryId,
        c.name_ar as categoryAr, c.name_en as categoryEn, p.purchase_cost as purchaseCost, p.selling_price as sellingPrice,
        p.quantity, p.avg_cost as avgCost, p.min_stock as minStock, p.image_path as imagePath
        FROM products p LEFT JOIN categories c ON c.id = p.category_id
        WHERE p.active = 1
        AND (:search IS NULL OR :search = '' OR p.name_ar LIKE '%'||:search||'%' OR p.name_en LIKE '%'||:search||'%' OR p.sku LIKE '%'||:search||'%' OR p.barcode LIKE '%'||:search||'%')
        AND (:lowOnly = 0 OR p.quantity <= p.min_stock)
        ORDER BY p.id DESC
    """) suspend fun list(search: String?, lowOnly: Int): List<ProductRow>
    @Query("SELECT * FROM products WHERE id = :id") suspend fun find(id: Long): Product?
    @Insert suspend fun insert(p: Product): Long
    @Update suspend fun update(p: Product)
    @Query("UPDATE products SET quantity = :qty, avg_cost = :avg, purchase_cost = :purchaseCost WHERE id = :id")
    suspend fun updateStock(id: Long, qty: Double, avg: Double, purchaseCost: Double)
    @Query("UPDATE products SET quantity = :qty WHERE id = :id") suspend fun setQuantity(id: Long, qty: Double)
    @Query("UPDATE products SET image_path = :path WHERE id = :id") suspend fun setImage(id: Long, path: String?)
    @Query("SELECT COALESCE(SUM(quantity * avg_cost),0) FROM products WHERE active=1") suspend fun inventoryValue(): Double
    @Query("SELECT * FROM products WHERE active=1 AND quantity <= min_stock ORDER BY quantity ASC") suspend fun lowStock(): List<Product>
    @Query("SELECT * FROM products WHERE active=1 ORDER BY id DESC") suspend fun allActive(): List<Product>
}

@Dao
interface MovementDao {
    @Insert suspend fun insert(m: InventoryMovement): Long
    @Query("SELECT * FROM inventory_movements WHERE product_id = :pid ORDER BY ts DESC, id DESC LIMIT :limit") suspend fun forProduct(pid: Long, limit: Int = 500): List<InventoryMovement>
    @Query("SELECT * FROM inventory_movements ORDER BY ts DESC, id DESC LIMIT :limit") suspend fun all(limit: Int = 1000): List<InventoryMovement>
}

@Dao
interface PartyDao {
    @Query("SELECT * FROM customers ORDER BY id DESC") suspend fun customers(): List<Customer>
    @Query("SELECT * FROM suppliers ORDER BY id DESC") suspend fun suppliers(): List<Supplier>
    @Query("SELECT * FROM customers WHERE id = :id") suspend fun customer(id: Long): Customer?
    @Query("SELECT * FROM suppliers WHERE id = :id") suspend fun supplier(id: Long): Supplier?
    @Insert suspend fun insertCustomer(c: Customer): Long
    @Insert suspend fun insertSupplier(s: Supplier): Long
    @Update suspend fun updateCustomer(c: Customer)
    @Update suspend fun updateSupplier(s: Supplier)
    @Delete suspend fun deleteCustomer(c: Customer)
    @Delete suspend fun deleteSupplier(s: Supplier)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY code") suspend fun all(): List<Account>
    @Query("SELECT * FROM accounts WHERE active=1 ORDER BY code") fun activeFlow(): Flow<List<Account>>
    @Query("SELECT * FROM accounts WHERE code = :code LIMIT 1") suspend fun byCode(code: String): Account?
    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1") suspend fun byId(id: Long): Account?
    @Insert suspend fun insert(a: Account): Long
    @Query("SELECT COUNT(*) FROM accounts") suspend fun count(): Int
}

@Dao
interface CashAccountDao {
    @Query("SELECT * FROM cash_accounts WHERE active=1 ORDER BY id") suspend fun all(): List<CashAccount>
    @Query("SELECT * FROM cash_accounts WHERE id = :id") suspend fun find(id: Long): CashAccount?
    @Insert suspend fun insert(c: CashAccount): Long
}

@Dao
interface JournalDao {
    @Insert suspend fun insertEntry(e: JournalEntry): Long
    @Insert suspend fun insertLine(l: JournalLine): Long
    @Insert suspend fun insertLines(lines: List<JournalLine>)
    @Query("SELECT * FROM journal_entries WHERE id = :id") suspend fun entry(id: Long): JournalEntry?
    @Query("SELECT * FROM journal_lines WHERE journal_id = :jid") suspend fun lines(jid: Long): List<JournalLine>
    @Query("UPDATE journal_entries SET reversed_by = :revId WHERE id = :id") suspend fun markReversed(id: Long, revId: Long)
    @Query("SELECT * FROM journal_entries WHERE (:from IS NULL OR date >= :from) AND (:to IS NULL OR date <= :to) ORDER BY date DESC, id DESC LIMIT :limit")
    suspend fun list(from: String?, to: String?, limit: Int = 300): List<JournalEntry>
    @Query("SELECT COALESCE(SUM(debit),0) as debit, COALESCE(SUM(credit),0) as credit FROM journal_lines WHERE journal_id = :jid")
    suspend fun totals(jid: Long): DebitCredit
    @Query("SELECT a.id, a.code, a.name_ar as nameAr, a.name_en as nameEn, a.group_name as groupName, a.type, a.normal_balance as normalBalance, COALESCE(SUM(l.debit),0) as debit, COALESCE(SUM(l.credit),0) as credit FROM accounts a LEFT JOIN journal_lines l ON l.account_id=a.id LEFT JOIN journal_entries j ON j.id=l.journal_id WHERE (:from IS NULL OR j.date >= :from) AND (:to IS NULL OR j.date <= :to) GROUP BY a.id ORDER BY a.code")
    suspend fun balances(from: String?, to: String?): List<AccountBalance>
    @Query("SELECT COALESCE(SUM(l.debit - l.credit),0) FROM journal_lines l JOIN journal_entries j ON j.id=l.journal_id WHERE l.account_id=:accountId AND (:from IS NULL OR j.date >= :from) AND (:to IS NULL OR j.date <= :to)")
    suspend fun netMovement(accountId: Long, from: String?, to: String?): Double
    @Query("SELECT j.date, j.reference, j.description, l.debit, l.credit FROM journal_lines l JOIN journal_entries j ON j.id=l.journal_id WHERE l.account_id=:accountId AND (:from IS NULL OR j.date >= :from) AND (:to IS NULL OR j.date <= :to) ORDER BY j.date, j.id, l.id")
    suspend fun ledger(accountId: Long, from: String?, to: String?): List<LedgerRow>
    @Query("SELECT l.debit, l.credit FROM journal_lines l JOIN journal_entries j ON j.id=l.journal_id WHERE l.account_id=:accountId AND j.date=:date")
    suspend fun linesForAccountOnDate(accountId: Long, date: String): List<DebitCredit>
}


data class DebitCredit(val debit: Double, val credit: Double)
data class LedgerRow(val date: String, val reference: String, val description: String, val debit: Double, val credit: Double)

@Dao
interface SalesDao {
    @Insert suspend fun insertInvoice(i: SalesInvoice): Long
    @Insert suspend fun insertItem(it: SaleItem): Long
    @Update suspend fun updateInvoice(i: SalesInvoice)
    @Query("SELECT * FROM sales_invoices WHERE (:from IS NULL OR date >= :from) AND (:to IS NULL OR date <= :to) AND (:status IS NULL OR status = :status) ORDER BY date DESC, id DESC LIMIT :limit")
    suspend fun list(from: String?, to: String?, status: String?, limit: Int = 300): List<SalesInvoice>
    @Query("SELECT * FROM sales_invoices WHERE id = :id") suspend fun find(id: Long): SalesInvoice?
    @Query("SELECT * FROM sale_items WHERE invoice_id = :id") suspend fun items(id: Long): List<SaleItem>
    @Query("SELECT * FROM sales_invoices WHERE customer_id = :cid AND status='confirmed'") suspend fun forCustomer(cid: Long): List<SalesInvoice>
}

@Dao
interface PurchaseDao {
    @Insert suspend fun insertInvoice(i: PurchaseInvoice): Long
    @Insert suspend fun insertItem(it: PurchaseItem): Long
    @Update suspend fun updateInvoice(i: PurchaseInvoice)
    @Query("SELECT * FROM purchase_invoices WHERE (:from IS NULL OR date >= :from) AND (:to IS NULL OR date <= :to) AND (:status IS NULL OR status = :status) ORDER BY date DESC, id DESC LIMIT :limit")
    suspend fun list(from: String?, to: String?, status: String?, limit: Int = 300): List<PurchaseInvoice>
    @Query("SELECT * FROM purchase_invoices WHERE id = :id") suspend fun find(id: Long): PurchaseInvoice?
    @Query("SELECT * FROM purchase_items WHERE invoice_id = :id") suspend fun items(id: Long): List<PurchaseItem>
    @Query("SELECT * FROM purchase_invoices WHERE supplier_id = :sid AND status='confirmed'") suspend fun forSupplier(sid: Long): List<PurchaseInvoice>
}

@Dao
interface PaymentDao {
    @Insert suspend fun insert(p: Payment): Long
    @Query("SELECT * FROM payments ORDER BY date DESC, id DESC LIMIT :limit") suspend fun list(limit: Int = 300): List<Payment>
    @Query("SELECT COALESCE(SUM(amount),0) FROM payments WHERE party_type='customer' AND party_id=:id AND direction='in'") suspend fun receivedFromCustomer(id: Long): Double
    @Query("SELECT COALESCE(SUM(amount),0) FROM payments WHERE party_type='supplier' AND party_id=:id AND direction='out'") suspend fun paidToSupplier(id: Long): Double
}

@Dao
interface CashDao {
    @Insert suspend fun insert(tx: CashTransaction): Long
    @Query("SELECT * FROM cash_transactions WHERE (:acc IS NULL OR account_id=:acc) AND (:from IS NULL OR date>=:from) AND (:to IS NULL OR date<=:to) ORDER BY date DESC,id DESC LIMIT :limit")
    suspend fun list(acc: Long?, from: String?, to: String?, limit: Int = 300): List<CashTransaction>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertClosing(c: CashClosing): Long
    @Query("SELECT * FROM cash_closings WHERE (:from IS NULL OR date>=:from) AND (:to IS NULL OR date<=:to) ORDER BY date DESC,id DESC LIMIT 200")
    suspend fun closings(from: String?, to: String?): List<CashClosing>
    @Query("SELECT * FROM cash_closings WHERE date=:date AND account_id=:acc LIMIT 1") suspend fun closing(date: String, acc: Long): CashClosing?
}

@Dao
interface ExpenseDao {
    @Insert suspend fun insert(e: Expense): Long
    @Query("SELECT * FROM expenses WHERE (:from IS NULL OR date>=:from) AND (:to IS NULL OR date<=:to) ORDER BY date DESC,id DESC LIMIT :limit")
    suspend fun list(from: String?, to: String?, limit: Int = 300): List<Expense>
    @Query("SELECT * FROM expenses WHERE id=:id") suspend fun find(id: Long): Expense?
    @Delete suspend fun delete(e: Expense)
    @Query("SELECT * FROM expense_categories ORDER BY id") suspend fun categories(): List<ExpenseCategory>
    @Insert suspend fun insertCategory(c: ExpenseCategory): Long
}

@Dao
interface IncomeCategoryDao {
    @Query("SELECT * FROM income_categories ORDER BY id") suspend fun all(): List<IncomeCategory>
    @Insert suspend fun insert(c: IncomeCategory): Long
}

@Dao
interface AccountingExtraDao {
    @Insert suspend fun insertReconciliation(r: Reconciliation): Long
    @Query("SELECT * FROM reconciliations ORDER BY date DESC,id DESC LIMIT 200") suspend fun reconciliations(): List<Reconciliation>
    @Insert suspend fun insertPeriod(p: AccountingPeriod): Long
    @Query("SELECT * FROM accounting_periods ORDER BY date_from,id") suspend fun periods(): List<AccountingPeriod>
    @Delete suspend fun deletePeriod(p: AccountingPeriod)
    @Query("SELECT * FROM accounting_periods WHERE id=:id") suspend fun period(id: Long): AccountingPeriod?
    @Query("SELECT * FROM accounting_periods WHERE locked=1 AND date_from <= :date AND date_to >= :date LIMIT 1") suspend fun lockedCovering(date: String): AccountingPeriod?
    @Insert suspend fun insertRate(r: ExchangeRate): Long
    @Query("SELECT * FROM exchange_rates ORDER BY effective_date DESC,id DESC") suspend fun rates(): List<ExchangeRate>
    @Query("SELECT * FROM exchange_rates WHERE currency=:currency AND effective_date <= :date ORDER BY effective_date DESC,id DESC LIMIT 1") suspend fun rateFor(currency: String, date: String): ExchangeRate?
}

@Dao
interface AuditSettingDao {
    @Insert suspend fun audit(a: AuditLog): Long
    @Query("SELECT * FROM audit_logs ORDER BY id DESC LIMIT 300") suspend fun audits(): List<AuditLog>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun set(s: Setting)
    @Query("SELECT * FROM settings") suspend fun settings(): List<Setting>
    @Query("SELECT value FROM settings WHERE key=:key") suspend fun get(key: String): String?
}
