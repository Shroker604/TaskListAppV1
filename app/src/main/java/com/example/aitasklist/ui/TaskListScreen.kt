package com.example.aitasklist.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitasklist.TaskViewModel

@Composable
fun TaskListScreen(viewModel: TaskViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var textInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "AI Task List",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            label = { Text("Enter your plans (e.g. Buy milk and call Mom)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { 
                    viewModel.generateTasks(textInput)
                    textInput = "" 
                },
                enabled = !uiState.isLoading
            ) {
                Text("Generate Tasks")
            }

            TextButton(
                onClick = { viewModel.removeCompletedTasks() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Remove Completed")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (uiState.error != null) {
            Text(
                text = "Error: ${uiState.error}",
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
                        onDateChange = { newDate -> viewModel.updateTaskDate(task.id, newDate) }
                    )
                }
            }
        }
    }
}
