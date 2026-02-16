package com.example.aitasklist.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    var showTimePicker by remember { mutableStateOf(false) }
    
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
        is24Hour = false // Could be user preference
    )

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("OK")
                }
            },
            text = {
                TimeInput(state = timeState)
            }
        )
    }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { utcDateMillis ->
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        utcCal.timeInMillis = utcDateMillis
                        
                        val localCal = Calendar.getInstance()
                        localCal.clear()
                        localCal.set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH))
                        
                        // If hasTime, set usage of Time
                        val finalTimeMillis: Long?
                        if (hasTime) {
                            localCal.set(Calendar.HOUR_OF_DAY, timeState.hour)
                            localCal.set(Calendar.MINUTE, timeState.minute)
                            finalTimeMillis = localCal.timeInMillis
                        } else {
                            // All Day -> Midnight
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
        Column {
            DatePicker(state = datePickerState)
            
            Divider()
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Set Time", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = hasTime,
                    onCheckedChange = { hasTime = it }
                )
            }
            
            if (hasTime) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showTimePicker = true }) {
                        val cal = Calendar.getInstance()
                        cal.set(Calendar.HOUR_OF_DAY, timeState.hour)
                        cal.set(Calendar.MINUTE, timeState.minute)
                        val timeFormat = java.text.SimpleDateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                        Text(text = timeFormat.format(cal.time), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
