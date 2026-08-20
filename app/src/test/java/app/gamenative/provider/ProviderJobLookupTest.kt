package app.gamenative.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderJobLookupTest {
    @Test
    fun `keeps the newest job when the list is newest first`() {
        val older = job("1", updatedAt = 1L, state = TransferState.FAILED)
        val newer = job("1", updatedAt = 2L, state = TransferState.DOWNLOADING)
        val latest = ProviderJobLookup.latestByItem(listOf(newer, older))
        assertEquals(TransferState.DOWNLOADING, latest.getValue("1").state)
        assertEquals(newer.jobId, latest.getValue("1").jobId)
    }

    private fun job(itemId: String, updatedAt: Long, state: TransferState): TransferJob = TransferJob(
        jobId = "job-$updatedAt",
        tabId = "tab",
        itemId = itemId,
        title = "Game",
        state = state,
        selectedLink = "https://datanodes.to/file.rar",
        updatedAtEpochMs = updatedAt,
    )
}
