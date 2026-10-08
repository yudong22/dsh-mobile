package com.clarklevis.dsh.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 应用统一的确认弹窗，视觉与设置页保持一致。
 *
 * [content] 用于承载输入框等额外内容；按钮为空时不会渲染按钮区域。
 * [confirmDestructive] 把确认按钮渲染为错误色——用于删除一类不可撤销的操作，
 * 避免用主色实心把破坏性动作暗示成推荐的常规操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DshAlertDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    dismissLabel: String? = null,
    onDismissClick: (() -> Unit)? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    confirmDestructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier.fillMaxWidth().widthIn(max = 340.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 8.dp,
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                message?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.56f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
                content?.invoke(this)
                if (dismissLabel != null || confirmLabel != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        dismissLabel?.let { label ->
                            DshAlertDialogButton(
                                label = label,
                                modifier = if (confirmLabel == null) Modifier.fillMaxWidth() else Modifier.weight(1f),
                                onClick = onDismissClick ?: onDismissRequest
                            )
                        }
                        confirmLabel?.let { label ->
                            DshAlertDialogButton(
                                label = label,
                                modifier = if (dismissLabel == null) Modifier.fillMaxWidth() else Modifier.weight(1f),
                                enabled = confirmEnabled,
                                // 确认动作是对话框的主操作，用实心与「取消/好」区分开。
                                primary = true,
                                destructive = confirmDestructive,
                                onClick = onConfirm ?: onDismissRequest
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DshAlertDialogButton(
    label: String,
    modifier: Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    // 主次分级：此前两个按钮都取 onSurface 8% 底，视觉上完全一样，
    // 用户看不出「好」与「重新连接」的区别。主按钮改为实心填充：
    // 破坏性操作用错误色，其余用主题主色。
    val colorScheme = MaterialTheme.colorScheme
    val buttonColor = when {
        !enabled -> colorScheme.onSurface.copy(alpha = 0.04f)
        destructive -> colorScheme.error
        primary -> colorScheme.primary
        else -> colorScheme.onSurface.copy(alpha = 0.08f)
    }
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(buttonColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> colorScheme.onSurface.copy(alpha = 0.38f)
                destructive || primary -> colorScheme.onPrimary
                else -> colorScheme.onSurface.copy(alpha = 1f)
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
