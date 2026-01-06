package com.example.aitasklist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.aitasklist.data.repository.CalendarInfo
import com.example.aitasklist.R

@Composable
fun CalendarFilterDialog(
    calendars: List<CalendarInfo>,
    excludedCalendarIds: Set<String>,
    onToggleExclusion: (Long, Boolean) -> Unit,
    onToggleGroupExclusion: (List<Long>, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sync Settings") },
        text = {
            Column {
                Text(
                    text = "Select which calendars to sync with. Unchecked calendars will be ignored.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyColumn {
                    val groupedCalendars = calendars.groupBy { it.accountName }
                    
                    groupedCalendars.forEach { (accountName, calendarsInAccount) ->
                        item {
                            val allIds = calendarsInAccount.map { it.id }
                            // If ALL are NOT excluded (i.e. all cached/included), group is Checked.
                            // If ANY is excluded, group is unchecked (simplest UX or Tri-state? Request said "uncheck everything" -> likely Master Switch)
                            // "User: just click the account name checkbox and it unchecks everything."
                            // Let's implement:
                            // Checked if NONE are excluded.
                            // Unchecked if ANY is excluded.
                            // Click -> If checked(full), uncheck all. If unchecked(partial/none), check all.
                            
                            val areAllIncluded = calendarsInAccount.none { excludedCalendarIds.contains(it.id.toString()) }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // If currently all included (Checked), we want to Exclude All (checkbox -> unchecked)
                                        // If currently not all included (Unchecked), we want to Include All (checkbox -> checked)
                                        onToggleGroupExclusion(allIds, areAllIncluded) 
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = areAllIncluded,
                                    onCheckedChange = { checked ->
                                        // checked = true (Include All) -> excluded = false
                                        // checked = false (Exclude All) -> excluded = true
                                        onToggleGroupExclusion(allIds, !checked)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.primary,
                                        uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = accountName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        
                        items(calendarsInAccount) { calendar ->
                            val isExcluded = excludedCalendarIds.contains(calendar.id.toString())
                            val isChecked = !isExcluded
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onToggleExclusion(calendar.id, !isExcluded)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp), // Tighter vertical padding inside group
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        onToggleExclusion(calendar.id, !checked)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(calendar.displayName, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        item {
                            Divider(modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}
