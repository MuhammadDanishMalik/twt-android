package com.talkswithtanha.twt.features.marketplace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.talkswithtanha.twt.core.data.ExchangeRateRepository
import com.talkswithtanha.twt.core.data.SupportConfigRepository
import com.talkswithtanha.twt.core.designsystem.Spacing
import com.talkswithtanha.twt.core.designsystem.TABULAR_FIGURES
import com.talkswithtanha.twt.core.designsystem.TwtColors
import com.talkswithtanha.twt.core.designsystem.components.TwtCard
import com.talkswithtanha.twt.core.designsystem.components.TwtScreen
import com.talkswithtanha.twt.core.designsystem.components.TwtSecondaryButton
import com.talkswithtanha.twt.core.model.ExchangeRate
import com.talkswithtanha.twt.core.model.SupportConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.math.roundToLong

@HiltViewModel
class MarketplaceViewModel @Inject constructor(
    exchangeRates: ExchangeRateRepository,
    supportConfig: SupportConfigRepository
) : ViewModel() {

    val rate: StateFlow<ExchangeRate?> = exchangeRates.observeRate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val support: StateFlow<SupportConfig> = supportConfig.observeConfig()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SupportConfig())
}

/**
 * The USD/PKR desk.
 *
 * The rate is a number Tanha types into the admin panel — deliberately not
 * derived from a market feed, because he sets the spread he is willing to
 * honour and the app must show exactly that. It also must not move under a
 * member who is deciding whether to trade at it.
 *
 * Opening a deal is a WhatsApp conversation rather than an in-app flow. The
 * `deals` collection and its rules exist for the admin panel's side of that;
 * wiring the member's side is the next piece of work here.
 */
@Composable
fun MarketplaceScreen(
    onBack: () -> Unit,
    onOpenWhatsApp: (String) -> Unit,
    onNewDeal: () -> Unit,
    onMyDeals: () -> Unit,
    viewModel: MarketplaceViewModel = hiltViewModel()
) {
    val rate by viewModel.rate.collectAsStateWithLifecycle()
    val support by viewModel.support.collectAsStateWithLifecycle()
    var usdInput by remember { mutableStateOf("") }

    TwtScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TwtColors.TextPrimary
                    )
                }
                Text(
                    text = "Exchange",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TwtColors.TextPrimary
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            val currentRate = rate

            if (currentRate == null) {
                TwtCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "The rate is not published right now.",
                        style = MaterialTheme.typography.titleMedium,
                        color = TwtColors.TextPrimary
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "Message Tanha and he will quote you directly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TwtColors.TextSecondary
                    )
                }
            } else {
                RateCard(currentRate)

                Spacer(Modifier.height(Spacing.lg))

                OutlinedTextField(
                    value = usdInput,
                    onValueChange = { raw ->
                        usdInput = raw.filter { it.isDigit() || it == '.' }
                    },
                    label = { Text("Amount in USD") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TwtColors.Gold,
                        unfocusedBorderColor = TwtColors.HairlineStrong,
                        focusedLabelColor = TwtColors.Gold,
                        unfocusedLabelColor = TwtColors.TextTertiary,
                        focusedTextColor = TwtColors.TextPrimary,
                        unfocusedTextColor = TwtColors.TextPrimary,
                        cursorColor = TwtColors.Gold
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Converted through integer cents and paisa. The input is a
                // string typed by a person, so it becomes a Double exactly once
                // -- here -- and is an integer from then on.
                val usdCents = usdInput.toDoubleOrNull()?.let { (it * 100).roundToLong() }

                if (usdCents != null && usdCents > 0) {
                    Spacer(Modifier.height(Spacing.lg))
                    TwtCard(modifier = Modifier.fillMaxWidth()) {
                        ConversionRow(
                            "You pay",
                            "PKR ${ExchangeRate.formatPaisa(currentRate.pkrPaisaToBuy(usdCents))}"
                        )
                        Spacer(Modifier.height(Spacing.sm))
                        ConversionRow(
                            "You receive selling",
                            "PKR ${ExchangeRate.formatPaisa(currentRate.pkrPaisaToSell(usdCents))}"
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.xl))

                com.talkswithtanha.twt.core.designsystem.components.TwtButton(
                    text = "Open a deal at this rate",
                    enabled = currentRate.isAcceptingDeals,
                    onClick = onNewDeal
                )

                Spacer(Modifier.height(Spacing.md))

                TwtSecondaryButton(
                    text = "My deals",
                    onClick = onMyDeals
                )

                Spacer(Modifier.height(Spacing.md))

                TwtSecondaryButton(
                    text = "Ask on WhatsApp",
                    icon = Icons.Outlined.Chat,
                    onClick = {
                        val amount = usdInput.takeIf { it.isNotBlank() } ?: "some"
                        onOpenWhatsApp(
                            support.whatsAppUrl(
                                "Hi Tanha, I would like to exchange $amount USD at " +
                                    "PKR ${ExchangeRate.formatPaisa(currentRate.buyPaisa)} per USD."
                            )
                        )
                    }
                )

                if (!currentRate.isAcceptingDeals) {
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = "Not taking deals at the moment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TwtColors.Warning
                    )
                }
            }

            Spacer(Modifier.height(Spacing.huge))
        }
    }
}

@Composable
private fun RateCard(rate: ExchangeRate) {
    TwtCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = rate.pair,
            style = MaterialTheme.typography.labelSmall,
            color = TwtColors.TextTertiary
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = ExchangeRate.formatPaisa(rate.buyPaisa),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFeatureSettings = TABULAR_FIGURES
                ),
                color = TwtColors.TextPrimary
            )
            Spacer(Modifier.padding(horizontal = Spacing.xs))
            when (rate.direction) {
                ExchangeRate.Direction.UP -> Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = "Up",
                    tint = TwtColors.Buy
                )
                ExchangeRate.Direction.DOWN -> Icon(
                    Icons.Filled.ArrowDownward,
                    contentDescription = "Down",
                    tint = TwtColors.Sell
                )
                ExchangeRate.Direction.FLAT -> Unit
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "Sell at ${ExchangeRate.formatPaisa(rate.sellPaisa)} · " +
                "spread ${ExchangeRate.formatPaisa(rate.spreadPaisa)}",
            style = MaterialTheme.typography.bodyMedium,
            color = TwtColors.TextSecondary
        )
    }
}

@Composable
private fun ConversionRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TwtColors.TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFeatureSettings = TABULAR_FIGURES
            ),
            color = TwtColors.TextPrimary
        )
    }
}
