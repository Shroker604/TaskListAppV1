package com.example.aitasklist.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.aitasklist.R
import com.example.aitasklist.domain.SortOption
import com.example.aitasklist.model.Task

@Composable
fun TaskHeader(
    sortOption: SortOption,
    sortAscending: Boolean,
    isDarkTheme: Boolean,
    onSortOptionSelected: (SortOption) -> Unit,
    onSortOrderToggle: () -> Unit, // implied by selecting same option
    onSaveReorder: () -> Unit,
    onCancelReorder: () -> Unit,
    onThemeToggle: () -> Unit,
    onOpenCalendarDialog: () -> Unit,
    onOpenFilterDialog: () -> Unit,
    onAutoSchedule: () -> Unit,
    onOpenRestoreDialog: () -> Unit,
    isDailyPlanner: Boolean = false,
    onToggleDailyPlanner: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.app_header),
            style = MaterialTheme.typography.headlineMedium
        )
        Row {
            if (sortOption == SortOption.CUSTOM) {
                IconButton(onClick = onCancelReorder) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel Reorder")
                }
                IconButton(onClick = onSaveReorder) {
                    Icon(Icons.Default.Check, contentDescription = "Save Reorder")
                }
            } else {
                Box {
                    var showSortMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Sort Tasks"
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("By Creation Date") },
                            onClick = {
                                onSortOptionSelected(SortOption.CREATION_DATE)
                                showSortMenu = false
                            },
                            trailingIcon = if (sortOption == SortOption.CREATION_DATE) {
                                {
                                    Icon(
                                        if (sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null
                                    )
                                }
                            } else null
                        )
                        DropdownMenuItem(
                            text = { Text("By Date & Reminder") },
                            onClick = {
                                onSortOptionSelected(SortOption.DATE_REMINDER)
                                showSortMenu = false
                            },
                            trailingIcon = if (sortOption == SortOption.DATE_REMINDER) {
                                {
                                    Icon(
                                        if (sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null
                                    )
                                }
                            } else null
                        )
                        if (sortOption == SortOption.CUSTOM) {
                            DropdownMenuItem(
                                text = { Text("Custom Order", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) },
                                onClick = { },
                                enabled = false,
                                trailingIcon = { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)) }
                            )
                        }
                    }
                }
            }

            Box {
                var showMenu by remember { mutableStateOf(false) }
                // "Main", "View"
                var currentMenuScreen by remember { mutableStateOf("Main") }

                IconButton(onClick = { 
                    showMenu = true 
                    currentMenuScreen = "Main"
                }) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_desc))
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { 
                        showMenu = false 
                        currentMenuScreen = "Main"
                    }
                ) {
                    if (currentMenuScreen == "Main") {
                        DropdownMenuItem(
                            text = { Text(if (isDarkTheme) "Light Mode" else "Dark Mode") },
                            onClick = {
                                onThemeToggle()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Default Calendar") },
                            onClick = {
                                onOpenCalendarDialog()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Default Calendar"
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Sync Settings") },
                            onClick = {
                                onOpenFilterDialog()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "Sync Settings"
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Restore Deleted") },
                            onClick = {
                                onOpenRestoreDialog()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Restore Deleted"
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Auto-Schedule Today") },
                            onClick = {
                                onAutoSchedule()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Auto-Schedule"
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("View") },
                            onClick = { currentMenuScreen = "View" },
                            leadingIcon = {
                                Icon(Icons.Default.Menu, contentDescription = "View Options")
                            },
                            trailingIcon = {
                                Icon(Icons.Default.ArrowForward, contentDescription = null)
                            }
                        )
                    } else if (currentMenuScreen == "View") {
                         DropdownMenuItem(
                            text = { Text("Back") },
                            onClick = { currentMenuScreen = "Main" },
                            leadingIcon = {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        )
                        Divider()
                         DropdownMenuItem(
                            text = { Text("Daily Planner") },
                            onClick = {
                                onToggleDailyPlanner()
                            },
                            leadingIcon = {
                                Checkbox(
                                    checked = isDailyPlanner,
                                    onCheckedChange = null // Handled by onClick of item
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
