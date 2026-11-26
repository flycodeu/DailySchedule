package com.flycode.dailyschedule.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.security.MessageDigest

private const val PIN_LENGTH = 4

fun hashPrivacyPin(pin: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("dailyschedule_pin_v1:$pin".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

@Composable
fun AppLockScreen(
    pinHash: String,
    onUnlocked: () -> Unit
) {
    var digits by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "日常计划",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = errorText ?: "请输入 4 位隐私密码解锁",
                style = MaterialTheme.typography.bodyMedium,
                color = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            PinDotsRow(filledCount = digits.length)
            Spacer(Modifier.height(36.dp))
            PinNumberPad(
                onDigit = { d ->
                    if (digits.length < PIN_LENGTH) {
                        val next = digits + d
                        errorText = null
                        if (next.length == PIN_LENGTH) {
                            if (hashPrivacyPin(next) == pinHash) {
                                digits = ""
                                onUnlocked()
                            } else {
                                digits = ""
                                errorText = "密码不正确，请重试"
                            }
                        } else {
                            digits = next
                        }
                    }
                },
                onBackspace = {
                    if (digits.isNotEmpty()) digits = digits.dropLast(1)
                }
            )
        }
    }
}

@Composable
fun PinVerifyDialog(
    title: String,
    pinHash: String,
    onDismiss: () -> Unit,
    onVerified: () -> Unit
) {
    var digits by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = errorText ?: "请输入 4 位数字密码",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                PinDotsRow(filledCount = digits.length)
                Spacer(Modifier.height(20.dp))
                PinNumberPad(
                    compact = true,
                    onDigit = { d ->
                        if (digits.length < PIN_LENGTH) {
                            val next = digits + d
                            errorText = null
                            if (next.length == PIN_LENGTH) {
                                if (hashPrivacyPin(next) == pinHash) {
                                    onVerified()
                                } else {
                                    digits = ""
                                    errorText = "密码不正确"
                                }
                            } else {
                                digits = next
                            }
                        }
                    },
                    onBackspace = {
                        if (digits.isNotEmpty()) digits = digits.dropLast(1)
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
fun SetPinDialog(
    hasExistingPin: Boolean,
    currentPinHash: String,
    onDismiss: () -> Unit,
    onSavePinHash: (String) -> Unit,
    onClearPin: () -> Unit
) {
    // Steps: 0 = verify old (if exists), 1 = enter new, 2 = confirm new
    var step by remember { mutableStateOf(if (hasExistingPin) 0 else 1) }
    var firstNewPin by remember { mutableStateOf("") }
    var digits by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val prompt = when (step) {
        0 -> "请先验证当前 4 位密码"
        1 -> "请设置新的 4 位数字密码"
        else -> "请再次输入以确认密码"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasExistingPin) "修改或关闭隐私密码" else "设置隐私密码", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = errorText ?: prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (errorText != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                PinDotsRow(filledCount = digits.length)
                Spacer(Modifier.height(20.dp))
                PinNumberPad(
                    compact = true,
                    onDigit = { d ->
                        if (digits.length < PIN_LENGTH) {
                            val next = digits + d
                            errorText = null
                            if (next.length == PIN_LENGTH) {
                                when (step) {
                                    0 -> {
                                        if (hashPrivacyPin(next) == currentPinHash) {
                                            digits = ""
                                            step = 1
                                        } else {
                                            digits = ""
                                            errorText = "原密码不正确"
                                        }
                                    }
                                    1 -> {
                                        firstNewPin = next
                                        digits = ""
                                        step = 2
                                    }
                                    2 -> {
                                        if (next == firstNewPin) {
                                            onSavePinHash(hashPrivacyPin(next))
                                        } else {
                                            digits = ""
                                            firstNewPin = ""
                                            step = 1
                                            errorText = "两次输入不一致，请重设"
                                        }
                                    }
                                }
                            } else {
                                digits = next
                            }
                        }
                    },
                    onBackspace = {
                        if (digits.isNotEmpty()) digits = digits.dropLast(1)
                    }
                )
            }
        },
        confirmButton = {
            if (hasExistingPin && step >= 1) {
                TextButton(onClick = onClearPin) {
                    Text("关闭密码", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun PinDotsRow(filledCount: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(PIN_LENGTH) { idx ->
            val filled = idx < filledCount
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.6.dp,
                        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun PinNumberPad(
    compact: Boolean = false,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val keySize = if (compact) 56.dp else 68.dp
    val spacing = if (compact) 12.dp else 18.dp
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )

    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                row.forEach { label ->
                    if (label.isEmpty()) {
                        Spacer(Modifier.size(keySize))
                    } else {
                        Surface(
                            modifier = Modifier
                                .size(keySize)
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (label == "⌫") onBackspace() else onDigit(label)
                                },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
