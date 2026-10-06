package com.naseeb.salestracker.data.model

import java.time.LocalDate
import java.time.format.DateTimeParseException

object SalesEntryValidator {
    enum class Code {
        INVALID_DATE, UNKNOWN_SHOP, NEGATIVE_COUNT, COUNT_TOO_LARGE
    }

    data class ValidationError(val field: String, val code: Code)

    fun validate(entry: SalesEntry): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        try {
            LocalDate.parse(entry.date)
        } catch (e: DateTimeParseException) {
            errors.add(ValidationError("date", Code.INVALID_DATE))
        }

        if (entry.shop !in Shops.ALL) {
            errors.add(ValidationError("shop", Code.UNKNOWN_SHOP))
        }

        val counts = listOf(
            "cd" to entry.cd,
            "mobile" to entry.mobile,
            "bajaj" to entry.bajaj,
            "idfc" to entry.idfc,
            "hdb" to entry.hdb,
            "tvs" to entry.tvs,
            "poonwala" to entry.poonwala,
            "others" to entry.others
        )

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
