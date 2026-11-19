package com.orchid.finance.data

import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.math.abs
import kotlin.math.round

object Money {
    fun m(v: Double?): Double = round((v ?: 0.0) * 100.0) / 100.0
}

object Dates {
    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    fun nowIso(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
    fun nowTs(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    fun prevDay(date: String): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance().apply { time = fmt.parse(date)!!; add(Calendar.DAY_OF_MONTH, -1) }
        return fmt.format(cal.time)
    }
}

object Passwords {
    private const val ITER = 210_000
    fun hash(password: String): String {
        require(password.length >= 8) { "Password must be at least 8 characters" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val spec = PBEKeySpec(password.toCharArray(), salt, ITER, 256)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val digest = skf.generateSecret(spec).encoded
        return "pbkdf2_sha256$${ITER}$${salt.joinToString("") { "%02x".format(it) }}$${digest.joinToString("") { "%02x".format(it) }}"
    }
    fun verify(password: String, stored: String): Boolean {
        return try {
            val parts = stored.split("$")
            if (parts.size != 4 || parts[0] != "pbkdf2_sha256") {
                false
            } else {
                val iter = parts[1].toInt()
                val salt = parts[2].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val expected = parts[3]
                val spec = PBEKeySpec(password.toCharArray(), salt, iter, 256)
                val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                val digest = skf.generateSecret(spec).encoded.joinToString("") { "%02x".format(it) }
                digest == expected
            }
        } catch (e: Exception) {
            false
        }
    }
}

data class JournalLineInput(
    val accountRef: Any, // Long id or String code
    val debit: Double = 0.0,
    val credit: Double = 0.0,
    val currency: String = "YER",
    val originalAmount: Double? = null,
    val exchangeRate: Double = 1.0
)

data class PreparedLine(
    val accountId: Long, val debit: Double, val credit: Double,
    val currency: String, val originalAmount: Double, val exchangeRate: Double
)

class AccountingEngine(private val db: AppDatabase) {
    private val j get() = db.journalDao()
    private val a get() = db.accountDao()
    private val extra get() = db.accountingExtraDao()

    suspend fun resolveAccount(ref: Any): Account = when (ref) {
        is Long -> a.byId(ref) ?: error("Unknown account: $ref")
        is Int -> a.byId(ref.toLong()) ?: error("Unknown account: $ref")
        is String -> a.byCode(ref) ?: error("Unknown account: $ref")
        is Account -> ref
        else -> error("Unknown account ref: $ref")
    }

    suspend fun ensureOpenPeriod(date: String) {
        val locked = extra.lockedCovering(date)
        if (locked != null) {
            val detail = if (!locked.notes.isNullOrBlank()) " (${locked.notes})" else ""
            error("Accounting period ${locked.dateFrom} .. ${locked.dateTo} is locked$detail; posting is not allowed")
        }
    }

    private suspend fun nextRef(dateStr: String, prefix: String = "JE"): String {
        val base = "$prefix-${dateStr.replace("-", "")}"
        // simple count based
        val list = j.list(dateStr, dateStr, 10000)
        var count = list.count { it.reference.startsWith(base) } + 1
        return "$base-${count.toString().padStart(4, '0')}"
    }

    suspend fun postJournal(
        dateStr: String, description: String, lines: List<JournalLineInput>,
        reference: String? = null, sourceType: String? = null, sourceId: Long? = null,
        userId: Long? = null
    ): Long {
        ensureOpenPeriod(dateStr)
        val prepared = mutableListOf<PreparedLine>()
        for (line in lines) {
            val account = resolveAccount(line.accountRef)
            val debit = Money.m(line.debit)
            val credit = Money.m(line.credit)
            require(debit >= 0 && credit >= 0) { "Journal lines cannot have negative amounts" }
            if (debit == 0.0 && credit == 0.0) continue
            require(!(debit > 0 && credit > 0)) { "A journal line cannot be both debit and credit" }
            prepared.add(PreparedLine(account.id, debit, credit, line.currency,
                Money.m(line.originalAmount ?: (if (debit > 0) debit else credit)), line.exchangeRate))
        }
        val totalDebit = Money.m(prepared.sumOf { it.debit })
        val totalCredit = Money.m(prepared.sumOf { it.credit })
        require(prepared.isNotEmpty() && totalDebit == totalCredit) {
            "Unbalanced journal: debits $totalDebit != credits $totalCredit"
        }
        val ref = reference ?: nextRef(dateStr)
        val jid = j.insertEntry(JournalEntry(date = dateStr, reference = ref, description = description,
            sourceType = sourceType, sourceId = sourceId, createdBy = userId, createdAt = Dates.nowIso()))
        prepared.forEach { pl ->
            j.insertLine(JournalLine(journalId = jid, accountId = pl.accountId, debit = pl.debit, credit = pl.credit,
                currency = pl.currency, originalAmount = pl.originalAmount, exchangeRate = pl.exchangeRate))
        }
        return jid
    }

    suspend fun reverseJournal(journalId: Long, dateStr: String = Dates.today(), userId: Long? = null, reason: String = ""): Long {
        val entry = j.entry(journalId) ?: error("Journal entry not found")
        require(entry.reversedBy == null) { "Journal entry has already been reversed" }
        val lines = j.lines(journalId)
        val revLines = lines.map { JournalLineInput(it.accountId, it.credit, it.debit, it.currency, it.originalAmount, it.exchangeRate) }
        val newId = postJournal(dateStr, "Reversal of ${entry.reference}${if (reason.isNotBlank()) ": $reason" else ""}",
            revLines, nextRef(dateStr, "REV"), "reversal", journalId, userId)
        j.markReversed(journalId, newId)
        return newId
    }

    suspend fun netMovement(accountId: Long, from: String? = null, to: String? = null): Double = Money.m(j.netMovement(accountId, from, to))

    suspend fun normalBalance(accountId: Long, from: String? = null, to: String? = null): Double {
        val acc = a.byId(accountId) ?: return 0.0
        val v = netMovement(accountId, from, to)
        return Money.m(if (acc.normalBalance == "debit") v else -v)
    }

    suspend fun trialBalance(from: String? = null, to: String? = null): TrialBalanceResult {
        val rows = j.balances(from, to).map {
            val net = Money.m(it.debit - it.credit)
            val normal = Money.m(if (it.normalBalance == "debit") net else -net)
            BalanceRow(it, net, normal)
        }.filter { it.raw.debit != 0.0 || it.raw.credit != 0.0 }
        val debit = Money.m(rows.sumOf { it.raw.debit })
        val credit = Money.m(rows.sumOf { it.raw.credit })
        val diff = Money.m(debit - credit)
        return TrialBalanceResult(rows, debit, credit, diff, abs(diff) < 0.005)
    }

    suspend fun incomeStatement(from: String? = null, to: String? = null): IncomeStatementResult {
        val rows = j.balances(from, to).map {
            val net = Money.m(it.debit - it.credit)
            BalanceRow(it, net, Money.m(if (it.normalBalance == "debit") net else -net))
        }
        val revenueAccounts = rows.filter { it.raw.type == "income" }
        val revenue = Money.m(revenueAccounts.filter { it.raw.groupName == "Revenue" && it.raw.normalBalance == "credit" }.sumOf { it.normal } -
                revenueAccounts.filter { it.raw.groupName == "Revenue" && it.raw.normalBalance == "debit" }.sumOf { it.normal })
        val cogs = Money.m(rows.filter { it.raw.groupName == "COGS" }.sumOf { it.normal })
        val gross = Money.m(revenue - cogs)
        val opEx = Money.m(rows.filter { it.raw.type == "expense" && it.raw.groupName != "COGS" }.sumOf { it.normal })
        val other = Money.m(revenueAccounts.filter { it.raw.groupName != "Revenue" }.sumOf { if (it.raw.normalBalance == "credit") it.normal else -it.normal })
        return IncomeStatementResult(revenue, cogs, gross, opEx, other, Money.m(gross - opEx + other))
    }

    suspend fun balanceSheet(asOf: String? = null): BalanceSheetResult {
        val rows = j.balances(null, asOf).map {
            val net = Money.m(it.debit - it.credit)
            BalanceRow(it, net, Money.m(if (it.normalBalance == "debit") net else -net))
        }
        val assets = Money.m(rows.filter { it.raw.type == "asset" }.sumOf { it.normal })
        val liabilities = Money.m(rows.filter { it.raw.type == "liability" }.sumOf { it.normal })
        val equityAcc = Money.m(rows.filter { it.raw.type == "equity" && it.raw.code != "9000" }.sumOf { it.normal })
        val profit = incomeStatement(null, asOf).netProfit
        val retained = Money.m(equityAcc + profit)
        val totalLE = Money.m(liabilities + retained)
        return BalanceSheetResult(assets, liabilities, equityAcc, profit, retained, totalLE, Money.m(assets - totalLE), abs(assets - totalLE) < 0.01)
    }

    suspend fun cashFlow(from: String? = null, to: String? = null): CashFlowResult {
        val cashIds = listOfNotNull(a.byCode("1000")?.id, a.byCode("1010")?.id)
        if (cashIds.isEmpty()) return CashFlowResult(0.0,0.0,0.0,0.0,0.0,0.0)
        val net = Money.m(cashIds.sumOf { netMovement(it, from, to) })
        val opening = if (from != null) Money.m(cashIds.sumOf { netMovement(it, null, Dates.prevDay(from)) }) else 0.0
        val closing = Money.m(opening + net)
        return CashFlowResult(opening, net, 0.0, 0.0, net, closing)
    }

    suspend fun generalLedger(accountId: Long, from: String? = null, to: String? = null): LedgerResult {
        val acc = a.byId(accountId) ?: error("Account not found")
        val opening = if (from != null) Money.m(j.netMovement(accountId, null, Dates.prevDay(from))) else 0.0
        var running = opening
        val rows = j.ledger(accountId, from, to).map {
            running = Money.m(running + it.debit - it.credit)
            LedgerDisplayRow(it.date, it.reference, it.description, it.debit, it.credit, running)
        }
        return LedgerResult(acc, opening, rows, running)
    }
}

data class BalanceRow(val raw: AccountBalance, val net: Double, val normal: Double)
data class TrialBalanceResult(val rows: List<BalanceRow>, val debit: Double, val credit: Double, val difference: Double, val balanced: Boolean)
data class IncomeStatementResult(val revenue: Double, val cogs: Double, val grossProfit: Double, val operatingExpenses: Double, val otherIncome: Double, val netProfit: Double)
data class BalanceSheetResult(val assets: Double, val liabilities: Double, val equityAccounts: Double, val currentProfit: Double, val retainedEarnings: Double, val totalLiabilitiesEquity: Double, val difference: Double, val balanced: Boolean)
data class CashFlowResult(val opening: Double, val operating: Double, val investing: Double, val financing: Double, val net: Double, val closing: Double)
data class LedgerDisplayRow(val date: String, val reference: String, val description: String, val debit: Double, val credit: Double, val balance: Double)
data class LedgerResult(val account: Account, val opening: Double, val rows: List<LedgerDisplayRow>, val closing: Double)
