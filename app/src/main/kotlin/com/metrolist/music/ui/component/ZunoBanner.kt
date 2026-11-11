package com.metrolist.music.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZunoBannerCarousel(
    banners: List<ZunoBanner>,
    onBannerClick: (ZunoBanner) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (banners.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { banners.size })

    // Apne aap slide (har 4.5 sec), user swipe kare to ruk jata hai
    LaunchedEffect(banners.size) {
        if (banners.size < 2) return@LaunchedEffect
        while (true) {
            delay(4500)
            if (!pagerState.isScrollInProgress) {
                val next = (pagerState.currentPage + 1) % banners.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 24.dp), // agla banner thoda dikhega (peek)
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val banner = banners[page]
            val offset =
                ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            val scale = lerp(0.92f, 1f, 1f - offset.coerceIn(0f, 1f))
            val alpha = lerp(0.7f, 1f, 1f - offset.coerceIn(0f, 1f))

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        }.clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(listOf(banner.colorStart, banner.colorEnd)),
                        ).clickable { onBannerClick(banner) },
            ) {
                if (banner.imageUrl != null) {
                    AsyncImage(
                        model = banner.imageUrl,
                        contentDescription = banner.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Neeche gehra gradient taaki text saaf padhe
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xCC000000)),
                                    startY = 120f,
                                ),
                            ),
                )

                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                ) {
                    Text(
                        text = banner.title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = banner.subtitle,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Box(
                        modifier =
                            Modifier
                                .padding(top = 10.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .padding(horizontal = 18.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = "Play",
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // Dots
        Row(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(banners.size) { index ->
                val selected = pagerState.currentPage == index
                val dotWidth = animateDpAsState(if (selected) 20.dp else 7.dp, label = "dotWidth")
                Box(
                    modifier =
                        Modifier
                            .height(7.dp)
                            .width(dotWidth.value)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                },
                            ),
                )
            }
        }
    }
}
