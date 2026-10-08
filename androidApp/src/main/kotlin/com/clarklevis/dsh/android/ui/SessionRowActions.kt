package com.clarklevis.dsh.android.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.clarklevis.dsh.shared.domain.SessionSummary

/**
 * 会话行的交互外壳：点击打开、长按弹出重命名/归档菜单，并承载两个对话框。
 *
 * 抽屉的「任务」列表与首页的「最近活跃会话」共用这一层，避免两处各写一份菜单与对话框
 * （重命名/归档的文案与语义必须一致，之前只存在于抽屉里）。
 *
 * 可点击区域包含 [contentPadding]：`combinedClickable` 在 `padding` 之外，整行都能命中，
 * 与抽屉原先的写法一致。
 */
@Composable
internal fun SessionRowActions(
    session: SessionSummary,
    onClick: () -> Unit,
    onRename: (String, String) -> Unit,
    onArchive: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 30.dp, vertical = 13.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    content: @Composable RowScope.() -> Unit
) {
    var menuVisible by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var archiving by remember { mutableStateOf(false) }
    // 以会话 id 为 key：行在同一条列表里可能被复用到另一个会话上（尤其是首页的非 lazy 列表），
    // 不设 key 会把上一个会话的标题带进下一个会话的重命名输入框。
    var title by remember(session.id) { mutableStateOf(session.title) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = { menuVisible = true }
            )
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement
    ) {
        content()
    }
    SessionContextMenu(
        expanded = menuVisible,
        onDismissRequest = { menuVisible = false },
        onRename = {
            title = session.title
            menuVisible = false
            renaming = true
        },
        onArchive = {
            menuVisible = false
            archiving = true
        }
    )
    if (renaming) AlertDialog(
        onDismissRequest = { renaming = false },
        title = { Text("重命名会话") },
        text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = {
                onRename(session.id, title)
                renaming = false
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = { renaming = false }) { Text("取消") } }
    )
    if (archiving) AlertDialog(
        onDismissRequest = { archiving = false },
        title = { Text("删除会话？") },
        text = { Text("会话将被归档并从列表隐藏，历史记录会保留。") },
        confirmButton = {
            TextButton(onClick = { onArchive(session.id); archiving = false }) { Text("删除") }
        },
        dismissButton = { TextButton(onClick = { archiving = false }) { Text("取消") } }
    )
}
