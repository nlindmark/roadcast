package se.roadcast.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import se.roadcast.core.model.PlaceCandidate
import se.roadcast.core.model.PlaceCategory

@Composable
fun PlaceImage(
    place: PlaceCandidate,
    modifier: Modifier = Modifier,
    height: Int = 220,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        CategoryCover(place.category, place.name)
        val imageUrl = place.imageUrl
        if (!imageUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = place.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White.copy(alpha = 0.85f))
                    }
                },
                error = { },
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                    ),
                ),
        )
        Text(
            place.name,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        )
    }
}

@Composable
private fun CategoryCover(category: PlaceCategory, name: String) {
    val colors = placeCategoryColors(category)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.take(1).uppercase(),
            style = MaterialTheme.typography.displayLarge,
            color = Color.White.copy(alpha = 0.85f),
        )
    }
}

fun placeCategoryColors(category: PlaceCategory): List<Color> = when (category) {
    PlaceCategory.HISTORY -> listOf(Color(0xFF5C4033), Color(0xFFA67C52))
    PlaceCategory.ARCHITECTURE -> listOf(Color(0xFF4A5568), Color(0xFF718096))
    PlaceCategory.NATURE -> listOf(Color(0xFF2F5D50), Color(0xFF6B8F71))
    PlaceCategory.ENGINEERING -> listOf(Color(0xFF37474F), Color(0xFF78909C))
    PlaceCategory.CULTURE -> listOf(Color(0xFF3D4A6B), Color(0xFF7A6B8A))
    PlaceCategory.INDUSTRY -> listOf(Color(0xFF455A64), Color(0xFF90A4AE))
    PlaceCategory.PERSON -> listOf(Color(0xFF5D4037), Color(0xFFA1887F))
    PlaceCategory.LEGEND -> listOf(Color(0xFF4A3F6B), Color(0xFF8E7DB0))
    PlaceCategory.OTHER -> listOf(Color(0xFF546E7A), Color(0xFF90A4AE))
}
