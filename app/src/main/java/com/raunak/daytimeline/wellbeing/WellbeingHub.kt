package com.raunak.daytimeline.wellbeing

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.raunak.daytimeline.ui.FullScreenPage

/** Screen time, the app blocker, limits and the web filter as one page with tabs. */
val WellbeingTabs = listOf("Overview", "Timeline", "Blocker", "Limits", "Web filter")

@Composable
fun WellbeingHub(initialTab: Int = 0, onClose: () -> Unit) {
    var tab by remember { mutableIntStateOf(initialTab) }
    FullScreenPage("Screen time", onClose) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp, containerColor = Color.Transparent) {
            WellbeingTabs.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> ScreenTimeDashboard()
            1 -> ScreenTimeDashboard(timelineMode = true)
            2 -> com.raunak.daytimeline.pro.FocusGuardTab()
            3 -> WellbeingScreen()
            else -> com.raunak.daytimeline.filter.WebFilterScreen()
        }
    }
}
