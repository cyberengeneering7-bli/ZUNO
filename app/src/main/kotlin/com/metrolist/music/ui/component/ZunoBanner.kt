package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.maskClip
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest

/**
 * ZUNO home banner (data panel se aata hai).
 *
 * actionType : playlist | song | link | none
 * actionValue: playlist ID / song ID / URL
 */
data class ZunoBanner(
    val id: String,
    val title: String,
    val subtitle: String,
    val actionType: String,
    val actionValue: String,
    val imageUrl: String? = null,
    val colorStart: Color = Color(0xFFFF7A45),
    val colorEnd: Color = Color(0xFFEB4060),
)

/** Ek baar mein sabse zyada itne banner (pinned pehle). */
private const val MAX_BANNERS = 20

/**
 * Bada card beech mein, aur dono taraf patli patti. Slide karne par patti khidki ke palle ki tarah
 * khulti aur band hoti hai (Material 3 carousel). Card pe patla glass jaisa border.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZunoBannerCarousel(
    banners: List<ZunoBanner>,
    onBannerClick: (ZunoBanner) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember(banners) { banners.take(MAX_BANNERS) }
    if (items.isEmpty()) return

    // list badalne par (cache -> internet) card ginti sahi rahe
    val count by rememberUpdatedState(items.size)
    val state = rememberCarouselState { count }
    val shape = RoundedCornerShape(32.dp)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(340.dp)
                .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = 320.dp,
            itemSpacing = 8.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(340.dp),
        ) { i ->
            val banner = items[i]
            ZunoBannerCard(
                banner = banner,
                onClick = { onBannerClick(banner) },
                modifier = Modifier.maskClip(shape),
            )
        }
    }
}

@Composable
private fun ZunoBannerCard(
    banner: ZunoBanner,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(banner.colorStart, banner.colorEnd)))
                .clickable(onClick = onClick),
    ) {
        if (banner.imageUrl != null) {
            AsyncImage(
                model =
                    remember(banner.imageUrl) {
                        ImageRequest
                            .Builder(context)
                            .data(banner.imageUrl)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .build()
                    },
                contentDescription = banner.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Neeche gehra scrim, taaki text saaf padhe
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0.55f to Color.Transparent,
                                    1f to Color(0xD9000000),
                                ),
                        ),
                    ),
        )

        // Glass border
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .border(
                        width = 1.dp,
                        brush =
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.40f),
                                    Color.White.copy(alpha = 0.12f),
                                    Color.White.copy(alpha = 0.28f),
                                ),
                            ),
                        shape = shape,
                    ),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            Text(
                text = banner.title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (banner.subtitle.isNotBlank()) {
                Text(
                    text = banner.subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
