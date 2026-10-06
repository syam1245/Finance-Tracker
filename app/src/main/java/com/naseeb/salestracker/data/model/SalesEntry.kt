package com.naseeb.salestracker.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class SalesEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val shop: String,
    val cd: Int,
    val mobile: Int,
    val bajaj: Int,
    val idfc: Int,
    val hdb: Int,
    val tvs: Int,
    val poonwala: Int,
    val others: Int
) {
    val totalCount: Int
        get() = cd + mobile

    val totalFinance: Int
        get() = bajaj + idfc + hdb + tvs + poonwala + others
}
