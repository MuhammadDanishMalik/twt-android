package com.talkswithtanha.twt.features.support

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.talkswithtanha.twt.core.designsystem.IosColors
import com.talkswithtanha.twt.core.designsystem.components.TwtButton
import com.talkswithtanha.twt.core.model.SupportTicket
import com.talkswithtanha.twt.features.settings.SettingsMetrics
import com.talkswithtanha.twt.features.settings.SheetHeader

/**
 * Raising a problem: what it is about, and what happened.
 *
 * Three fields and no more. Every extra box on a support form is a reason not to
 * finish it, and the one thing genuinely needed — what went wrong, in their own
 * words — is what people will write if nothing else is in the way. The category
 * is there because it is the difference between a billing question and a crash
 * in the panel's queue, and it is one tap.
 */
@Composable
fun NewTicketScreen(
    onClose: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: SupportTicketsViewModel = hiltViewModel()
) {
    var subject by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(SupportTicket.categories.first()) }
    var detail by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canSave = subject.trim().length > 2 && detail.trim().length > 4

    Column(
        Modifier
            .fillMaxSize()
            .background(IosColors.Background)
            .statusBarsPadding()
            .imePadding()
    ) {
        SheetHeader("New ticket", onClose)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SettingsMetrics.pageInset)
        ) {
            FieldLabel("What is it about?")
            TicketField(
                value = subject,
                hint = "Access code not working",
                onChange = { subject = it.take(120) }
            )

            FieldLabel("Category")
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SupportTicket.categories.forEach { option ->
                    val selected = option == category
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) Color.White else Color(0xFF1F1F1F))
                            .clickable { category = option }
                            .padding(horizontal = 13.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = option,
                            color = if (selected) Color.Black else IosColors.TextPrimary.copy(alpha = 0.75f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            FieldLabel("What happened?")
            TicketField(
                value = detail,
                hint = "Tell us what you did, what you expected, and what you saw instead.",
                onChange = { detail = it },
                minHeight = 120.dp
            )

            error?.let {
                Text(it, color = IosColors.SellRed, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
            }

            Text(
                text = "You will get an email whenever somebody replies or the ticket moves.",
                color = IosColors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            TwtButton(
                text = if (isSaving) "Opening…" else "Raise ticket",
                enabled = canSave && !isSaving,
                loading = isSaving,
                onClick = {
                    isSaving = true
                    error = null
                    viewModel.create(
                        subject = subject,
                        category = category,
                        detail = detail,
                        onCreated = { id ->
                            isSaving = false
                            onCreated(id)
                        },
                        onFailed = {
                            isSaving = false
                            error = it
                        }
                    )
                },
                modifier = Modifier.padding(bottom = 40.dp)
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = IosColors.TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun TicketField(
    value: String,
    hint: String,
    onChange: (String) -> Unit,
    minHeight: androidx.compose.ui.unit.Dp = 0.dp
) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF171717))
            .padding(14.dp)
    ) {
        if (value.isEmpty()) {
            Text(hint, color = IosColors.TextSecondary, fontSize = 15.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = LocalTextStyle.current.copy(color = IosColors.TextPrimary, fontSize = 15.sp),
            cursorBrush = SolidColor(IosColors.TextPrimary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
