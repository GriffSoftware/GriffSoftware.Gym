package com.griffgym.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.griffgym.presentation.components.GriffGymPrimaryButton
import com.griffgym.presentation.components.GriffGymSecondaryButton
import com.griffgym.presentation.components.NumericInput
import com.griffgym.presentation.theme.GriffGymTheme

/** Reference maxes are the lifter's own planning numbers, so they stay editable. */
@Composable
fun ReferenceMaxDialog(
    editor: ReferenceMaxEditor,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = GriffGymTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .border(1.dp, colors.outlineStrong)
                .padding(20.dp),
        ) {
            Text(
                text = "REFERENCE MAX",
                style = GriffGymTheme.typography.label,
                color = colors.textTertiary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = editor.label,
                style = GriffGymTheme.typography.headline,
                color = colors.textPrimary,
            )
            Spacer(Modifier.height(16.dp))
            NumericInput(
                value = editor.input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "KG",
                isError = editor.error != null,
                imeAction = ImeAction.Done,
                textStyle = GriffGymTheme.typography.dataLarge,
            )
            if (editor.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = editor.error,
                    style = GriffGymTheme.typography.bodySmall,
                    color = colors.error,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GriffGymSecondaryButton(
                    text = "CANCEL",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                GriffGymPrimaryButton(
                    text = "SAVE",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
