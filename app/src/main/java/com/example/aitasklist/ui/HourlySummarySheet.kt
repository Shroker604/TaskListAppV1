package com.example.aitasklist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aitasklist.model.Task
import com.example.aitasklist.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HourlySummarySheet(
    overdueTasks: List<Task>,
    nextHourTasks: List<Task>,
    restOfDayTasks: List<Task>,
    unscheduledTasks: List<Task>,
    onDismiss: () -> Unit,
    onTaskClick: (Task) -> Unit 
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        HourlySummaryContent(
            overdueTasks = overdueTasks,
            nextHourTasks = nextHourTasks,
            restOfDayTasks = restOfDayTasks,
            unscheduledTasks = unscheduledTasks,
            onTaskClick = onTaskClick
        )
    }
}

@Composable
fun HourlySummaryContent(
    overdueTasks: List<Task>,
    nextHourTasks: List<Task>,
    restOfDayTasks: List<Task>,
    unscheduledTasks: List<Task>,
    onTaskClick: (Task) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Handle (Bar) for visual affordance if not in Sheet? 
        // ModalBottomSheet adds handle automatically.
        // For Overlay, we might want one. Adding a small spacer/handle logic if needed.
        // Keeping it simple for now.
        
        Text(
            text = "Hourly Briefing",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 32.dp)
        ) {
            // 1. Overdue (Critical - Yellow/Red)
            if (overdueTasks.isNotEmpty()) {
                item {
                    SectionHeader("Overdue", MaterialTheme.colorScheme.error)
                }
                
                val now = System.currentTimeMillis()
                val startOfToday = DateUtils.getStartOfDay(now)
                
                items(overdueTasks) { task ->
                    val isDayOverdue = task.scheduledDate < startOfToday
                    val containerColor = if (isDayOverdue) MaterialTheme.colorScheme.error else Color(0xFFFFEB3B)
                    val contentColor = if (isDayOverdue) MaterialTheme.colorScheme.onError else Color.Black
                    
                    NextHourTaskCard(
                        task = task,
                        containerColor = containerColor,
                        contentColor = contentColor
                    ) 
                }
            }

            // 2. Next Hour (Critical)
            if (nextHourTasks.isNotEmpty()) {
                item {
                    SectionHeader("Next Hour", MaterialTheme.colorScheme.primary)
                }
                items(nextHourTasks) { task ->
                    NextHourTaskCard(task)
                }
            } else if (overdueTasks.isEmpty()) { 
                 item {
                    SectionHeader("Next Hour", MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                         text = "No urgent tasks. You're clear!",
                         style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onSurfaceVariant,
                         modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            // 3. Rest of Day
            if (restOfDayTasks.isNotEmpty()) {
                item {
                    SectionHeader("Rest of the Day", MaterialTheme.colorScheme.onSurface)
                }
                items(restOfDayTasks) { task ->
                    StandardTaskRow(task)
                }
            }

            // 4. Unscheduled
            if (unscheduledTasks.isNotEmpty()) {
                item {
                    SectionHeader("Unscheduled / Anytime", MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                items(unscheduledTasks) { task ->
                    UnscheduledTaskRow(task)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, color: Color) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
fun NextHourTaskCard(
    task: Task,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = DateUtils.formatTime(task.scheduledDate),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
            Text(
                text = task.content,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
fun StandardTaskRow(task: Task) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = DateUtils.formatTime(task.scheduledDate),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 12.dp).size(60.dp, 20.dp) // Fixed width for alignment
        )
        Text(
            text = task.content,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun UnscheduledTaskRow(task: Task) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.size(60.dp, 20.dp)) // Alignment spacer
        Text(
            text = task.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}
