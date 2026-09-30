package com.raunak.daytimeline.campus

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode

/** Goes back to Campus › Today if needed, then taps the section's shortcut. */
fun ComposeTestRule.openCampusSection(name: String) {
    val back = onAllNodes(hasContentDescription("Campus"))
    if (back.fetchSemanticsNodes().isNotEmpty()) back[0].performClick()
    waitForIdle()
    onAllNodes(hasScrollToIndexAction() and hasTestTag("campusNav"))[0].performScrollToNode(hasText(name))
    onAllNodes(hasText(name) and hasAnyAncestor(hasTestTag("campusNav")))[0].performClick()
    waitForIdle()
}
