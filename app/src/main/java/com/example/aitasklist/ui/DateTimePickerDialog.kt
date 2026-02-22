package com.example.aitasklist.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    initialDateMillis: Long,
    initialTimeMillis: Long?, // Null if no time set (All Day)
    onConfirm: (Long, Long?) -> Unit, // returns (DateMillis, TimeMillis?)
    onDismiss: () -> Unit
) {
    // State for Tabs
    var selectedTabIndex by remember { mutableIntStateOf(0) } // 0 = Date, 1 = Time
    val tabs = listOf("Date", "Time")
    
    // Date State
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (initialDateMillis != 0L) {
            // Adjust to UTC for Display as DatePicker expects UTC midnight
            val cal = Calendar.getInstance()
            cal.timeInMillis = initialDateMillis
            val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            utcCal.clear()
            utcCal.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
            utcCal.timeInMillis
        } else {
            System.currentTimeMillis()
        }
    )

    // Time State
    val initialHasTime = initialTimeMillis != null
    var hasTime by remember { mutableStateOf(initialHasTime) }
    
    val timeState = rememberTimePickerState(
        initialHour = if (initialHasTime) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = initialTimeMillis!!
            cal.get(Calendar.HOUR_OF_DAY)
        } else 12,
        initialMinute = if (initialHasTime) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = initialTimeMillis!!
            cal.get(Calendar.MINUTE)
        } else 0,
        is24Hour = false
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.95f),
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { utcDateMillis ->
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        utcCal.timeInMillis = utcDateMillis
                        
                        val localCal = Calendar.getInstance()
                        localCal.clear()
                        localCal.set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH))
                        
                        val finalTimeMillis: Long?
                        if (hasTime) {
                            localCal.set(Calendar.HOUR_OF_DAY, timeState.hour)
                            localCal.set(Calendar.MINUTE, timeState.minute)
                            finalTimeMillis = localCal.timeInMillis
                        } else {
                            finalTimeMillis = null
                        }
                        
                        onConfirm(localCal.timeInMillis, finalTimeMillis)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        ) {
            // Tab Row
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Content
            when (selectedTabIndex) {
                0 -> {
                    // Date Tab
                    DatePicker(
                        state = datePickerState,
                        title = null, // Hide built-in title to save space
                        headline = null,
                        showModeToggle = false
                    )
                }
                1 -> {
                    // Time Tab
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Include Time", style = MaterialTheme.typography.bodyLarge)
                            Switch(
                                checked = hasTime,
                                onCheckedChange = { hasTime = it }
                            )
                        }
                        
                        if (hasTime) {
                            TimePicker(state = timeState)
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "All Day Event",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
