package com.example.aitasklist.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aitasklist.model.Task
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RestoreTasksDialog(
    deletedTasks: List<Task>,
    onRestoreTask: (String) -> Unit,
    onHardDeleteTask: (String) -> Unit, // Optional, for cleaning
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore Deleted Tasks") },
        text = {
            if (deletedTasks.isEmpty()) {
                Text("No deleted tasks found.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(deletedTasks) { task ->
                        DeletedTaskItem(
                            task = task,
                            onRestore = { onRestoreTask(task.id) },
                            onDeleteForever = { onHardDeleteTask(task.id) }
                        )
                        Divider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DeletedTaskItem(
    task: Task,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
    
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (task.scheduledDate != 0L) {
                 Text(
                    text = dateFormat.format(Date(task.scheduledDate)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        Row {
             IconButton(onClick = onRestore) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Restore",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            // Optional: Hard delete button? Uses simple Text or Trash icon if available
            // Let's stick to Restore for now to minimize clutter, user can ignore them or we can add "Clean" later.
            // But having a way to permanently delete is good.
            // Let's add a small text button or icon.
            /* 
            IconButton(onClick = onDeleteForever) {
                Icon(
                    imageVector = Icons.Default.Delete, 
                    contentDescription = "Delete Forever",
                     tint = MaterialTheme.colorScheme.error
                )
            }
            */
        }
    }
}
