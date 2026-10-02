package com.ottotech.workoutbuilder.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ottotech.workoutbuilder.ui.theme.AppType
import com.ottotech.workoutbuilder.ui.theme.Dimens
import com.ottotech.workoutbuilder.ui.theme.LocalAppColors

enum class Tab(val label: String) { HOME("Home"), LIBRARY("Library") }

/**
 * The page frame for the two bottom-nav destinations: background, status-bar inset, and
 * the nav bar itself. [content] is given the remaining height and is expected to do its
 * own scrolling.
 *
 * Deliberately not Material 3's Scaffold + NavigationBar — those bring an indicator pill,
 * their own colour scheme and their own height, all three of which would have to be
 * fought back to reach the handoff's 1px-border-and-glyph design.
 */
@Composable
fun AppScaffold(
    tab: Tab,
    onSelectTab: (Tab) -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Box(Modifier.weight(1f)) { content() }
        BottomNav(tab = tab, onSelectTab = onSelectTab)
    }
}

@Composable
private fun BottomNav(tab: Tab, onSelectTab: (Tab) -> Unit) {
    val colors = LocalAppColors.current
    Column(Modifier.fillMaxWidth().background(colors.background)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Dimens.hairline)
                .background(colors.borderSubtle),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavItem(Tab.HOME, tab == Tab.HOME) { onSelectTab(Tab.HOME) }
            NavItem(Tab.LIBRARY, tab == Tab.LIBRARY) { onSelectTab(Tab.LIBRARY) }
        }
    }
}

@Composable
private fun NavItem(tab: Tab, active: Boolean, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    // Active state is an instant swap with no transition and no ripple — spec §5, Motion.
    val tint = if (active) colors.onBackground else colors.navInactive
    val interaction = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        when (tab) {
            Tab.HOME -> HomeGlyph(color = tint)
            Tab.LIBRARY -> LibraryGlyph(color = tint)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = tab.label,
            style = if (active) AppType.navLabel.copy(fontWeight = FontWeight.Bold) else AppType.navLabel,
            color = tint,
        )
    }
}
