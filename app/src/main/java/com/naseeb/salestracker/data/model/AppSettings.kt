package com.naseeb.salestracker.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
    val shopList: List<String> = DEFAULT_SHOPS,
    val financeOptions: List<String> = DEFAULT_FINANCE_OPTIONS
) {
    companion object {
        val DEFAULT_SHOPS = listOf(
            "MYG FUTURE HARIPAD",
            "NAYANAM MOBILES",
            "MANGALYA MOBILES",
            "D WEB",
            "AEON DIGITAL",
            "ADMIN INFO SOLUTIONS"
        )

        val DEFAULT_FINANCE_OPTIONS = listOf(
            "Bajaj",
            "IDFC",
            "HDB",
            "TVS",
            "Poonwala",
            "Others"
        )
    }
}
