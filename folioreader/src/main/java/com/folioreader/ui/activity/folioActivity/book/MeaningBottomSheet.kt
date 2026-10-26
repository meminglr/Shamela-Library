@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.folioreader.ui.activity.folioActivity.book

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.folioreader.R
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.shamela.apptheme.presentation.theme.AppFonts
import com.shamela.apptheme.presentation.theme.ShamelaIcons

/**
 * Bottom sheet with the AI explanation (see [MeaningApi]) of the text the reader selected,
 * and a field to ask follow-up questions about it. Driven entirely by [BookState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeaningBottomSheet(
    selection: String,
    messages: List<ChatMessage>,
    loading: Boolean,
    error: MeaningError?,
    onAsk: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Answers come in the app's language; a Turkish one has lines that start with an Arabic
    // word ("**التَّنظِير** (et-tanzîr)") which, left to the default, are laid out right-to-left
    // with their parts scrambled — so the conversation follows the answer language explicitly.
    val turkish = LocalContext.current.resources.configuration.locales[0].language == "tr"
    val direction = if (turkish) LayoutDirection.Ltr else LayoutDirection.Rtl
    val textDirection = if (turkish) TextDirection.Ltr else TextDirection.Rtl
    var question by rememberSaveable { mutableStateOf("") }
    val scroll = rememberScrollState()

    // Keep the newest message (or the spinner / error under it) in view.
    LaunchedEffect(messages.size, loading, error) { scroll.animateScrollTo(scroll.maxValue) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
        ) {
            // The selection is Arabic: right-aligned whatever the app language.
            Text(
                text = selection,
                style = AppFonts.textLargeBold.copy(textDirection = TextDirection.Rtl, textAlign = TextAlign.Right),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            Spacer(Modifier.height(12.dp))

            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    messages.forEach { message ->
                        if (message.fromUser) UserQuestion(message.text, textDirection)
                        else AnswerMarkdown(message.text, textDirection)
                    }
                    when {
                        loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }

                        error != null -> Column {
                            Text(
                                text = stringResource(
                                    when (error) {
                                        MeaningError.NOT_CONFIGURED -> R.string.meaning_not_configured
                                        MeaningError.NETWORK -> R.string.meaning_network_error
                                        MeaningError.SERVER -> R.string.meaning_server_error
                                    }
                                ),
                                style = AppFonts.textNormal,
                            )
                            if (error != MeaningError.NOT_CONFIGURED) {
                                TextButton(onClick = onRetry) { Text(stringResource(R.string.meaning_retry)) }
                            }
                        }
                    }
                }

                // Follow-ups only once there is an answer to ask about.
                if (messages.isNotEmpty()) {
                    val send = {
                        if (question.isNotBlank() && !loading) {
                            onAsk(question)
                            question = ""
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    QuestionField(
                        value = question,
                        onValueChange = { question = it },
                        canSend = question.isNotBlank() && !loading,
                        onSend = send,
                    )
                }
            }
        }
    }
}

/** An AI answer: Markdown (bold labels, lists) in the reader's text style. */
@Composable
private fun AnswerMarkdown(text: String, direction: TextDirection) {
    val body = AppFonts.textNormal.copy(textDirection = direction)
    val heading = AppFonts.textNormalBold.copy(textDirection = direction)
    SelectionContainer {
        Markdown(
            content = text,
            typography = markdownTypography(
                h1 = heading, h2 = heading, h3 = heading, h4 = heading, h5 = heading, h6 = heading,
                text = body, paragraph = body, ordered = body, bullet = body, list = body,
            ),
        )
    }
}

/** A follow-up question the reader asked, as a bubble on the trailing side. */
@Composable
private fun UserQuestion(text: String, direction: TextDirection) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text = text,
            style = AppFonts.textNormal.copy(textDirection = direction),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/**
 * The follow-up question field: a borderless, tinted pill with the send button inside it,
 * filled in the primary color once there is something to send.
 */
@Composable
private fun QuestionField(value: String, onValueChange: (String) -> Unit, canSend: Boolean, onSend: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = AppFonts.textNormal.copy(color = colors.onSurface),
        cursorBrush = SolidColor(colors.primary),
        maxLines = 4,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, capitalization = KeyboardCapitalization.Sentences),
        keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .background(colors.surfaceContainerHigh, RoundedCornerShape(28.dp))
                    .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.meaning_question_hint),
                            style = AppFonts.textNormal.copy(color = colors.onSurfaceVariant),
                        )
                    }
                    innerTextField()
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary,
                        disabledContainerColor = colors.onSurface.copy(alpha = 0.08f),
                        disabledContentColor = colors.onSurface.copy(alpha = 0.38f),
                    ),
                ) {
                    Icon(
                        ShamelaIcons.Send,
                        contentDescription = stringResource(R.string.meaning_send),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
    )
}
