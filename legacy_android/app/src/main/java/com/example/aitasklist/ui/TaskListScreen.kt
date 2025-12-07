package com.example.aitasklist.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitasklist.TaskViewModel
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.aitasklist.model.Task
import android.content.Intent
import android.provider.CalendarContract
import android.content.ContentUris

import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.stringResource
import com.example.aitasklist.R

@Composable
fun TaskListScreen(
    viewModel: TaskViewModel = viewModel(),
    isDarkTheme: Boolean = false,
    onThemeToggle: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var textInput by remember { mutableStateOf("") }
    var showCalendarDialog by remember { mutableStateOf(false) }
    var isSplitInputEnabled by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        viewModel.loadCalendars()
    }
    
    val context = LocalContext.current
    var pendingCalendarTask by remember { mutableStateOf<Task?>(null) }

    val calendarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val readGranted = permissions[android.Manifest.permission.READ_CALENDAR] == true
        val writeGranted = permissions[android.Manifest.permission.WRITE_CALENDAR] == true

        if (readGranted && writeGranted && pendingCalendarTask != null) {
            viewModel.addToCalendar(pendingCalendarTask!!) { accountName ->
                Toast.makeText(context, context.getString(R.string.added_to_calendar, accountName), Toast.LENGTH_SHORT).show()
                pendingCalendarTask = null
            }
        } else {
            Toast.makeText(context, context.getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium
            )
            Row {

                
                Box {
                    var showMenu by remember { mutableStateOf(false) }
                    
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_desc))
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
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
                            text = { Text("Calendars") },
                            onClick = {
                                showCalendarDialog = true
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = "Select Calendar"
                                )
                            }
                        )
                    }
                }
            }
        }

        if (showCalendarDialog) {
            CalendarSelectionDialog(
                calendars = uiState.calendars,
                defaultCalendarId = uiState.defaultCalendarId,
                onCalendarSelected = { viewModel.setDefaultCalendar(it) },
                onDismiss = { showCalendarDialog = false }
            )
        }

        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            label = { Text(stringResource(R.string.enter_plans_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { 
                        viewModel.generateTasks(textInput, isSplitInputEnabled)
                        textInput = "" 
                    },
                    enabled = !uiState.isLoading
                ) {
                    Text(stringResource(R.string.generate_tasks))
                }

                Button(
                    onClick = { isSplitInputEnabled = !isSplitInputEnabled },
                    border = if (isSplitInputEnabled) BorderStroke(2.dp, androidx.compose.ui.graphics.Color.Green) else null
                ) {
                    Text("Split")
                }
            }

            TextButton(
                onClick = { viewModel.removeCompletedTasks() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.remove_completed))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (uiState.error != null) {
            Text(
                text = stringResource(R.string.error_prefix, uiState.error ?: ""),
                color = MaterialTheme.colorScheme.error
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.tasks, key = { it.id }) { task ->
                    TaskItem(
                        task = task,
                        onCheckedChange = { viewModel.toggleTaskCompletion(task.id) },
                        onDateChange = { newDate -> viewModel.updateTaskDate(task.id, newDate) },
                        onAddToCalendar = {
                            pendingCalendarTask = task
                            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                                viewModel.addToCalendar(task) { accountName ->
                                    Toast.makeText(context, context.getString(R.string.added_to_calendar, accountName), Toast.LENGTH_SHORT).show()
                                    pendingCalendarTask = null
                                }
                            } else {
                                calendarLauncher.launch(arrayOf(
                                    android.Manifest.permission.READ_CALENDAR,
                                    android.Manifest.permission.WRITE_CALENDAR
                                ))
                            }
                        },
                        onOpenCalendar = {
                            viewModel.openCalendarEvent(
                                task = task,
                                onSuccess = { uri ->
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        data = uri
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, context.getString(R.string.could_not_open_calendar), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onError = {
                                    Toast.makeText(context, context.getString(R.string.event_not_found_resync), Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}
