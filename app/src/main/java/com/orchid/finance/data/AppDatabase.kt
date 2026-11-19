package com.orchid.finance.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        User::class, Session::class, Category::class, Product::class, InventoryMovement::class,
        Customer::class, Supplier::class, Account::class, CashAccount::class,
        JournalEntry::class, JournalLine::class,
        SalesInvoice::class, SaleItem::class, PurchaseInvoice::class, PurchaseItem::class,
        Payment::class, CashTransaction::class, ExpenseCategory::class, Expense::class,
        IncomeCategory::class, CashClosing::class, Reconciliation::class, AccountingPeriod::class,
        ExchangeRate::class, AuditLog::class, Setting::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun sessionDao(): SessionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun movementDao(): MovementDao
    abstract fun partyDao(): PartyDao
    abstract fun accountDao(): AccountDao
    abstract fun cashAccountDao(): CashAccountDao
    abstract fun journalDao(): JournalDao
    abstract fun salesDao(): SalesDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun paymentDao(): PaymentDao
    abstract fun cashDao(): CashDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun incomeCategoryDao(): IncomeCategoryDao
    abstract fun accountingExtraDao(): AccountingExtraDao
    abstract fun auditSettingDao(): AuditSettingDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "orchid.db"
                ).addCallback(SeedCallback()).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }

    private class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // seed accounts
            val accounts = listOf(
                "1000" to "النقدية|Cash|Assets|asset|debit",
                "1010" to "البنك|Bank|Assets|asset|debit",
                "1100" to "العملاء|Accounts Receivable|Assets|asset|debit",
                "1200" to "المخزون|Inventory|Assets|asset|debit",
                "1300" to "ضريبة مدفوعة مقدماً|Input VAT|Assets|asset|debit",
                "2000" to "الموردون|Accounts Payable|Liabilities|liability|credit",
                "2100" to "الضرائب المستحقة|Taxes Payable|Liabilities|liability|credit",
                "3000" to "رأس المال|Owner's Equity|Equity|equity|credit",
                "3100" to "الأرباح المحتجزة|Retained Earnings|Equity|equity|credit",
                "4000" to "المبيعات|Sales Revenue|Revenue|income|credit",
                "4100" to "مرتجعات المبيعات|Sales Returns|Revenue|income|debit",
                "4200" to "إيرادات أخرى|Other Income|Other Income|income|credit",
                "5000" to "تكلفة المبيعات|Cost of Goods Sold|COGS|expense|debit",
                "5100" to "المشتريات|Purchases|Purchases|expense|debit",
                "5200" to "مرتجعات المشتريات|Purchase Returns|Purchases|income|credit",
                "6000" to "مصروفات تشغيلية|Operating Expenses|Expenses|expense|debit",
                "6100" to "رواتب|Salaries|Expenses|expense|debit",
                "6200" to "إيجار|Rent|Expenses|expense|debit",
                "6300" to "كهرباء وماء|Utilities|Expenses|expense|debit",
                "6400" to "تسويق|Marketing|Expenses|expense|debit",
                "9000" to "أرباح/خسائر مجمّعة|Profit / Loss Summary|Equity|equity|credit"
            )
            accounts.forEach { (code, rest) ->
                val p = rest.split("|")
                db.execSQL("INSERT OR IGNORE INTO accounts(code,name_ar,name_en,group_name,type,normal_balance,active) VALUES(?,?,?,?,?,?,1)",
                    arrayOf(code,p[0],p[1],p[2],p[3],p[4]))
            }
            val cats = listOf("أدوات تجميل|Beauty Tools","العناية بالبشرة|Skincare","العناية الشخصية|Personal Care","العطور|Perfumes","أخرى|Other")
            cats.forEach { val p = it.split("|"); db.execSQL("INSERT INTO categories(name_ar,name_en) VALUES(?,?)", arrayOf(p[0],p[1])) }
            val ex = listOf("رواتب|Salaries","إيجار|Rent","كهرباء وماء|Utilities","تسويق|Marketing","مصروفات أخرى|Other")
            ex.forEach { val p = it.split("|"); db.execSQL("INSERT INTO expense_categories(name_ar,name_en) VALUES(?,?)", arrayOf(p[0],p[1])) }
            // cash accounts and income category need account ids - fetch
            fun accId(code: String): Long {
                val c = db.query("SELECT id FROM accounts WHERE code=?", arrayOf(code))
                c.moveToFirst(); val id = c.getLong(0); c.close(); return id
            }
            val cashId = accId("1000"); val bankId = accId("1010"); val otherInc = accId("4200")
            db.execSQL("INSERT INTO cash_accounts(name_ar,name_en,account_id,opening_balance,active) VALUES(?,?,?,?,1)", arrayOf("النقدية","Cash",cashId,0.0))
            db.execSQL("INSERT INTO cash_accounts(name_ar,name_en,account_id,opening_balance,active) VALUES(?,?,?,?,1)", arrayOf("البنك","Bank",bankId,0.0))
            db.execSQL("INSERT INTO income_categories(name_ar,name_en,account_id) VALUES(?,?,?)", arrayOf("إيرادات أخرى","Other Income",otherInc))
            db.execSQL("INSERT OR REPLACE INTO settings(key,value) VALUES(?,?)", arrayOf("base_currency","YER"))
            db.execSQL("INSERT OR REPLACE INTO settings(key,value) VALUES(?,?)", arrayOf("tax_rate","0"))
        }
    }
}
