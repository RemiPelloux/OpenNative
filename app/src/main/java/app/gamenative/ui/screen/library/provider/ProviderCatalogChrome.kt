package app.gamenative.ui.screen.library.provider

object ProviderCatalogChrome {
    fun nextSearchVisible(
        visible: Boolean,
        firstIndex: Int,
        firstOffset: Int,
        previousIndex: Int,
        previousOffset: Int,
        keepVisible: Boolean,
    ): Boolean {
        if (keepVisible) return true
        if (firstIndex == 0 && firstOffset <= TOP_SLACK) return true
        val down = firstIndex > previousIndex ||
            (firstIndex == previousIndex && firstOffset > previousOffset + SCROLL_SLACK)
        val up = firstIndex < previousIndex ||
            (firstIndex == previousIndex && firstOffset < previousOffset - SCROLL_SLACK)
        return when {
            down -> false
            up -> true
            else -> visible
        }
    }

    private const val TOP_SLACK = 8
    private const val SCROLL_SLACK = 8
}
