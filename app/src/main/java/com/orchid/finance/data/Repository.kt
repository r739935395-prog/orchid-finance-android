package com.orchid.finance.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

class Repository(val db: AppDatabase) {
    val users get() = db.userDao()
    val sessions get() = db.sessionDao()
    val categories get() = db.categoryDao()
    val products get() = db.productDao()
    val movements get() = db.movementDao()
    val parties get() = db.partyDao()
    val accounts get() = db.accountDao()
    val cashAccounts get() = db.cashAccountDao()
    val journals get() = db.journalDao()
    val sales get() = db.salesDao()
    val purchases get() = db.purchaseDao()
    val payments get() = db.paymentDao()
    val cash get() = db.cashDao()
    val expenses get() = db.expenseDao()
    val incomeCategories get() = db.incomeCategoryDao()
    val extra get() = db.accountingExtraDao()
    val auditSettings get() = db.auditSettingDao()

    val engine = AccountingEngine(db)
    val inv = InventoryService(db)

    suspend fun <T> tx(block: suspend () -> T): T = db.withTransaction { block() }

    suspend fun audit(userId: Long?, action: String, entity: String, entityId: Long? = null, details: String? = null) {
        auditSettings.audit(AuditLog(ts = Dates.nowIso(), userId = userId, action = action, entity = entity, entityId = entityId, details = details))
    }

    suspend fun getSetting(key: String, default: String? = null): String? = auditSettings.get(key) ?: default
    suspend fun setSetting(key: String, value: String) = auditSettings.set(Setting(key, value))

    fun productsFlow(): Flow<List<Account>> = accounts.activeFlow() // placeholder not used
    suspend fun categoryList() = categories.all()

    suspend fun taxRate(): Double = getSetting("tax_rate", "0")?.toDoubleOrNull() ?: 0.0

    suspend fun amountInBase(original: Double, currency: String, rate: Double): Double {
        if (currency == "YER") return Money.m(original)
        require(rate > 0) { "A valid exchange rate is required for foreign currency" }
        return Money.m(original * rate)
    }

    suspend fun resolveExchangeRate(currency: String, date: String): Double {
        if (currency == "YER") return 1.0
        return extra.rateFor(currency, date)?.rate ?: error("No exchange rate configured for $currency on $date")
    }

    suspend fun nextNumber(table: String, prefix: String): String {
        val year = Dates.today().substring(0, 4)
        val p = "$prefix-$year-"
        val max = when (table) {
            "sales" -> sales.list(null, null, null, 10000).mapNotNull { it.invoiceNo.takeIf { n -> n.startsWith(p) }?.substringAfterLast('-')?.toIntOrNull() }.maxOrNull() ?: 0
            else -> purchases.list(null, null, null, 10000).mapNotNull { it.invoiceNo.takeIf { n -> n.startsWith(p) }?.substringAfterLast('-')?.toIntOrNull() }.maxOrNull() ?: 0
        }
        return "$p${(max + 1).toString().padStart(5, '0')}"
    }

    // --- Auth ---
    suspend fun userCount() = users.count()
    suspend fun createUser(username: String, password: String, fullName: String, role: String): Long {
        val u = username.trim().lowercase()
        require(u.isNotBlank() && password.isNotBlank()) { "Username and password are required" }
        require(users.findByUsername(u) == null) { "Username already exists" }
        val id = users.insert(User(username = u, fullName = fullName, passwordHash = Passwords.hash(password), role = role, createdAt = Dates.nowIso()))
        audit(id, "create", "users", id, "Created user $u")
        return id
    }
    suspend fun validateCredentials(username: String, password: String): User? {
        val u = users.findByUsername(username.trim().lowercase()) ?: return null
        return if (Passwords.verify(password, u.passwordHash)) u else null
    }
    suspend fun createSession(userId: Long): String {
        val token = java.util.UUID.randomUUID().toString().replace("-", "") + java.util.UUID.randomUUID().toString().replace("-", "")
        val now = java.util.Calendar.getInstance()
        val created = Dates.nowIso()
        now.add(java.util.Calendar.HOUR_OF_DAY, 12)
        val exp = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(now.time)
        sessions.insert(Session(token, userId, created, exp))
        return token
    }
    suspend fun getSessionUser(token: String?): User? {
        if (token.isNullOrBlank()) return null
        val s = sessions.get(token) ?: return null
        if (s.expiresAt <= Dates.nowIso()) return null
        val u = users.findById(s.userId) ?: return null
        return if (u.active == 1) u else null
    }
    suspend fun deleteSession(token: String?) { if (!token.isNullOrBlank()) sessions.delete(token) }

    // --- Products ---
    suspend fun addProduct(data: ProductInput, userId: Long?): Long = tx {
        require(data.nameAr.isNotBlank() && data.nameEn.isNotBlank()) { "Name required" }
        val pid = products.insert(Product(
            sku = data.sku?.ifBlank { null }, barcode = data.barcode?.ifBlank { null },
            nameAr = data.nameAr.trim(), nameEn = data.nameEn.trim(), categoryId = data.categoryId,
            purchaseCost = data.purchaseCost, sellingPrice = data.sellingPrice,
            quantity = 0.0, avgCost = 0.0, minStock = data.minStock, imagePath = data.imagePath,
            createdAt = Dates.nowIso()
        ))
        if (data.quantity > 0) {
            inv.receive(pid, data.quantity, data.purchaseCost, Dates.nowTs(), "opening", "product", pid, "Opening stock")
            engine.postJournal(Dates.today(), "Opening stock: ${data.nameEn}",
                listOf(JournalLineInput("1200", debit = Money.m(data.quantity * data.purchaseCost)),
                       JournalLineInput("3000", credit = Money.m(data.quantity * data.purchaseCost))),
                sourceType = "product", sourceId = pid, userId = userId)
        }
        audit(userId, "create", "products", pid, data.nameEn)
        pid
    }

    suspend fun updateProduct(id: Long, data: ProductInput, userId: Long?) = tx {
        val old = products.find(id) ?: error("Product not found")
        products.update(old.copy(sku = data.sku?.ifBlank { null }, barcode = data.barcode?.ifBlank { null },
            nameAr = data.nameAr.trim(), nameEn = data.nameEn.trim(), categoryId = data.categoryId,
            purchaseCost = data.purchaseCost, sellingPrice = data.sellingPrice, minStock = data.minStock,
            imagePath = data.imagePath ?: old.imagePath))
        audit(userId, "update", "products", id, data.nameEn)
    }

    suspend fun adjustProductStock(productId: Long, newQty: Double, unitCost: Double?, reason: String, userId: Long?): Double = tx {
        val (delta, costDelta) = inv.adjust(productId, newQty, unitCost, reason, Dates.nowTs(), "adjustment", productId)
        if (delta == 0.0) return@tx 0.0
        if (abs(costDelta) > 0.0001) {
            val lines = if (delta > 0) listOf(JournalLineInput("1200", debit = Money.m(costDelta)), JournalLineInput("3000", credit = Money.m(costDelta)))
                        else listOf(JournalLineInput("3000", debit = Money.m(-costDelta)), JournalLineInput("1200", credit = Money.m(-costDelta)))
            engine.postJournal(Dates.today(), "Stock adjustment: $reason", lines, sourceType = "adjustment", sourceId = productId, userId = userId)
        }
        audit(userId, "adjust", "products", productId, "$reason: $delta")
        delta
    }

    // --- Sales ---
    suspend fun createSale(input: SaleInput, userId: Long?): Long = tx {
        require(input.items.isNotEmpty()) { "Invoice must contain at least one item" }
        val dateStr = input.date.ifBlank { Dates.today() }
        val rate = if (input.currency == "YER") 1.0 else input.exchangeRate
        val discount = Money.m(input.discount)
        val tax = Money.m(input.tax)
        var subtotal = 0.0; var totalCost = 0.0
        val prepared = mutableListOf<PreparedSaleItem>()
        for (item in input.items) {
            val price = amountInBase(item.unitPrice, input.currency, rate)
            val lineDiscount = Money.m(item.discount)
            val lineTotal = Money.m(item.quantity * price - lineDiscount)
            require(item.quantity > 0 && lineTotal >= 0) { "Invalid invoice line" }
            val cost = inv.issue(item.productId, item.quantity, "$dateStr ${Dates.nowTs().takeLast(8)}", "sale", reason = "Sales invoice")
            prepared.add(PreparedSaleItem(item.productId, item.quantity, price, lineDiscount, cost, lineTotal))
            subtotal += lineTotal; totalCost += cost
        }
        subtotal = Money.m(subtotal)
        val total = Money.m((subtotal - discount + tax).coerceAtLeast(0.0))
        val paid = Money.m(input.paid.coerceAtMost(total))
        val receivable = Money.m(total - paid)
        val payStatus = if (paid >= total) "paid" else if (paid > 0) "partial" else "credit"
        val cashCode = if (input.paymentMethod == "bank") "1010" else "1000"
        val lines = mutableListOf<JournalLineInput>()
        if (paid > 0) lines.add(JournalLineInput(cashCode, debit = paid, currency = input.currency, originalAmount = Money.m(paid / rate), exchangeRate = rate))
        if (receivable > 0) lines.add(JournalLineInput("1100", debit = receivable, currency = input.currency, originalAmount = Money.m(receivable / rate), exchangeRate = rate))
        lines.add(JournalLineInput("4000", credit = Money.m(subtotal - discount), currency = input.currency, originalAmount = Money.m((subtotal - discount) / rate), exchangeRate = rate))
        if (tax > 0) lines.add(JournalLineInput("2100", credit = tax, currency = input.currency, originalAmount = Money.m(tax / rate), exchangeRate = rate))
        if (totalCost > 0) { lines.add(JournalLineInput("5000", debit = Money.m(totalCost))); lines.add(JournalLineInput("1200", credit = Money.m(totalCost))) }
        val invNo = input.invoiceNo.ifBlank { nextNumber("sales", "SAL") }
        val journalId = engine.postJournal(dateStr, "Sales invoice $invNo", lines, sourceType = "sale", userId = userId)
        val invoiceId = sales.insertInvoice(SalesInvoice(invoiceNo = invNo, date = dateStr, customerId = input.customerId,
            paymentMethod = input.paymentMethod, paymentStatus = payStatus, currency = input.currency, exchangeRate = rate,
            subtotal = subtotal, discount = discount, tax = tax, total = total, paid = paid, status = "confirmed",
            journalId = journalId, notes = input.notes, createdAt = Dates.nowIso()))
        prepared.forEach { it2 -> sales.insertItem(SaleItem(invoiceId = invoiceId, productId = it2.productId, quantity = it2.quantity, unitPrice = it2.unitPrice, discount = it2.discount, cost = it2.cost, lineTotal = it2.lineTotal)) }
        audit(userId, "create", "sales_invoices", invoiceId, "$invNo total=$total")
        invoiceId
    }

    suspend fun cancelSale(invoiceId: Long, reason: String, userId: Long?) = tx {
        val sale = sales.find(invoiceId) ?: error("Invoice not found")
        require(sale.status == "confirmed") { "Only confirmed invoices can be cancelled" }
        // check for returns by movement source
        val retMoves = movements.all(5000).filter { it.sourceType == "sale-return" && it.sourceId == invoiceId }
        require(retMoves.isEmpty()) { "Invoice has recorded returns; it cannot be cancelled. Reverse the return first." }
        sale.journalId?.let { engine.reverseJournal(it, Dates.today(), userId, reason) }
        sales.items(invoiceId).forEach { item ->
            val qty = item.quantity
            val unitCost = if (qty != 0.0) Money.m(item.cost / qty) else 0.0
            inv.receive(item.productId, qty, unitCost, Dates.nowTs(), "sale-return", "sale", invoiceId, "Cancel ${sale.invoiceNo}")
        }
        sales.updateInvoice(sale.copy(status = "cancelled"))
        audit(userId, "cancel", "sales_invoices", invoiceId, reason)
    }

    // --- Purchases ---
    suspend fun createPurchase(input: PurchaseInput, userId: Long?): Long = tx {
        require(input.items.isNotEmpty()) { "Invoice must contain at least one item" }
        val dateStr = input.date.ifBlank { Dates.today() }
        val rate = if (input.currency == "YER") 1.0 else input.exchangeRate
        val discount = Money.m(input.discount); val tax = Money.m(input.tax)
        var subtotal = 0.0
        val prepared = mutableListOf<PreparedPurchaseItem>()
        for (item in input.items) {
            val cost = amountInBase(item.unitCost, input.currency, rate)
            val lineDiscount = Money.m(item.discount)
            val lineTotal = Money.m(item.quantity * cost - lineDiscount)
            require(item.quantity > 0 && lineTotal >= 0) { "Invalid invoice line" }
            inv.receive(item.productId, item.quantity, cost, Dates.nowTs(), "purchase", reason = "Purchase invoice")
            prepared.add(PreparedPurchaseItem(item.productId, item.quantity, cost, lineDiscount, lineTotal))
            subtotal += lineTotal
        }
        subtotal = Money.m(subtotal)
        val total = Money.m((subtotal - discount + tax).coerceAtLeast(0.0))
        val paid = Money.m(input.paid.coerceAtMost(total))
        val payable = Money.m(total - paid)
        val payStatus = if (paid >= total) "paid" else if (paid > 0) "partial" else "credit"
        val cashCode = if (input.paymentMethod == "bank") "1010" else "1000"
        val net = Money.m(subtotal - discount)
        val lines = mutableListOf<JournalLineInput>()
        if (net > 0) lines.add(JournalLineInput("1200", debit = net, currency = input.currency, originalAmount = Money.m(net / rate), exchangeRate = rate))
        if (tax > 0) lines.add(JournalLineInput("1300", debit = tax, currency = input.currency, originalAmount = Money.m(tax / rate), exchangeRate = rate))
        if (paid > 0) lines.add(JournalLineInput(cashCode, credit = paid, currency = input.currency, originalAmount = Money.m(paid / rate), exchangeRate = rate))
        if (payable > 0) lines.add(JournalLineInput("2000", credit = payable, currency = input.currency, originalAmount = Money.m(payable / rate), exchangeRate = rate))
        val invNo = input.invoiceNo.ifBlank { nextNumber("purchases", "PUR") }
        val journalId = engine.postJournal(dateStr, "Purchase invoice $invNo", lines, sourceType = "purchase", userId = userId)
        val invoiceId = purchases.insertInvoice(PurchaseInvoice(invoiceNo = invNo, date = dateStr, supplierId = input.supplierId,
            paymentMethod = input.paymentMethod, paymentStatus = payStatus, currency = input.currency, exchangeRate = rate,
            subtotal = subtotal, discount = discount, tax = tax, total = total, paid = paid, status = "confirmed",
            journalId = journalId, notes = input.notes, createdAt = Dates.nowIso()))
        prepared.forEach { purchases.insertItem(PurchaseItem(invoiceId = invoiceId, productId = it.productId, quantity = it.quantity, unitCost = it.unitCost, discount = it.discount, lineTotal = it.lineTotal)) }
        audit(userId, "create", "purchase_invoices", invoiceId, "$invNo total=$total")
        invoiceId
    }

    suspend fun cancelPurchase(invoiceId: Long, reason: String, userId: Long?) = tx {
        val pur = purchases.find(invoiceId) ?: error("Purchase invoice not found")
        require(pur.status == "confirmed") { "Confirmed purchase invoice not found" }
        val retMoves = movements.all(5000).filter { it.sourceType == "purchase-return" && it.sourceId == invoiceId }
        require(retMoves.isEmpty()) { "Invoice has recorded returns; it cannot be cancelled." }
        pur.journalId?.let { engine.reverseJournal(it, Dates.today(), userId, reason) }
        purchases.items(invoiceId).forEach { inv.issue(it.productId, it.quantity, Dates.nowTs(), "purchase-return", "purchase", invoiceId, "Cancel ${pur.invoiceNo}") }
        purchases.updateInvoice(pur.copy(status = "cancelled"))
        audit(userId, "cancel", "purchase_invoices", invoiceId, reason)
    }

    // --- Expenses ---
    suspend fun addExpense(input: ExpenseInput, userId: Long?): Long = tx {
        require(input.amount > 0) { "Amount must be positive" }
        val dateStr = input.date.ifBlank { Dates.today() }
        val expAcc = if (input.accountId != null) accounts.byId(input.accountId) ?: error("Account not found") else accounts.byCode("6000")!!
        val creditAccId = if (input.paidFrom != null) (cashAccounts.find(input.paidFrom)?.accountId ?: accounts.byCode("1000")!!.id) else accounts.byCode("1000")!!.id
        val journalId = engine.postJournal(dateStr, input.description.ifBlank { "Expense" },
            listOf(JournalLineInput(expAcc.id, debit = Money.m(input.amount)), JournalLineInput(creditAccId, credit = Money.m(input.amount))),
            sourceType = "expense", userId = userId)
        val eid = expenses.insert(Expense(date = dateStr, categoryId = input.categoryId, accountId = expAcc.id, amount = Money.m(input.amount),
            currency = input.currency, exchangeRate = input.exchangeRate, description = input.description, accountPaidFrom = input.paidFrom, journalId = journalId, createdAt = Dates.nowIso()))
        audit(userId, "create", "expenses", eid, "${input.amount}")
        eid
    }

    suspend fun deleteExpense(id: Long, userId: Long?) = tx {
        val e = expenses.find(id) ?: error("Expense not found")
        e.journalId?.let { engine.reverseJournal(it, Dates.today(), userId, "Expense deleted") }
        expenses.delete(e)
        audit(userId, "delete", "expenses", id)
    }

    // --- Cash ---
    suspend fun cashTransaction(input: CashTxInput, userId: Long?): Long = tx {
        val acc = cashAccounts.find(input.accountId) ?: error("Cash account not found")
        require(input.amount > 0) { "Amount must be positive" }
        val dateStr = input.date.ifBlank { Dates.today() }
        val lines = when (input.direction) {
            "in" -> {
                val incAccId = if (input.incomeCategoryId != null) (incomeCategories.all().find { it.id == input.incomeCategoryId }?.accountId ?: accounts.byCode("4200")!!.id) else accounts.byCode("4200")!!.id
                listOf(JournalLineInput(acc.accountId, debit = Money.m(input.amount)), JournalLineInput(incAccId, credit = Money.m(input.amount)))
            }
            "out" -> {
                val expAcc = input.expenseAccountId ?: accounts.byCode("6000")!!.id
                listOf(JournalLineInput(expAcc, debit = Money.m(input.amount)), JournalLineInput(acc.accountId, credit = Money.m(input.amount)))
            }
            "transfer" -> {
                val target = cashAccounts.find(input.toAccountId ?: 0) ?: error("Invalid transfer target")
                require(target.id != acc.id) { "Invalid transfer target" }
                listOf(JournalLineInput(target.accountId, debit = Money.m(input.amount)), JournalLineInput(acc.accountId, credit = Money.m(input.amount)))
            }
            else -> error("Invalid direction")
        }
        val jid = engine.postJournal(dateStr, input.description.ifBlank { "Cash transaction" }, lines, sourceType = "cash", sourceId = acc.id, userId = userId)
        val tid = cash.insert(CashTransaction(date = dateStr, cashAccountId = acc.id, direction = input.direction, amount = Money.m(input.amount),
            description = input.description, reference = input.reference, journalId = jid, createdAt = Dates.nowIso()))
        audit(userId, "create", "cash_transactions", tid, "${input.direction} ${input.amount}")
        tid
    }

    // --- Manual journal ---
    suspend fun manualJournal(date: String, description: String, lines: List<JournalLineInput>, userId: Long?): Long = tx {
        engine.postJournal(date.ifBlank { Dates.today() }, description.ifBlank { "Manual journal" }, lines, userId = userId)
    }

    // --- Reconciliation & periods ---
    suspend fun reconcile(accountId: Long, date: String, statementBalance: Double, notes: String, userId: Long?): Reconciliation = tx {
        val acc = accounts.byId(accountId) ?: error("Account not found")
        val ledger = engine.normalBalance(accountId, null, date)
        val stmt = Money.m(statementBalance)
        val diff = Money.m(stmt - ledger)
        val r = Reconciliation(date = date, accountId = accountId, ledgerBalance = ledger, statementBalance = stmt, difference = diff, notes = notes, createdBy = userId, createdAt = Dates.nowIso())
        val id = extra.insertReconciliation(r)
        audit(userId, "reconcile", "accounts", accountId, "$date ledger=$ledger statement=$stmt")
        r.copy(id = id)
    }

    suspend fun setPeriod(from: String, to: String, locked: Boolean, notes: String, userId: Long?): Long = tx {
        require(from.isNotBlank() && to.isNotBlank() && from <= to) { "A valid period range is required" }
        val p = AccountingPeriod(dateFrom = from, dateTo = to, locked = if (locked) 1 else 0, lockedBy = if (locked) userId else null, lockedAt = if (locked) Dates.nowIso() else null, notes = notes)
        val id = extra.insertPeriod(p)
        audit(userId, if (locked) "lock" else "unlock", "accounting_periods", id, "$from..$to")
        id
    }

    suspend fun deletePeriod(id: Long, userId: Long?) = tx {
        val p = extra.period(id) ?: error("Period not found")
        extra.deletePeriod(p)
        audit(userId, "unlock", "accounting_periods", id, "${p.dateFrom}..${p.dateTo}")
    }

    // --- Payments ---
    suspend fun recordPayment(input: PaymentInput, userId: Long?): Long = tx {
        require(input.direction in listOf("in","out") && input.partyType in listOf("customer","supplier")) { "Invalid payment" }
        require(input.amount > 0) { "Amount must be positive" }
        val dateStr = input.date.ifBlank { Dates.today() }
        val arCode = if (input.partyType == "customer") "1100" else "2000"
        val cashCode = if (input.method == "bank") "1010" else "1000"
        val lines = if (input.direction == "in") listOf(JournalLineInput(cashCode, debit = Money.m(input.amount)), JournalLineInput(arCode, credit = Money.m(input.amount)))
                    else listOf(JournalLineInput(arCode, debit = Money.m(input.amount)), JournalLineInput(cashCode, credit = Money.m(input.amount)))
        val jid = engine.postJournal(dateStr, "Payment ${input.direction} - ${input.partyType}", lines, sourceType = "payment", userId = userId)
        val pid = payments.insert(Payment(date = dateStr, direction = input.direction, partyType = input.partyType, partyId = input.partyId, invoiceId = input.invoiceId,
            amount = Money.m(input.amount), currency = input.currency, exchangeRate = input.exchangeRate, method = input.method, reference = input.reference, journalId = jid, createdAt = Dates.nowIso()))
        if (input.invoiceId != null) {
            if (input.partyType == "customer") {
                val inv = sales.find(input.invoiceId) ?: return@tx pid
                val newPaid = Money.m(inv.paid + input.amount)
                val status = if (newPaid >= inv.total) "paid" else "partial"
                sales.updateInvoice(inv.copy(paid = newPaid, paymentStatus = status))
            } else {
                val inv = purchases.find(input.invoiceId) ?: return@tx pid
                val newPaid = Money.m(inv.paid + input.amount)
                val status = if (newPaid >= inv.total) "paid" else "partial"
                purchases.updateInvoice(inv.copy(paid = newPaid, paymentStatus = status))
            }
        }
        audit(userId, "create", "payments", pid, "${input.direction} ${input.amount}")
        pid
    }

    // --- Parties ---
    suspend fun addParty(kind: String, input: PartyInput, userId: Long?): Long = tx {
        require(input.nameAr.isNotBlank() && input.nameEn.isNotBlank()) { "Name required" }
        val id = if (kind == "customer") parties.insertCustomer(Customer(nameAr = input.nameAr.trim(), nameEn = input.nameEn.trim(), phone = input.phone, email = input.email, address = input.address, openingBalance = input.openingBalance, createdAt = Dates.nowIso()))
                 else parties.insertSupplier(Supplier(nameAr = input.nameAr.trim(), nameEn = input.nameEn.trim(), phone = input.phone, email = input.email, address = input.address, openingBalance = input.openingBalance, createdAt = Dates.nowIso()))
        if (input.openingBalance != 0.0) {
            val arCode = if (kind == "customer") "1100" else "2000"
            val lines = if (kind == "customer") listOf(JournalLineInput(arCode, debit = Money.m(input.openingBalance)), JournalLineInput("3000", credit = Money.m(input.openingBalance)))
                        else listOf(JournalLineInput("3000", debit = Money.m(input.openingBalance)), JournalLineInput(arCode, credit = Money.m(input.openingBalance)))
            engine.postJournal(Dates.today(), "Opening balance: ${input.nameEn}", lines, sourceType = kind, sourceId = id, userId = userId)
        }
        audit(userId, "create", if (kind=="customer") "customers" else "suppliers", id, input.nameEn)
        id
    }


    // --- Returns ---
    suspend fun salesReturn(invoiceId: Long, items: List<SaleItemInput>, reason: String, userId: Long?): Double = tx {
        val sale = sales.find(invoiceId) ?: error("Invoice not found")
        require(sale.status == "confirmed") { "Confirmed invoice not found" }
        val original = sales.items(invoiceId).associateBy { it.productId }
        var refund = 0.0; var cost = 0.0
        for (item in items) {
            val src = original[item.productId] ?: error("Invalid return line")
            require(item.quantity > 0 && item.quantity <= src.quantity + 0.0001) { "Invalid return line" }
            val lineRefund = Money.m(item.quantity * src.unitPrice - (src.discount * item.quantity / src.quantity))
            refund = Money.m(refund + lineRefund)
            val unitCost = if (src.quantity != 0.0) Money.m(src.cost / src.quantity) else 0.0
            val returnedCost = Money.m(item.quantity * unitCost)
            cost = Money.m(cost + returnedCost)
            inv.receive(item.productId, item.quantity, unitCost, Dates.nowTs(), "sale-return", "sale-return", invoiceId, reason)
        }
        val ratio = if (sale.subtotal != 0.0) refund / sale.subtotal else 0.0
        val tax = Money.m(sale.tax * ratio)
        val totalRefund = Money.m(refund + tax)
        require(totalRefund > 0) { "Nothing to return" }
        val arCode = if (sale.paymentStatus != "paid") "1100" else "1000"
        val lines = listOf(
            JournalLineInput("4100", debit = Money.m(refund)),
            JournalLineInput("2100", debit = tax),
            JournalLineInput(arCode, credit = totalRefund),
            JournalLineInput("1200", debit = Money.m(cost)),
            JournalLineInput("5000", credit = Money.m(cost))
        )
        engine.postJournal(Dates.today(), "Sales return ${sale.invoiceNo}: $reason", lines, sourceType = "sale-return", sourceId = invoiceId, userId = userId)
        audit(userId, "return", "sales_invoices", invoiceId, reason)
        totalRefund
    }

    suspend fun purchaseReturn(invoiceId: Long, items: List<PurchaseItemInput>, reason: String, userId: Long?): Double = tx {
        val pur = purchases.find(invoiceId) ?: error("Purchase invoice not found")
        require(pur.status == "confirmed") { "Confirmed purchase invoice not found" }
        val original = purchases.items(invoiceId).associateBy { it.productId }
        var refund = 0.0
        for (item in items) {
            val src = original[item.productId] ?: error("Invalid return line")
            require(item.quantity > 0 && item.quantity <= src.quantity + 0.0001) { "Invalid return line" }
            inv.issue(item.productId, item.quantity, Dates.nowTs(), "purchase-return", "purchase-return", invoiceId, reason)
            refund = Money.m(refund + Money.m(item.quantity * src.unitCost))
        }
        val ratio = if (pur.subtotal != 0.0) refund / pur.subtotal else 0.0
        val tax = Money.m(pur.tax * ratio)
        val total = Money.m(refund + tax)
        val target = if (pur.paymentStatus != "paid") "2000" else "1000"
        val lines = listOf(
            JournalLineInput(target, debit = total),
            JournalLineInput("5200", credit = Money.m(refund)),
            JournalLineInput("1300", credit = tax)
        )
        engine.postJournal(Dates.today(), "Purchase return ${pur.invoiceNo}", lines, sourceType = "purchase-return", sourceId = invoiceId, userId = userId)
        audit(userId, "return", "purchase_invoices", invoiceId, reason)
        total
    }

    // --- Cash closing ---
    suspend fun cashClosing(cashAccountId: Long, date: String, notes: String, userId: Long?): CashClosing = tx {
        val ca = cashAccounts.find(cashAccountId) ?: error("Cash account not found")
        val prior = Dates.prevDay(date)
        val opening = engine.normalBalance(ca.accountId, null, prior)
        val movements = journals.linesForAccountOnDate(ca.accountId, date)
        val receipts = Money.m(movements.sumOf { it.debit })
        val paymentsOut = Money.m(movements.sumOf { it.credit })
        val closingVal = Money.m(opening + receipts - paymentsOut)
        val c = CashClosing(date = date, accountId = ca.id, opening = opening, receipts = receipts, payments = paymentsOut, closing = closingVal, notes = notes, createdBy = userId, createdAt = Dates.nowIso())
        cash.upsertClosing(c)
        audit(userId, "close", "cash_closings", ca.id, "$date closing=$closingVal")
        cash.closing(date, ca.id) ?: c
    }

    // --- Statements & balances ---
    suspend fun partyBalance(kind: String, partyId: Long): Double {
        return if (kind == "customer") {
            val p = parties.customer(partyId) ?: return 0.0
            val invs = sales.forCustomer(partyId)
            val invTotal = Money.m(invs.sumOf { it.total })
            val paid = payments.receivedFromCustomer(partyId)
            Money.m(p.openingBalance + invTotal - paid)
        } else {
            val p = parties.supplier(partyId) ?: return 0.0
            val invs = purchases.forSupplier(partyId)
            val invTotal = Money.m(invs.sumOf { it.total })
            val paid = payments.paidToSupplier(partyId)
            Money.m(p.openingBalance + invTotal - paid)
        }
    }

    data class StatementRow(val date: String, val ref: String, val kind: String, val amount: Double, val direction: String)
    suspend fun partyStatement(kind: String, partyId: Long): List<StatementRow> {
        val rows = mutableListOf<StatementRow>()
        if (kind == "customer") {
            sales.forCustomer(partyId).forEach { rows.add(StatementRow(it.date, it.invoiceNo, "sale", it.total, "debit")) }
            payments.list(10000).filter { it.partyType=="customer" && it.partyId==partyId && it.direction=="in" }
                .forEach { rows.add(StatementRow(it.date, it.reference ?: "#${it.id}", "payment", it.amount, "credit")) }
        } else {
            purchases.forSupplier(partyId).forEach { rows.add(StatementRow(it.date, it.invoiceNo, "purchase", it.total, "credit")) }
            payments.list(10000).filter { it.partyType=="supplier" && it.partyId==partyId && it.direction=="out" }
                .forEach { rows.add(StatementRow(it.date, it.reference ?: "#${it.id}", "payment", it.amount, "debit")) }
        }
        return rows.sortedWith(compareBy({ it.date }, { it.ref }))
    }

    // Dashboard
    suspend fun dashboard(from: String?, to: String?): DashboardData {
        val salesList = sales.list(from, to, "confirmed", 10000)
        val purchList = purchases.list(from, to, "confirmed", 10000)
        val salesTotal = Money.m(salesList.sumOf { it.total })
        val purchaseTotal = Money.m(purchList.sumOf { it.total })
        val pl = engine.incomeStatement(from, to)
        val cashBalance = Money.m(listOfNotNull(accounts.byCode("1000")?.id, accounts.byCode("1010")?.id).sumOf { engine.normalBalance(it) })
        val receivables = engine.normalBalance(accounts.byCode("1100")!!.id)
        val payables = engine.normalBalance(accounts.byCode("2000")!!.id)
        val invValue = products.inventoryValue()
        val low = products.lowStock()
        val recent = mutableListOf<RecentTx>()
        sales.list(from, to, "confirmed", 5).forEach { recent.add(RecentTx(it.date, it.invoiceNo, it.total, "sale")) }
        purchases.list(from, to, "confirmed", 5).forEach { recent.add(RecentTx(it.date, it.invoiceNo, it.total, "purchase")) }
        payments.list(5).forEach { recent.add(RecentTx(it.date, it.reference ?: "#${it.id}", it.amount, "payment")) }
        recent.sortWith(compareByDescending<RecentTx> { it.date }.thenByDescending { it.ref })
        return DashboardData(salesTotal, purchaseTotal, pl.grossProfit, pl.netProfit, pl.operatingExpenses, pl.revenue, pl.cogs, cashBalance, receivables, payables, Money.m(invValue), low, recent.take(10))
    }
}

data class RecentTx(val date: String, val ref: String, val amount: Double, val kind: String)

// Input DTOs
data class ProductInput(val sku: String?, val barcode: String?, val nameAr: String, val nameEn: String, val categoryId: Long?, val purchaseCost: Double, val sellingPrice: Double, val quantity: Double, val minStock: Double, val imagePath: String?)
data class SaleItemInput(val productId: Long, val quantity: Double, val unitPrice: Double, val discount: Double)
data class SaleInput(val date: String, val customerId: Long?, val paymentMethod: String, val currency: String, val exchangeRate: Double, val discount: Double, val tax: Double, val paid: Double, val items: List<SaleItemInput>, val notes: String?, val invoiceNo: String = "")
data class PreparedSaleItem(val productId: Long, val quantity: Double, val unitPrice: Double, val discount: Double, val cost: Double, val lineTotal: Double)
data class PurchaseItemInput(val productId: Long, val quantity: Double, val unitCost: Double, val discount: Double)
data class PurchaseInput(val date: String, val supplierId: Long?, val paymentMethod: String, val currency: String, val exchangeRate: Double, val discount: Double, val tax: Double, val paid: Double, val items: List<PurchaseItemInput>, val notes: String?, val invoiceNo: String = "")
data class PreparedPurchaseItem(val productId: Long, val quantity: Double, val unitCost: Double, val discount: Double, val lineTotal: Double)
data class ExpenseInput(val date: String, val categoryId: Long?, val accountId: Long?, val amount: Double, val currency: String, val exchangeRate: Double, val description: String, val paidFrom: Long?)
data class CashTxInput(val accountId: Long, val direction: String, val amount: Double, val date: String, val description: String, val reference: String?, val incomeCategoryId: Long?, val expenseAccountId: Long?, val toAccountId: Long?)
data class PartyInput(val nameAr: String, val nameEn: String, val phone: String?, val email: String?, val address: String?, val openingBalance: Double)
data class PaymentInput(val date: String, val direction: String, val partyType: String, val partyId: Long, val invoiceId: Long?, val amount: Double, val currency: String, val exchangeRate: Double, val method: String, val reference: String?)
data class DashboardData(val salesTotal: Double, val purchaseTotal: Double, val grossProfit: Double, val netProfit: Double, val operatingExpenses: Double, val revenue: Double, val cogs: Double, val cashBalance: Double, val receivables: Double, val payables: Double, val inventoryValue: Double, val lowStock: List<Product>, val recent: List<RecentTx> = emptyList())
