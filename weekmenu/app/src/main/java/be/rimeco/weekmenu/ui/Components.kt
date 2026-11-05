@file:OptIn(ExperimentalMaterial3Api::class)

package be.rimeco.weekmenu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import be.rimeco.weekmenu.data.Recipe
import coil.compose.AsyncImage

val StarColor = Color(0xFFF5A623)

@Composable
fun StarRating(rating: Int, modifier: Modifier = Modifier, size: Dp = 18.dp, onRate: ((Int) -> Unit)? = null) {
    Row(if (onRate != null) modifier.offset(x = (-8).dp) else modifier) {
        for (i in 1..5) {
            val tint = if (i <= rating) StarColor else MaterialTheme.colorScheme.outlineVariant
            val m = if (onRate != null) {
                Modifier.size(size + 16.dp).clickable { onRate(if (i == rating) 0 else i) }.padding(8.dp)
            } else Modifier.size(size)
            Icon(Icons.Filled.Star, contentDescription = "$i sterren", tint = tint, modifier = m)
        }
    }
}

@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    step: Int = 1,
    label: (Int) -> String = { it.toString() },
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { onChange((value - step).coerceIn(range)) },
            enabled = value > range.first,
            modifier = Modifier.size(32.dp),
        ) { Text("−", style = MaterialTheme.typography.titleMedium) }
        Text(label(value), modifier = Modifier.widthIn(min = 72.dp).padding(horizontal = 4.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        FilledTonalIconButton(
            onClick = { onChange((value + step).coerceIn(range)) },
            enabled = value < range.last,
            modifier = Modifier.size(32.dp),
        ) { Text("+", style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(onClick) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Terug") }
}

@Composable
fun RecipeThumb(recipe: Recipe, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val shape = RoundedCornerShape(12.dp)
    if (recipe.imageUrl.isNotBlank()) {
        AsyncImage(
            model = recipe.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        Box(modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Text(recipe.title.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
fun RecipeRow(recipe: Recipe, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RecipeThumb(recipe)
        Column(Modifier.weight(1f)) {
            Text(recipe.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                StarRating(recipe.rating, size = 14.dp)
                val extra = listOfNotNull(
                    recipe.minutes.takeIf { it > 0 }?.let { "$it min" },
                    recipe.tags.take(2).joinToString(", ").takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (extra.isNotBlank()) Text("  $extra", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Rij met tag-chips met drie standen: tik = tonen, nogmaals tikken = uitsluiten ("Geen …"), nogmaals = uit.
 * Is er een filter actief, dan staat vooraan een chip om alles te wissen.
 */
@Composable
fun TagFilterRow(
    tags: List<String>,
    included: Set<String>,
    excluded: Set<String>,
    onChange: (include: Set<String>, exclude: Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (included.isNotEmpty() || excluded.isNotEmpty()) {
            item {
                androidx.compose.material3.AssistChip(onClick = { onChange(emptySet(), emptySet()) }, label = { Text("Wis filters") })
            }
        }
        items(tags) { t ->
            val isExcluded = t in excluded
            androidx.compose.material3.FilterChip(
                selected = t in included || isExcluded,
                onClick = { val (i, e) = be.rimeco.weekmenu.data.Tags.cycle(t, included, excluded); onChange(i, e) },
                label = { Text(if (isExcluded) "Geen $t" else t) },
                colors = if (isExcluded) androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer,
                ) else androidx.compose.material3.FilterChipDefaults.filterChipColors(),
            )
        }
    }
}
