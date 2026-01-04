package com.example.aitasklist.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.aitasklist.R

@Composable
fun TaskInputSection(
    textInput: String,
    onTextInputChange: (String) -> Unit,
    isLoading: Boolean,
    onGenerateTasks: (String, Boolean) -> Unit,
    onRemoveCompleted: () -> Unit
) {
    var isSplitInputEnabled by remember { mutableStateOf(false) }

    Column {
        OutlinedTextField(
            value = textInput,
            onValueChange = onTextInputChange,
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
                        onGenerateTasks(textInput, isSplitInputEnabled)
                    },
                    enabled = !isLoading
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
                onClick = onRemoveCompleted,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.remove_completed))
            }
        }
    }
}
