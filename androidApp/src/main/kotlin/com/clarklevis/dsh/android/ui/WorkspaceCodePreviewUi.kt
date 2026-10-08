package com.clarklevis.dsh.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.clarklevis.dsh.android.AndroidWorkspaceLocalFile
import com.clarklevis.dsh.android.R
import com.clarklevis.dsh.shared.facade.WorkspaceCodeDocument
import com.clarklevis.dsh.shared.facade.WorkspaceCodePreviewSupport
import com.clarklevis.dsh.shared.facade.WorkspaceCodeTokenKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_CODE_PREVIEW_BYTES = 2L * 1_024 * 1_024

private sealed interface CodePreviewLoadState {
    data object Loading : CodePreviewLoadState
    data class Ready(val document: WorkspaceCodeDocument) : CodePreviewLoadState
    data class Failed(val message: String) : CodePreviewLoadState
}

@Composable
internal fun WorkspaceCodePreviewDialog(
    file: AndroidWorkspaceLocalFile,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit
) {
    var loadState by remember(file.file) {
        mutableStateOf<CodePreviewLoadState>(CodePreviewLoadState.Loading)
    }
    LaunchedEffect(file.file) {
        loadState = withContext(Dispatchers.IO) {
            if (file.file.length() > MAX_CODE_PREVIEW_BYTES) {
                CodePreviewLoadState.Failed("文件超过 2 MB，请使用系统应用打开")
            } else {
                runCatching {
                    WorkspaceCodePreviewSupport.prepare(
                        source = file.file.readText(Charsets.UTF_8),
                        name = file.name,
                        mediaType = file.mediaType
                    )
                }.fold(
                    onSuccess = CodePreviewLoadState::Ready,
                    onFailure = { CodePreviewLoadState.Failed("读取代码失败：${it.localizedMessage}") }
                )
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                CodePreviewToolbar(
                    fileName = file.name,
                    onDismiss = onDismiss,
                    onOpenExternal = onOpenExternal
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                when (val state = loadState) {
                    CodePreviewLoadState.Loading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("正在准备代码预览…", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f))
                        }
                    }
                    is CodePreviewLoadState.Failed -> Box(
                        Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(state.message, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f))
                            TextButton(onClick = onOpenExternal) { Text("使用系统应用打开") }
                        }
                    }
                    is CodePreviewLoadState.Ready -> CodePreviewDocument(state.document)
                }
            }
        }
    }
}

@Composable
private fun CodePreviewToolbar(
    fileName: String,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "关闭代码预览",
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            fileName,
            modifier = Modifier.padding(start = 4.dp).weight(1f),
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        TextButton(onClick = onOpenExternal) {
            Icon(
                painter = painterResource(R.drawable.ic_open_external),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Text("系统打开", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/**
 * 一次高亮的完整结果：按行切好的 [lines] 与行号。
 *
 * 放在 IO 线程构建，而不是 `remember(document, dark) { highlightedCode(...) }`——
 * 后者会在 composition 里遍历全文与全部 token 拼一个 2MB 级的 AnnotatedString，
 * 主线程直接卡住。
 */
private class HighlightedDocument(
    val lines: List<AnnotatedString>,
    val gutter: List<String>
)

/**
 * 按行虚拟化，而不是把行号和代码各自塞进一个巨大的 `Text`。
 *
 * 原先两个 `Text` 承载整份 2MB 文本：排版、测量与绘制都要一次性处理全文，
 * 滚动前就会明显卡顿且占用大量内存。改为 LazyColumn 后只排版可见行。
 */
private fun highlightInBackground(document: WorkspaceCodeDocument, dark: Boolean): HighlightedDocument {
    val full = highlightedCode(document, dark)
    val text = document.text
    val lineStarts = ArrayList<Int>(document.lineCount + 1)
    lineStarts.add(0)
    var index = text.indexOf('\n')
    while (index >= 0) {
        lineStarts.add(index + 1)
        index = text.indexOf('\n', index + 1)
    }
    if (lineStarts.last() != text.length) lineStarts.add(text.length)
    val lines = ArrayList<AnnotatedString>(lineStarts.size - 1)
    for (i in 0 until lineStarts.size - 1) {
        val start = lineStarts[i]
        val end = lineStarts[i + 1].let { if (text.getOrNull(it - 1) == '\n') it - 1 else it }
        lines.add(if (end > start) full.subSequence(start, end) as AnnotatedString else AnnotatedString(""))
    }
    val gutter = (1..lines.size).map(Int::toString)
    return HighlightedDocument(lines, gutter)
}

@Composable
private fun CodePreviewDocument(document: WorkspaceCodeDocument) {
    val palette = dshPalette()
    val dark = palette.isDark
    val horizontalScroll = rememberScrollState()
    // 高亮在 IO 构建：composition 里做这件事会在打开大文件时卡住主线程。
    var highlighted by remember(document, dark) {
        mutableStateOf<HighlightedDocument?>(null)
    }
    LaunchedEffect(document, dark) {
        highlighted = withContext(Dispatchers.IO) { highlightInBackground(document, dark) }
    }
    val codeBackground = palette.canvas
    val gutterBackground = palette.surfaceMuted
    val secondary = palette.textSecondary

    Column(Modifier.fillMaxSize().background(codeBackground)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(gutterBackground)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                document.languageDisplayName,
                color = palette.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text("${document.lineCount} 行 · UTF-8", color = secondary, fontSize = 12.sp)
        }
        when (val ready = highlighted) {
            null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                state = rememberLazyListState()
            ) {
                items(ready.lines.size) { index ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = ready.gutter[index],
                            modifier = Modifier
                                .widthIn(min = 52.dp)
                                .background(gutterBackground)
                                .padding(start = 8.dp, end = 12.dp),
                            color = secondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.End
                        )
                        Text(
                            text = ready.lines[index],
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScroll)
                                .padding(start = 14.dp, end = 28.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 20.sp,
                            softWrap = false
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

private fun highlightedCode(document: WorkspaceCodeDocument, dark: Boolean): AnnotatedString =
    buildAnnotatedString {
        append(document.text)
        if (document.text.isNotEmpty()) {
            addStyle(
                SpanStyle(color = if (dark) Color(0xFFE6EDF3) else Color(0xFF1F2328)),
                0,
                document.text.length
            )
        }
        document.tokens.forEach { token ->
            if (token.start >= 0 && token.endExclusive <= document.text.length) {
                addStyle(SpanStyle(color = codeTokenColor(token.kind, dark)), token.start, token.endExclusive)
            }
        }
    }

private fun codeTokenColor(kind: WorkspaceCodeTokenKind, dark: Boolean): Color = when (kind) {
    WorkspaceCodeTokenKind.COMMENT -> if (dark) Color(0xFF8B949E) else Color(0xFF6E7781)
    WorkspaceCodeTokenKind.STRING -> if (dark) Color(0xFFA5D6FF) else Color(0xFF0A7A3D)
    WorkspaceCodeTokenKind.KEYWORD -> if (dark) Color(0xFFFF7B72) else Color(0xFF8250DF)
    WorkspaceCodeTokenKind.NUMBER -> if (dark) Color(0xFF79C0FF) else Color(0xFF0550AE)
    WorkspaceCodeTokenKind.TYPE -> if (dark) Color(0xFFD2A8FF) else Color(0xFF953800)
    WorkspaceCodeTokenKind.TAG -> if (dark) Color(0xFF7EE787) else Color(0xFFCF222E)
    WorkspaceCodeTokenKind.ATTRIBUTE -> if (dark) Color(0xFFFFA657) else Color(0xFF953800)
}
