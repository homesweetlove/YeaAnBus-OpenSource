package kr.yeaan.bus.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.yeaan.bus.algorithm.ArrivalText
import kr.yeaan.bus.data.model.ArrivalDisplay
import kr.yeaan.bus.data.model.Stop
import kr.yeaan.bus.ui.theme.Board
import kr.yeaan.bus.ui.theme.Ink
import kr.yeaan.bus.ui.theme.Line
import kr.yeaan.bus.ui.theme.Muted
import kr.yeaan.bus.ui.theme.Navy
import kotlin.math.ceil

private val Tabular = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun SectionTitle(title: String, trailing: String? = null, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (trailing != null) {
            if (action != null) TextButton(onClick = action) { Text(trailing) }
            else Text(trailing, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
    }
}

@Composable
fun EmptyState(title: String, detail: String, icon: ImageVector = Icons.Outlined.DirectionsBus) {
    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Navy, modifier = Modifier.size(52.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)).padding(13.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun Notice(text: String, error: Boolean = false) {
    val shape = MaterialTheme.shapes.small
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(shape)
        .background(if (error) MaterialTheme.colorScheme.errorContainer.copy(alpha = .35f) else MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = if (error) MaterialTheme.colorScheme.error else Muted)
        Spacer(Modifier.width(9.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (error) MaterialTheme.colorScheme.error else Muted)
    }
}

/** Corner shape of one row in a list that reads as a single rounded group. */
fun groupShape(index: Int, count: Int, radius: Dp = 20.dp): Shape {
    val top = if (index <= 0) radius else 0.dp
    val bottom = if (index >= count - 1) radius else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** A plain surface group for settings and short lists. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface), content = content)
}

/** The dark arrival board: the one loud element of the app. */
@Composable
fun BoardPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    Column(modifier.fillMaxWidth().clip(shape).background(Board.panel).border(1.dp, Board.line, shape)
        .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 16.dp), content = content)
}

enum class PlateSize(val minWidth: Dp, val minHeight: Dp, val font: Int, val corner: Dp) {
    SMALL(46.dp, 28.dp, 15, 8.dp), MEDIUM(56.dp, 36.dp, 20, 10.dp), LARGE(76.dp, 48.dp, 28, 12.dp)
}

/** Route number drawn like the number plate on the front of a bus. */
@Composable
fun RoutePlate(number: String, size: PlateSize = PlateSize.MEDIUM, onBoard: Boolean = false, quiet: Boolean = false) {
    val shape = RoundedCornerShape(size.corner)
    val fill = if (quiet) Color.Transparent else if (onBoard) Board.plate else MaterialTheme.colorScheme.primary
    val content = if (quiet) Muted else if (onBoard) Board.onPlate else MaterialTheme.colorScheme.onPrimary
    val outline = if (quiet) Modifier.border(1.5.dp, Line, shape) else Modifier
    Box(Modifier.widthIn(min = size.minWidth, max = 132.dp).heightIn(min = size.minHeight)
        .background(fill, shape).then(outline).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(number, color = content, fontSize = size.font.sp, lineHeight = (size.font + 6).sp, fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, style = Tabular)
    }
}

/** Minutes as a large numeral, "곧 도착" as the amber signal, anything else as a quiet label. */
@Composable
fun EtaValue(minutes: Int?, soon: Boolean, label: String?, big: Boolean, color: Color, labelColor: Color, modifier: Modifier = Modifier) {
    if (soon) {
        Text("곧 도착", modifier.background(Board.signal, RoundedCornerShape(50))
            .padding(horizontal = if (big) 14.dp else 10.dp, vertical = if (big) 8.dp else 5.dp),
            color = Board.onSignal, fontWeight = FontWeight.Bold, fontSize = if (big) 20.sp else 15.sp,
            lineHeight = if (big) 26.sp else 20.sp, maxLines = 1)
    } else if (minutes != null) {
        Row(modifier) {
            Text("$minutes", Modifier.alignByBaseline(), color = color, fontSize = if (big) 52.sp else 24.sp,
                lineHeight = if (big) 56.sp else 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, maxLines = 1, style = Tabular)
            Text("분", Modifier.alignByBaseline().padding(start = 2.dp), color = color, fontSize = if (big) 18.sp else 14.sp,
                lineHeight = if (big) 24.sp else 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    } else {
        Text(label ?: "정보 없음", modifier, color = labelColor, style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EtaText(seconds: Int?, stale: Boolean, big: Boolean, color: Color, labelColor: Color, modifier: Modifier = Modifier) {
    val live = !stale && seconds != null
    val soon = live && seconds!! <= 35
    val minutes = if (live && !soon) ceil(seconds!! / 60.0).toInt() else null
    EtaValue(minutes, soon, if (stale) "갱신 대기" else "정보 없음", big, color, labelColor, modifier)
}

/** 0 = still far away, 1 = at the stop. Null when neither distance nor stop count is known. */
fun approachProgress(stopsAway: Int?, distanceMeters: Double?): Float? = when {
    distanceMeters != null && distanceMeters.isFinite() -> (1.0 - (distanceMeters / 3000.0).coerceIn(0.0, 1.0)).toFloat()
    stopsAway != null -> 1f - stopsAway.coerceIn(0, 8) / 8f
    else -> null
}

/** A bus closing in on the stop, like the route strip above the doors of a bus. Decorative. */
@Composable
fun ApproachStrip(progress: Float, track: Color, busColor: Color, onBus: Color, stopColor: Color, modifier: Modifier = Modifier) {
    val position = progress.coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(28.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val y = size.height / 2
            val end = size.width - 7.dp.toPx()
            drawLine(track, Offset(0f, y), Offset(end, y), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            for (tick in 1..4) drawCircle(track, 3.dp.toPx(), Offset(end * tick / 5f, y))
            drawCircle(stopColor, 5.dp.toPx(), Offset(end, y), style = Stroke(4.dp.toPx()))
        }
        Box(Modifier.align(BiasAlignment(-1f + 1.72f * position, 0f)).size(28.dp)
            .background(busColor, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.DirectionsBus, null, tint = onBus, modifier = Modifier.size(18.dp))
        }
    }
}

fun arrivalNote(row: ArrivalDisplay): String =
    if (row.distanceMeters != null) ArrivalText.distance(row.distanceMeters)
    else row.arrival.stopsAway?.let { "${it}개 정류장 전" } ?: row.arrival.status.ifBlank { "운행 정보 대기" }

fun arrivalDirection(row: ArrivalDisplay): String =
    row.arrival.route.to.takeIf { it.isNotBlank() }?.let { "$it 방면" } ?: "시내버스"

@Composable
fun StopRow(stop: Stop, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit, subtitle: String? = null,
            index: Int = 0, count: Int = 1) {
    Column(Modifier.fillMaxWidth().clip(groupShape(index, count)).background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocationOn, null, tint = Navy, modifier = Modifier.size(36.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)).padding(8.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stop.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle ?: "${stop.city.label} · ${stop.code.ifBlank { stop.id }}", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            IconButton(onClick = onFavorite) {
                Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    if (favorite) "${stop.name} 즐겨찾기 해제" else "${stop.name} 즐겨찾기 추가",
                    tint = if (favorite) Navy else Muted)
            }
        }
        if (index < count - 1) HorizontalDivider(Modifier.padding(start = 62.dp), color = Line)
    }
}

@Composable
fun ArrivalRow(row: ArrivalDisplay, index: Int = 0, count: Int = 1, inset: Boolean = true, onClick: () -> Unit) {
    val waiting = row.seconds == null
    Column(Modifier.fillMaxWidth().clip(groupShape(index, count)).background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "지도에서 보기", onClick = onClick).heightIn(min = 68.dp)
            .padding(horizontal = if (inset) 16.dp else 0.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            RoutePlate(row.arrival.route.number, PlateSize.MEDIUM, quiet = waiting)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(arrivalDirection(row), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(arrivalNote(row), style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!waiting || row.stale) {
                Spacer(Modifier.width(10.dp))
                EtaText(row.seconds, row.stale, big = false, color = Ink, labelColor = Muted)
            }
        }
        if (index < count - 1) HorizontalDivider(Modifier.padding(start = if (inset) 16.dp else 0.dp), color = Line)
    }
}

/** One choice out of a few, shown side by side. Each option is a radio button for screen readers. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .padding(4.dp).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { option ->
            val on = option == selected
            Box(Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                .background(if (on) Board.thumb else Color.Transparent)
                .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(option) })
                .padding(horizontal = 6.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(label(option), style = MaterialTheme.typography.labelLarge, color = if (on) Ink else Muted,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
