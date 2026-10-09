package com.clarklevis.dsh.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clarklevis.dsh.android.R
import com.clarklevis.dsh.shared.protocol.GatewayPendingQuestionRequest
import com.clarklevis.dsh.shared.protocol.GatewayQuestion
import com.clarklevis.dsh.shared.protocol.GatewayQuestionAnswer
import com.clarklevis.dsh.shared.protocol.GatewayQuestionOption
import com.clarklevis.dsh.shared.projection.ConversationItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * 时间线内联的 `ask_user_question` 卡片。
 *
 * 与 composer 位的 [HumanQuestionCard] 的区别：本卡片常驻对话流中（不随作答消失），
 * 未作答时可交互、已作答后转为只读并展示所选答案，因此历史回放也能看到当时问了什么。
 *
 * 约束：
 * - **整批提交**：answers 的 id 与顺序必须与 `questions` 一致（见 `QuestionReducer.validate`）。
 * - `selected` 必须是 `options[].label` 原文，不能是归一化后的展示标题。
 */
@Composable
internal fun AskQuestionToolCard(
    request: GatewayPendingQuestionRequest?,
    callArguments: String,
    result: ConversationItem?,
    isSubmitting: Boolean,
    onAnswer: (List<GatewayQuestionAnswer>) -> Unit,
    onCancel: () -> Unit
) {
    // 未关联到 pending 请求时只用 tool_call 参数渲染只读预览（历史行 / 断线期间）。
    val questions = request?.questions ?: remember(callArguments) { parseQuestions(callArguments) }
    val answered = result != null
    val rowKey = request?.rpcId ?: callArguments

    var currentIndex by remember(rowKey) { mutableIntStateOf(0) }
    val selections = remember(rowKey) { mutableStateMapOf<String, Set<String>>() }
    val customAnswers = remember(rowKey) { mutableStateMapOf<String, String>() }
    var validationMessage by remember(rowKey) { mutableStateOf<String?>(null) }
    val palette = dshPalette()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface.copy(alpha = 0.97f))
            .border(
                0.8.dp,
                palette.cardBorder,
                RoundedCornerShape(18.dp)
            )
            .padding(vertical = 13.dp)
            .testTag("ask-question-card")
    ) {
        QuestionCardHeader(
            questionCount = questions.size,
            answerable = request != null && !answered && !isSubmitting,
            answered = answered,
            onCancel = onCancel
        )
        HorizontalDivider(color = palette.divider)

        if (questions.isEmpty()) {
            Text(
                "无法解析这道提问的参数。",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                modifier = Modifier.padding(horizontal = 17.dp, vertical = 14.dp)
            )
            return@Column
        }

        val question = questions[currentIndex.coerceIn(0, questions.lastIndex)]
        Column(
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (questions.size > 1) {
                Text(
                    "第 ${currentIndex + 1} / ${questions.size} 题",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
            QuestionHeading(question)

            if (answered) {
                SettledAnswers(result = result)
            } else if (request == null) {
                Text(
                    "该提问已不在待答状态，无法在应用内作答。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
                )
            } else {
                AnswerControls(
                    question = question,
                    selections = selections[question.id].orEmpty(),
                    customAnswer = customAnswers[question.id].orEmpty(),
                    onSelectionChange = { updated ->
                        selections[question.id] = updated
                        if (updated.isNotEmpty()) customAnswers[question.id] = ""
                        validationMessage = null
                    },
                    onCustomAnswerChange = { value ->
                        customAnswers[question.id] = value
                        if (!question.allowsMultipleSelections && value.isNotBlank()) {
                            selections[question.id] = emptySet()
                        }
                        validationMessage = null
                    }
                )
                validationMessage?.let { message ->
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                QuestionCardFooter(
                    isLast = currentIndex >= questions.lastIndex,
                    isSubmitting = isSubmitting,
                    onNext = {
                        if (!answered(question, selections, customAnswers)) {
                            validationMessage = "请选择一个选项或填写自定义答案。"
                            return@QuestionCardFooter
                        }
                        currentIndex++
                        validationMessage = null
                    },
                    onSubmit = {
                        val firstMissing = questions.indexOfFirst {
                            !answered(it, selections, customAnswers)
                        }
                        if (firstMissing >= 0) {
                            currentIndex = firstMissing
                            validationMessage = "请先完成这道问题。"
                            return@QuestionCardFooter
                        }
                        // 整批提交；selected 必须是 label 原文。
                        onAnswer(
                            questions.map { item ->
                                GatewayQuestionAnswer(
                                    id = item.id,
                                    selected = selections[item.id].orEmpty().toList(),
                                    custom = customAnswers[item.id]?.takeIf(String::isNotBlank)
                                )
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun QuestionCardHeader(
    questionCount: Int,
    answerable: Boolean,
    answered: Boolean,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 17.dp)
            .padding(bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            painterResource(R.drawable.ic_question_bubble),
            contentDescription = null,
            tint = DshColors.Ocean,
            modifier = Modifier.size(17.dp)
        )
        Text(
            when {
                answered -> "提问 · 已作答"
                answerable -> if (questionCount > 1) "需要你的回答（$questionCount 题）" else "需要你的回答"
                else -> "提问 · 等待中"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.weight(1f))
        if (answerable) {
            Text(
                "跳过",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f),
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 9.dp, vertical = 5.dp)
                    .testTag("ask-question-skip")
            )
        }
    }
}

@Composable
private fun QuestionCardFooter(
    isLast: Boolean,
    isSubmitting: Boolean,
    onNext: () -> Unit,
    onSubmit: () -> Unit
) {
    val palette = dshPalette()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        val action = if (isLast) onSubmit else onNext
        val label = when {
            isSubmitting -> "提交中…"
            isLast -> "提交回答"
            else -> "下一题"
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(if (isSubmitting) palette.primary.copy(alpha = 0.45f) else palette.primary)
                .clickable(enabled = !isSubmitting, onClick = action)
                .testTag(if (isLast) "ask-question-submit" else "ask-question-next"),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                color = palette.onPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** 已作答（或历史回放）时展示结果文本；投影把答案写进 tool/result 的文本。 */
@Composable
private fun SettledAnswers(result: ConversationItem) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "已提交的回答",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
        )
        val text = result.text.takeIf(String::isNotBlank) ?: result.title
        Text(
            text,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = if (result.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 从 `tool/call` 的原始 JSON 参数解析 questions。解析失败返回空列表（不产生可交互卡片），
 * 与 harness `questionsOf` 的容错一致。兼容 snake_case 的 `multi_select`。
 */
internal fun parseQuestions(arguments: String): List<GatewayQuestion> {
    val parsed = runCatching { wireJsonLenient.parseToJsonElement(arguments) }.getOrNull() as? JsonObject
        ?: return emptyList()
    val array = parsed["questions"] as? JsonArray ?: return emptyList()
    return array.mapIndexedNotNull { index, element ->
        val obj = element as? JsonObject ?: return@mapIndexedNotNull null
        val text = (obj["question"] as? JsonPrimitive)?.contentOrNull
            ?: (obj["prompt"] as? JsonPrimitive)?.contentOrNull
            ?: (obj["header"] as? JsonPrimitive)?.contentOrNull
            ?: return@mapIndexedNotNull null
        val options = (obj["options"] as? JsonArray)?.mapNotNull { option ->
            when (option) {
                is JsonPrimitive -> option.contentOrNull?.let { GatewayQuestionOption(label = it) }
                is JsonObject -> (option["label"] as? JsonPrimitive)?.contentOrNull?.let {
                    GatewayQuestionOption(
                        label = it,
                        description = (option["description"] as? JsonPrimitive)?.contentOrNull
                    )
                }
                else -> null
            }
        }
        val multiSelect = (obj["multiSelect"] as? JsonPrimitive)?.booleanOrNull
            ?: (obj["multi_select"] as? JsonPrimitive)?.booleanOrNull
        GatewayQuestion(
            id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: "q_${index + 1}",
            header = (obj["header"] as? JsonPrimitive)?.contentOrNull,
            question = text,
            detail = (obj["detail"] as? JsonPrimitive)?.contentOrNull,
            options = options,
            multiSelect = multiSelect
        )
    }
}

/**
 * 把提问帧与时间线里的 `tool/call` 关联起来。
 *
 * 网关的 `question-requested` 帧**不带 `callId`**（审批帧才带），因此只能按
 * 「题目 id 集合」join；两者来自同一份模型参数，可稳定比中。`id` 缺失时回落题目文本。
 */
internal fun matchesQuestionCall(
    requestQuestions: List<GatewayQuestion>,
    callArguments: String
): Boolean {
    val callQuestions = parseQuestions(callArguments)
    if (callQuestions.isEmpty() || requestQuestions.isEmpty()) return false
    val requestIds = requestQuestions.map { it.id }.filter(String::isNotBlank).toSet()
    val callIds = callQuestions.map { it.id }.filter(String::isNotBlank).toSet()
    if (requestIds.isNotEmpty() && callIds.isNotEmpty()) {
        return requestIds.intersect(callIds).isNotEmpty()
    }
    val requestTexts = requestQuestions.map { it.question }.toSet()
    return callQuestions.any { it.question in requestTexts }
}

/** 内联卡片不做协议校验，使用宽松解析以避免因未知字段丢整行。 */
private val wireJsonLenient = Json { ignoreUnknownKeys = true; isLenient = true }
