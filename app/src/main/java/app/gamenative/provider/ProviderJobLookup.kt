package app.gamenative.provider

object ProviderJobLookup {
    fun latestByItem(jobs: List<TransferJob>): Map<String, TransferJob> {
        val latest = LinkedHashMap<String, TransferJob>()
        jobs.forEach { job -> latest.putIfAbsent(job.itemId, job) }
        return latest
    }
}
