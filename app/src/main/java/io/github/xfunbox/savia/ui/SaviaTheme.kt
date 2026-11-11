package io.github.xfunbox.savia.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Una sola paleta para las superficies translúcidas, formularios y navegación. */
@Composable
internal fun SaviaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFC6FF00), onPrimary = Color(0xFF182000),
            primaryContainer = Color(0xFF26330D), onPrimaryContainer = Color(0xFFE8FF9A),
            secondary = Color(0xFFB69AFF), onSecondary = Color(0xFF251344),
            secondaryContainer = Color(0xFF302448), onSecondaryContainer = Color(0xFFEDE3FF),
            tertiary = Color(0xFF7EE7B1), onTertiary = Color(0xFF073923),
            error = Color(0xFFFF7891), onError = Color(0xFF4A0015),
            errorContainer = Color(0xFF431E2A), onErrorContainer = Color(0xFFFFD9E1),
            background = Color(0xFF090B10), onBackground = Color(0xFFF3F0FA),
            surface = Color(0xFF13151D), onSurface = Color(0xFFF3F0FA),
            surfaceVariant = Color(0xFF23232F), onSurfaceVariant = Color(0xFFC7C4D3),
            surfaceContainerLowest = Color(0xFF0D0F16), surfaceContainerLow = Color(0xC9181B25),
            surfaceContainer = Color(0xFF1D202B), surfaceContainerHigh = Color(0xFF282A37),
            surfaceContainerHighest = Color(0xFF333443), outline = Color(0xFF777084),
            outlineVariant = Color(0xFF3D3C4D), inverseSurface = Color(0xFFEAE5F2),
            inverseOnSurface = Color(0xFF292631), inversePrimary = Color(0xFF526B00)
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(16.dp),
            medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(32.dp)
        ),
        content = content
    )
}

@Composable
internal fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .10f))
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun SectionIntro(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun SaviaMark(modifier: Modifier = Modifier) {
    androidx.compose.material3.Icon(
        painter = androidx.compose.ui.res.painterResource(io.github.xfunbox.savia.R.drawable.ic_savia_mark),
        contentDescription = null,
        modifier = modifier,
        tint = Color.Unspecified
    )
}
