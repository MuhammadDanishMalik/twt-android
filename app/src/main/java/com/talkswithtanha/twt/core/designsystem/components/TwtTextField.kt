package com.talkswithtanha.twt.core.designsystem.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.talkswithtanha.twt.core.designsystem.WebColors
import com.talkswithtanha.twt.core.designsystem.WebFonts

/**
 * The app's text field: Material 3's [OutlinedTextField], in the website's
 * colours — a warm hairline at rest, green when focused, red on error — with
 * the floating label, supporting text and icon slots Material provides.
 *
 * Passwords get their own show/hide toggle, kept here so every password field
 * behaves the same and no screen has to track visibility itself.
 */
@Composable
fun TwtTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    autoCorrect: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = TwtTextFieldDefaults.textStyle,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    var visible by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = true,
        textStyle = textStyle,
        label = { Text(label, fontFamily = WebFonts.Body) },
        placeholder = placeholder?.let {
            { Text(it, style = textStyle.copy(color = WebColors.Text3.copy(alpha = 0.5f))) }
        },
        supportingText = supportingText?.let {
            { Text(it, fontFamily = WebFonts.Body, fontSize = 12.sp) }
        },
        isError = isError,
        leadingIcon = leading,
        trailingIcon = when {
            trailing != null -> trailing
            isPassword -> {
                {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (visible) "Hide password" else "Show password"
                        )
                    }
                }
            }
            else -> null
        },
        visualTransformation = if (isPassword && !visible) PasswordVisualTransformation()
        else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
            capitalization = capitalization,
            autoCorrectEnabled = autoCorrect && !isPassword,
            imeAction = imeAction
        ),
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(3.dp),
        colors = TwtTextFieldDefaults.colors()
    )
}

object TwtTextFieldDefaults {
    val textStyle = TextStyle(
        fontFamily = WebFonts.Body,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        color = WebColors.Text
    )

    @Composable
    fun colors() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = WebColors.Text,
        unfocusedTextColor = WebColors.Text,
        disabledTextColor = WebColors.Text3,
        focusedContainerColor = WebColors.Brand.copy(alpha = 0.03f),
        unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
        disabledContainerColor = Color.White.copy(alpha = 0.02f),
        errorContainerColor = WebColors.Red.copy(alpha = 0.04f),
        cursorColor = WebColors.Brand,
        errorCursorColor = WebColors.Red,
        selectionColors = TextSelectionColors(
            handleColor = WebColors.Brand,
            backgroundColor = WebColors.Brand.copy(alpha = 0.3f)
        ),
        focusedBorderColor = WebColors.Brand,
        unfocusedBorderColor = WebColors.Line2,
        disabledBorderColor = WebColors.Line,
        errorBorderColor = WebColors.Red,
        focusedLabelColor = WebColors.Brand,
        unfocusedLabelColor = WebColors.Text3,
        disabledLabelColor = WebColors.Text3,
        errorLabelColor = WebColors.Red,
        focusedLeadingIconColor = WebColors.Text2,
        unfocusedLeadingIconColor = WebColors.Text3,
        focusedTrailingIconColor = WebColors.Text2,
        unfocusedTrailingIconColor = WebColors.Text3,
        errorTrailingIconColor = WebColors.Red,
        focusedSupportingTextColor = WebColors.Text3,
        unfocusedSupportingTextColor = WebColors.Text3,
        errorSupportingTextColor = WebColors.Red,
        focusedPlaceholderColor = WebColors.Text3,
        unfocusedPlaceholderColor = WebColors.Text3
    )
}
