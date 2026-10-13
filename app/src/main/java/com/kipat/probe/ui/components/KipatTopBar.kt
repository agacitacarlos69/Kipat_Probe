package com.kipat.probe.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.kipat.probe.ui.theme.Kipat

/**
 * Material 3 small top app bar on the page background. Title + optional navigation icon + at
 * most two actions. Screens with their own hero (Home) keep the bar small and quiet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KipatTopBar(
    title: String,
    navigationIcon: ImageVector? = null,
    navigationDescription: String = "Back",
    onNavigate: (() -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { titleContent?.invoke() ?: Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (navigationIcon != null && onNavigate != null) {
                IconButton(onClick = onNavigate) { Icon(navigationIcon, contentDescription = navigationDescription) }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Kipat.colors.background,
            scrolledContainerColor = Kipat.colors.background,
            titleContentColor = Kipat.colors.textPrimary,
            navigationIconContentColor = Kipat.colors.textPrimary,
            actionIconContentColor = Kipat.colors.textSecondary,
        ),
    )
}
