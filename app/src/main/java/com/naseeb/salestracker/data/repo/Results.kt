package com.naseeb.salestracker.data.repo

import com.naseeb.salestracker.data.model.SalesEntry
import com.naseeb.salestracker.data.model.SalesEntryValidator.ValidationError

sealed interface SaveResult {
    data class Saved(val entry: SalesEntry) : SaveResult
    data class Duplicate(val existingId: String) : SaveResult
    data class Invalid(val errors: List<ValidationError>) : SaveResult
    object NotFound : SaveResult
    data class IoFailure(val cause: Exception) : SaveResult
}

sealed interface DeleteResult {
    object Deleted : DeleteResult
    object NotFound : DeleteResult
    data class IoFailure(val cause: Exception) : DeleteResult
}

sealed interface WipeResult {
    data class Wiped(val count: Int) : WipeResult
    data class IoFailure(val cause: Exception) : WipeResult
}

sealed interface ImportResult {
    data class Imported(val imported: Int, val skippedDuplicates: Int, val skippedInvalid: Int) : ImportResult
    data class IoFailure(val cause: Exception) : ImportResult
}
