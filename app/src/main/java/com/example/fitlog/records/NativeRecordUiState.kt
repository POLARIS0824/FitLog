package com.example.fitlog.records


internal data class NativeRecordUiState(
    val input: NativeRecordInput = NativeRecordInput(java.time.LocalDate.now().toString()),
    val loading: Boolean = true,
    val state: RecordSaveState = RecordSaveState.EMPTY,
    val message: Int? = null,
    val leaveRequested: Boolean = false,
    val deleting: Boolean = false,
    val persisted: NativeRecordInput? = null,
    val readFailed: Boolean = false,
    val closeRequested: Boolean = false,
    val deleted: Boolean = false,
) {
    val exists get() = persisted != null
    val hasChanges get() = input != persisted && (persisted != null || input.hasContent)
    val canEdit get() = !loading && !deleting && !readFailed && !deleted && !closeRequested
}
