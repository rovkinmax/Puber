package com.kino.puber.ui.feature.history.vm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class HistoryRuntimeStoreTest {

    @Test
    fun update_serializesConcurrentMultiFieldTransitionsWithoutLostRevisions() {
        val store = HistoryRuntimeStore()
        val start = CountDownLatch(1)
        val workers = List(WORKER_COUNT) {
            thread(start = true) {
                start.await()
                repeat(UPDATES_PER_WORKER) {
                    store.update { state ->
                        state.copy(
                            currentPage = state.currentPage + 1,
                            pageAttemptRevision = state.pageAttemptRevision + 1L,
                        )
                    }
                }
            }
        }

        start.countDown()
        workers.forEach(Thread::join)

        val finalState = store.snapshot()
        val expected = WORKER_COUNT * UPDATES_PER_WORKER
        assertEquals(expected, finalState.currentPage)
        assertEquals(expected.toLong(), finalState.pageAttemptRevision)
    }

    @Test
    fun prepareContentPublication_keepsOperationGatedUntilUiCommit() {
        val rowKey = com.kino.puber.domain.interactor.history.HistoryRowKey.DeletionMedia(1)
        val publication = HistoryOperation.AwaitingPublication(
            operationId = 7L,
            kind = HistoryPublicationKind.FIRST_PAGE,
        )
        val store = HistoryRuntimeStore(
            HistoryRuntimeState(
                operation = publication,
                requestedFocusKey = rowKey,
            ),
        )

        val prepared = store.prepareContentPublication(
            availableKeys = setOf(rowKey),
            paginatorFocusKey = null,
        )

        assertEquals(publication, prepared.operation)
        assertEquals(rowKey, prepared.requestedFocusKey)
        assertEquals(publication, store.snapshot().operation)
    }

    @Test
    fun authoritativeEmptyPublicationClearsDeferredRefresh() {
        val store = HistoryRuntimeStore(
            HistoryRuntimeState(
                operation = HistoryOperation.AwaitingPublication(
                    operationId = 7L,
                    kind = HistoryPublicationKind.FIRST_PAGE,
                ),
                refreshAfterPendingOperation = true,
            ),
        )

        assertEquals(true, store.completeEmptyPublication())
        assertFalse(store.snapshot().refreshAfterPendingOperation)
    }

    @Test
    fun duplicateRestartIsRejectedBeforeFirstPageSideEffectStarts() {
        val store = HistoryRuntimeStore()

        assertTrue(store.beginRestart())
        assertFalse(store.beginRestart())
        assertTrue(store.beginFirstPage() != null)
        assertNull(store.beginFirstPage())
    }

    @Test
    fun deferredRefreshReservationBlocksNextPageThenReservesAtomically() {
        val store = HistoryRuntimeStore(
            HistoryRuntimeState(
                currentPage = 1,
                refreshAfterPendingOperation = true,
            ),
        )

        assertFalse(store.beginNextPage())
        assertTrue(store.beginDeferredRefresh())
        val reserved = store.snapshot()
        assertFalse(reserved.refreshAfterPendingOperation)
        assertTrue(reserved.operation is HistoryOperation.RefreshRequested)
    }

    @Test
    fun authoritativeEmptyPublicationDropsStaleQueuedDeletionAndFocus() {
        val queuedKey = com.kino.puber.domain.interactor.history.HistoryRowKey.DeletionMedia(9)
        val store = HistoryRuntimeStore(
            HistoryRuntimeState(
                operation = HistoryOperation.AwaitingPublication(
                    operationId = 7L,
                    kind = HistoryPublicationKind.FIRST_PAGE,
                ),
                queuedDeletion = HistoryQueuedDeletion(
                    rowKey = queuedKey,
                ),
                focusedKey = queuedKey,
                requestedFocusKey = queuedKey,
                openMenuKey = queuedKey,
            ),
        )

        assertTrue(store.completeEmptyPublication())
        val empty = store.snapshot()
        assertNull(empty.queuedDeletion)
        assertNull(empty.focusedKey)
        assertNull(empty.requestedFocusKey)
        assertNull(empty.openMenuKey)
    }

    private companion object {
        const val WORKER_COUNT = 8
        const val UPDATES_PER_WORKER = 250
    }
}
