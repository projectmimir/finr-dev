package com.projectmimir.finr

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
private fun appTextColorLocal(): Color = appTextColor()

private fun categoryIcon(name: String?): ImageVector {
    return when (name) {
        "Housing" -> Icons.Filled.Home
        "Utilities" -> Icons.Filled.ElectricBolt
        "Groceries" -> Icons.Filled.ShoppingCart
        "Transportation" -> Icons.Filled.DirectionsCar
        "Communication" -> Icons.Filled.PhoneIphone
        "Insurance" -> Icons.Filled.Security
        "Healthcare" -> Icons.Filled.MedicalServices
        "Entertainment" -> Icons.Filled.Movie
        "Dining Out" -> Icons.Filled.Restaurant
        "Subscriptions" -> Icons.Filled.Subscriptions
        "Personal Care" -> Icons.Filled.Spa
        "Shopping" -> Icons.Filled.LocalMall
        "Travel" -> Icons.Filled.Flight
        "Debt Repayment" -> Icons.Filled.CreditCard
        "Savings & Investments" -> Icons.Filled.TrendingUp
        "Gifts & Donations" -> Icons.Filled.CardGiftcard
        "Pets" -> Icons.Filled.Pets
        "Childcare/Education" -> Icons.Filled.School
        "Professional Services" -> Icons.Filled.Work
        else -> Icons.Filled.Category
    }
}

private fun legacyBankLogoRes(bank: String?): Int? {
    return when (bank?.trim()?.uppercase()) {
        "HDFC" -> R.drawable.hdfc_logo
        "AMEX" -> R.drawable.amex_logo
        "ICICI" -> R.drawable.icici_logo
        "STANCHART" -> R.drawable.stanchart_logo
        "AXIS" -> R.drawable.axis_logo
        else -> null
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TransactionCard(
    item: UiItem.Transaction,
    categoryById: Map<String, CategoryEntity>,
    onEdit: (TransactionEntity) -> Unit
) {
    val msg = item.data
    val context = LocalContext.current
    val classification = categoryById[msg.txnClass]
    val isUserCreated = msg.message.equals(AppText.USER_CREATED, ignoreCase = true)
    val cardShape = RoundedCornerShape(12.dp)
    val cardContainer = txnCardBg()
    val amountColor = if (msg.txn.equals(AppText.CREDIT, ignoreCase = true)) TxnCreditAmount else debitAmountColor()
    val txnTextColor = appTextColor()
    val bankName = msg.bank.trim()
    val bankLogoRef = msg.bankLogo.trim()
    val bankLogo = if (bankLogoRef.isNotBlank()) {
        context.resources.getIdentifier(bankLogoRef, "drawable", context.packageName).takeIf { it != 0 }
    } else {
        legacyBankLogoRes(bankName)
    }
    val categoryName = classification?.name ?: AppText.MISC
    val subcategoryName = classification?.subcategory ?: AppText.UNCAT

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onEdit(msg) },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = cardContainer)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (msg.txn.equals(AppText.CREDIT, ignoreCase = true)) {
                            Icons.Filled.CallReceived
                        } else {
                            Icons.Filled.CallMade
                        },
                        contentDescription = null,
                        tint = amountColor,
                        modifier = Modifier.height(32.dp)
                    )
                    if (bankLogo != null) {
                        Image(
                            painter = painterResource(id = bankLogo),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.height(18.dp)
                        )
                    } else if (isUserCreated) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = null,
                            tint = txnTextColor,
                            modifier = Modifier.height(18.dp)
                        )
                    }
                    Icon(
                        imageVector = categoryIcon(categoryName),
                        contentDescription = null,
                        tint = txnTextColor,
                        modifier = Modifier.height(18.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatCurrency(parseAmountValue(msg.amount) ?: BigDecimal.ZERO),
                    style = MaterialTheme.typography.headlineSmall,
                    color = amountColor,
                    textAlign = TextAlign.End
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = categoryName,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = txnTextColor
                    )
                }
                Card(
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Text(
                        text = subcategoryName,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = txnTextColor
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    subtitle: String,
    debitTotal: BigDecimal,
    creditTotal: BigDecimal
) {
    val isDaily = title == AppText.DAILY_SUMMARY
    val isMonthly = title == AppText.MONTHLY_SUMMARY
    val mode = appThemeMode()
    val containerColor = if (isMonthly) {
        when (mode) {
            ThemeMode.LIGHT -> Color(0xFFF29F05)
            ThemeMode.DARK -> DarkSurface
            ThemeMode.MIDNIGHT -> Midnight3
        }
    } else {
        when (mode) {
            ThemeMode.LIGHT -> Color(0xFFFAD3B5)
            ThemeMode.DARK -> Color(0xFF262626)
            ThemeMode.MIDNIGHT -> Color(0xFF3A2E43)
        }
    }
    val textColor = if (isMonthly) {
        when (mode) {
            ThemeMode.LIGHT -> Color.Black
            ThemeMode.DARK -> Vaporwave2
            ThemeMode.MIDNIGHT -> Midnight1
        }
    } else {
        when (mode) {
            ThemeMode.LIGHT -> Color.Black
            ThemeMode.DARK -> DarkText
            ThemeMode.MIDNIGHT -> Midnight1
        }
    }
    val monthlySentMore = debitTotal > creditTotal
    val monthlyBorderColor = if (monthlySentMore) monthlySentBorderColor() else monthlyReceivedBorderColor()
    val monthlyShadowColor = if (monthlySentMore) monthlySentShadowColor() else monthlyReceivedShadowColor()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .then(
                if (isDaily) {
                    Modifier.shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = dailySummaryShadowColor(),
                        spotColor = dailySummaryShadowColor()
                    )
                } else if (isMonthly) {
                    Modifier.shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = monthlyShadowColor,
                        spotColor = monthlyShadowColor
                    )
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        border = when {
            isDaily -> null
            isMonthly -> null
            else -> null
        },
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isMonthly) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Normal,
                        fontFamily = RobotoCondensedFamily
                    ),
                    color = textColor,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Normal,
                        fontFamily = RobotoCondensedFamily
                    ),
                    color = textColor
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    imageVector = Icons.Filled.NorthEast,
                    contentDescription = null,
                    tint = debitAmountColor()
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatCurrency(debitTotal),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                    color = textColor,
                    textAlign = TextAlign.End
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    imageVector = Icons.Filled.SouthWest,
                    contentDescription = null,
                    tint = TxnCreditAmount
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatCurrency(creditTotal),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp),
                    color = textColor,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
fun DayHeaderCard(date: LocalDate) {
    Text(
        text = date.format(DateTimeFormatter.ofPattern(AppText.DATE_FMT_DAY)),
        style = MaterialTheme.typography.titleMedium,
        color = appTextColorLocal(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp)
    )
}

@Composable
fun HeaderBar(
    onMenuToggle: () -> Unit,
    onTitleTap: () -> Unit,
) {
    val headerTextColor = Vaporwave2
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(appHeaderBg())
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.clickable { onTitleTap() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Box(
                    modifier = Modifier.padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.finr_logo),
                        contentDescription = AppText.LOGO_DESC,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
            Text(
                text = AppText.APP_NAME,
                style = MaterialTheme.typography.headlineSmall,
                color = headerTextColor
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onMenuToggle) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = AppText.MENU_DESC,
                tint = headerTextColor
            )
        }
    }
}

@Composable
fun SlideOutMenu(
    visible: Boolean,
    secureEnabled: Boolean,
    themeMode: ThemeMode,
    onSecureToggle: (Boolean) -> Unit,
    onThemeClick: () -> Unit,
    onAboutClick: () -> Unit,
    onRecycleClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(180)) + slideInHorizontally(
            animationSpec = tween(220),
            initialOffsetX = { it }
        ),
        exit = fadeOut(animationSpec = tween(160)) + slideOutHorizontally(
            animationSpec = tween(200),
            targetOffsetX = { it }
        )
    ) {
        val panelShape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
        val panelBounds = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.82f)
            .statusBarsPadding()
            .padding(top = 14.dp)
        val glassBase = Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            )
        )
        val glassHighlight = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.20f),
                Color.White.copy(alpha = 0.05f)
            )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onDismiss() }
        ) {
            Box(
                modifier = panelBounds
                    .align(Alignment.CenterEnd)
                    .background(
                        brush = glassBase,
                        shape = panelShape
                    )
            )
            Box(
                modifier = panelBounds
                    .align(Alignment.CenterEnd)
                    .padding(1.dp)
                    .background(
                        brush = glassHighlight,
                        shape = panelShape
                    )
            )
            Column(
                modifier = panelBounds
                    .align(Alignment.CenterEnd)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.64f),
                        panelShape
                    )
                    .border(
                        width = 1.2.dp,
                        color = Color.White.copy(alpha = 0.34f),
                        shape = panelShape
                    )
                    .padding(horizontal = 20.dp, vertical = 36.dp)
                    .clickable(enabled = false) {},
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                MenuItemRow(
                    icon = when (themeMode) {
                        ThemeMode.LIGHT -> Icons.Filled.LightMode
                        ThemeMode.DARK -> Icons.Filled.DarkMode
                        ThemeMode.MIDNIGHT -> Icons.Filled.DarkMode
                    },
                    label = AppText.THEME,
                    onClick = onThemeClick
                )
                MenuItemRow(
                    icon = Icons.Filled.Info,
                    label = AppText.ABOUT,
                    onClick = onAboutClick
                )
                MenuItemRow(
                    icon = Icons.Filled.Autorenew,
                    label = AppText.RECYCLE,
                    onClick = onRecycleClick
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.height(30.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = AppText.SECURE,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Switch(checked = secureEnabled, onCheckedChange = onSecureToggle)
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.pm_logo),
                        contentDescription = null,
                        modifier = Modifier.height(36.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppText.SPLASH_POWERED_BY,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuItemRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.height(30.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun FloatingDock(
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
    onCalendar: () -> Unit,
    onExport: () -> Unit
) {
    // Dock keeps existing footprint, but each action floats in its own circular button.
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 16.dp, start = 20.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(appHeaderBg())
                .padding(8.dp)
        ) {
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.AddCircle,
                    contentDescription = AppText.ADD_TXN_DESC,
                    tint = Vaporwave7
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(appHeaderBg())
                .padding(8.dp)
        ) {
            IconButton(onClick = onCalendar) {
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = AppText.CALENDAR_DESC,
                    tint = Vaporwave7
                )
            }
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(appHeaderBg())
                .padding(8.dp)
        ) {
            IconButton(onClick = onExport) {
                Icon(
                    imageVector = Icons.Filled.FileUpload,
                    contentDescription = AppText.EXPORT_DESC,
                    tint = Vaporwave7
                )
            }
        }
    }
}

@Composable
fun DropdownField(
    label: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    // Read-only field + parent click provides reliable selector trigger in Compose forms.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}
