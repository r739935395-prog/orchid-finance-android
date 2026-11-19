package com.orchid.finance.data

import kotlin.math.abs

class InventoryService(private val db: AppDatabase) {
    private val pdao get() = db.productDao()
    private val mdao get() = db.movementDao()

    suspend fun receive(productId: Long, quantity: Double, unitCost: Double, ts: String = Dates.nowTs(),
                        movementType: String = "purchase", sourceType: String? = null, sourceId: Long? = null, reason: String? = null): Double {
        require(quantity > 0) { "Receipt quantity must be positive" }
        val product = pdao.find(productId) ?: error("Product not found")
        val oldQty = product.quantity
        val oldAvg = product.avgCost
        val newQty = Money.m(oldQty + quantity)
        val newAvg = if (newQty != 0.0) ((oldQty * oldAvg + quantity * Money.m(unitCost)) / newQty).let { x -> kotlin.math.round(x * 1_000_000) / 1_000_000 } else 0.0
        pdao.updateStock(productId, newQty, newAvg, Money.m(unitCost))
        mdao.insert(InventoryMovement(productId = productId, ts = ts, movementType = movementType, quantity = Money.m(quantity),
            unitCost = Money.m(unitCost), balanceQty = newQty, balanceValue = Money.m(newQty * newAvg),
            sourceType = sourceType, sourceId = sourceId, reason = reason))
        return newAvg
    }

    suspend fun issue(productId: Long, quantity: Double, ts: String = Dates.nowTs(),
                      movementType: String = "sale", sourceType: String? = null, sourceId: Long? = null, reason: String? = null): Double {
        require(quantity > 0) { "Issue quantity must be positive" }
        val product = pdao.find(productId) ?: error("Product not found")
        val available = product.quantity
        require(quantity <= available + 0.0001) { "Insufficient stock for product $productId: $quantity requested, $available available" }
        val avg = product.avgCost
        val cost = Money.m(quantity * avg)
        val newQty = Money.m(available - quantity)
        pdao.setQuantity(productId, newQty)
        mdao.insert(InventoryMovement(productId = productId, ts = ts, movementType = movementType, quantity = -Money.m(quantity),
            unitCost = avg, balanceQty = newQty, balanceValue = Money.m(newQty * avg),
            sourceType = sourceType, sourceId = sourceId, reason = reason))
        return cost
    }

    suspend fun adjust(productId: Long, newQuantity: Double, unitCost: Double? = null, reason: String = "Manual adjustment",
                       ts: String = Dates.nowTs(), sourceType: String = "adjustment", sourceId: Long? = productId): Pair<Double, Double> {
        val product = pdao.find(productId) ?: error("Product not found")
        val current = product.quantity
        require(newQuantity >= 0) { "Quantity cannot be negative" }
        val delta = Money.m(newQuantity - current)
        if (abs(delta) < 0.0001) return 0.0 to 0.0
        val costDelta = if (delta > 0) {
            val cost = unitCost ?: product.avgCost
            receive(productId, delta, cost, ts = ts, movementType = "adjustment", sourceType = sourceType, sourceId = sourceId, reason = reason)
            Money.m(delta * cost)
        } else {
            val cost = issue(productId, -delta, ts = ts, movementType = "adjustment", sourceType = sourceType, sourceId = sourceId, reason = reason)
            -cost
        }
        return delta to costDelta
    }
}
