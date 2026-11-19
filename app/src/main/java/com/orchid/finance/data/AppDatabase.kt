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
                Triple("1000","النقدية|Cash|Assets|asset|debit"),
                Triple("1010","البنك|Bank|Assets|asset|debit"),
                Triple("1100","العملاء|Accounts Receivable|Assets|asset|debit"),
                Triple("1200","المخزون|Inventory|Assets|asset|debit"),
                Triple("1300","ضريبة مدفوعة مقدماً|Input VAT|Assets|asset|debit"),
                Triple("2000","الموردون|Accounts Payable|Liabilities|liability|credit"),
                Triple("2100","الضرائب المستحقة|Taxes Payable|Liabilities|liability|credit"),
                Triple("3000","رأس المال|Owner's Equity|Equity|equity|credit"),
                Triple("3100","الأرباح المحتجزة|Retained Earnings|Equity|equity|credit"),
                Triple("4000","المبيعات|Sales Revenue|Revenue|income|credit"),
                Triple("4100","مرتجعات المبيعات|Sales Returns|Revenue|income|debit"),
                Triple("4200","إيرادات أخرى|Other Income|Other Income|income|credit"),
                Triple("5000","تكلفة المبيعات|Cost of Goods Sold|COGS|expense|debit"),
                Triple("5100","المشتريات|Purchases|Purchases|expense|debit"),
                Triple("5200","مرتجعات المشتريات|Purchase Returns|Purchases|income|credit"),
                Triple("6000","مصروفات تشغيلية|Operating Expenses|Expenses|expense|debit"),
                Triple("6100","رواتب|Salaries|Expenses|expense|debit"),
                Triple("6200","إيجار|Rent|Expenses|expense|debit"),
                Triple("6300","كهرباء وماء|Utilities|Expenses|expense|debit"),
                Triple("6400","تسويق|Marketing|Expenses|expense|debit"),
                Triple("9000","أرباح/خسائر مجمّعة|Profit / Loss Summary|Equity|equity|credit")
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
