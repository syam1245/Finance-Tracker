package com.naseeb.salestracker.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SalesEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val shop: String,
    val cd: Int = 0,
    val mobile: Int = 0,
    val financeValues: Map<String, Int> = emptyMap(),
    // Legacy fields for backward compatibility when reading existing json:
    val bajaj: Int? = null,
    val idfc: Int? = null,
    val hdb: Int? = null,
    val tvs: Int? = null,
    val poonwala: Int? = null,
    val others: Int? = null
) {
    val totalCount: Int
        get() = cd + mobile

    private val cachedResolvedFinances: Map<String, Int> by lazy(LazyThreadSafetyMode.NONE) {
        if (bajaj == null && idfc == null && hdb == null && tvs == null && poonwala == null && others == null) {
            financeValues
        } else {
            val map = financeValues.toMutableMap()
            if (bajaj != null && !map.containsKey("Bajaj")) map["Bajaj"] = bajaj
            if (idfc != null && !map.containsKey("IDFC")) map["IDFC"] = idfc
            if (hdb != null && !map.containsKey("HDB")) map["HDB"] = hdb
            if (tvs != null && !map.containsKey("TVS")) map["TVS"] = tvs
            if (poonwala != null && !map.containsKey("Poonwala")) map["Poonwala"] = poonwala
            if (others != null && !map.containsKey("Others")) map["Others"] = others
            map
        }
    }

    private val cachedTotalFinance: Int by lazy(LazyThreadSafetyMode.NONE) {
        cachedResolvedFinances.values.sum()
    }

    /**
     * Resolves all finance values by combining dynamic [financeValues]
     * with any legacy fields (bajaj, idfc, etc.) if they were deserialized from older records.
     * Cached lazily to prevent heap allocation churn.
     */
    fun getResolvedFinances(): Map<String, Int> = cachedResolvedFinances

    fun getFinanceValue(optionName: String): Int {
        val resolved = cachedResolvedFinances
        return resolved[optionName]
            ?: resolved.entries.firstOrNull { it.key.equals(optionName, ignoreCase = true) }?.value
            ?: 0
    }

    val totalFinance: Int
        get() = cachedTotalFinance
}
