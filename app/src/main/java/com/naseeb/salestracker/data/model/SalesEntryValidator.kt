package com.naseeb.salestracker.data.model

import java.time.LocalDate
import java.time.format.DateTimeParseException

object SalesEntryValidator {
    enum class Code {
        INVALID_DATE, UNKNOWN_SHOP, NEGATIVE_COUNT, COUNT_TOO_LARGE
    }

    data class ValidationError(val field: String, val code: Code)

    fun validate(entry: SalesEntry, knownShops: List<String> = Shops.ALL): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        try {
            LocalDate.parse(entry.date)
        } catch (e: DateTimeParseException) {
            errors.add(ValidationError("date", Code.INVALID_DATE))
        }

        if (entry.shop.isBlank()) {
            errors.add(ValidationError("shop", Code.UNKNOWN_SHOP))
        } else if (knownShops.isNotEmpty() && !knownShops.any { it.equals(entry.shop, ignoreCase = true) }) {
            errors.add(ValidationError("shop", Code.UNKNOWN_SHOP))
        }

        val counts = mutableListOf(
            "cd" to entry.cd,
            "mobile" to entry.mobile
        )

        for ((field, value) in entry.getResolvedFinances()) {
            counts.add(field to value)
        }

        for ((field, count) in counts) {
            if (count < 0) {
                errors.add(ValidationError(field, Code.NEGATIVE_COUNT))
            } else if (count > 999_999) {
                errors.add(ValidationError(field, Code.COUNT_TOO_LARGE))
            }
        }

        return errors
    }
}
