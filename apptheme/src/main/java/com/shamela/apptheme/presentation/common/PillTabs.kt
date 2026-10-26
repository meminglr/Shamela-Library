package com.shamela.apptheme.presentation.common

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The app's tabs: a rounded track (a different fill from the page) holding icon + label tabs, and
 * one pill that marks the selected tab. Between two tabs the pill stretches: its leading edge
 * reaches the next tab first, then the trailing edge catches up. [position] (e.g. a pager's page
 * plus its swipe fraction) makes it follow the finger; without it, it springs to [selectedIndex].
 */
@Composable
fun PillTabs(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?> = emptyList(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    position: Float? = null,
) {
    val animated by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "pill position",
    )
    val current = (position ?: animated).coerceIn(0f, (labels.size - 1).coerceAtLeast(0).toFloat())
    // Measured left edge and width of every tab, in px, relative to the row of tabs.
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val density = LocalDensity.current

    Row(modifier.padding(contentPadding)) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .horizontalScroll(rememberScrollState())
                .padding(4.dp)
        ) {
            // The pill, behind the labels.
            val from = floor(current).toInt()
            val to = (from + 1).coerceAtMost(labels.lastIndex)
            val fraction = current - from
            val a = bounds[from]
            val b = bounds[to] ?: a
            if (a != null && b != null) {
                val forward = b.first >= a.first
                // First half: the edge facing the next tab moves; second half: the other edge.
                val lead = (fraction * 2f).coerceIn(0f, 1f)
                val trail = (fraction * 2f - 1f).coerceIn(0f, 1f)
                val aStart = a.first
                val aEnd = a.first + a.second
                val bStart = b.first
                val bEnd = b.first + b.second
                val start = if (forward) aStart + (bStart - aStart) * trail else aStart + (bStart - aStart) * lead
                val end = if (forward) aEnd + (bEnd - aEnd) * lead else aEnd + (bEnd - aEnd) * trail
                Box(
                    Modifier
                        // Measured from the left in both layout directions, so place it absolutely.
                        .align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset { IntOffset(start.roundToInt(), 0) }
                        .width(with(density) { (end - start).coerceAtLeast(0f).toDp() })
                        .height(36.dp)
                        .shadow(2.dp, CircleShape)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                )
            }

            Row(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                labels.forEachIndexed { index, label ->
                    // Label color follows the pill: full accent under it, fading as it leaves.
                    val nearness = (1f - abs(current - index)).coerceIn(0f, 1f)
                    val content = lerp(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        MaterialTheme.colorScheme.primary,
                        nearness,
                    )
                    Row(
                        modifier = Modifier
                            .onPlaced { coordinates ->
                                bounds[index] = coordinates.positionInParent().x to coordinates.size.width.toFloat()
                            }
                            .height(36.dp)
                            .clip(CircleShape)
                            // Accent-tinted ripple: the default one (dark, from the text color)
                            // looks like a grey smudge on the white pill and the light track.
                            .selectable(
                                selected = index == selectedIndex,
                                interactionSource = null,
                                indication = ripple(color = MaterialTheme.colorScheme.primary),
                                role = Role.Tab,
                            ) { onSelect(index) }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        icons.getOrNull(index)?.let {
                            Icon(it, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = label,
                            color = content,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
