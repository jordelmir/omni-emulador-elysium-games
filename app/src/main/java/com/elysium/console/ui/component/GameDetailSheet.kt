package com.elysium.console.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.elysium.console.domain.model.RomFile
import com.elysium.console.ui.theme.DeepBlack
import com.elysium.console.ui.theme.NeonGreen
import com.elysium.console.ui.theme.SurfaceDark
import com.elysium.console.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailSheet(
    rom: RomFile,
    onLaunch: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        scrimColor = Color.Black.copy(alpha = 0.7f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = NeonGreen.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Large Boxart
                Box(
                    modifier = Modifier
                        .size(120.dp, 160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DeepBlack)
                ) {
                    if (rom.hasCoverArt) {
                        AsyncImage(
                            model = rom.coverArtPath,
                            contentDescription = rom.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = rom.platform.abbreviation,
                            modifier = Modifier.align(Alignment.Center),
                            color = NeonGreen.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                Column {
                    Text(
                        text = rom.name,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.sp
                        ),
                        color = Color.White
                    )
                    
                    Text(
                        text = rom.platform.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonGreen
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SdCard,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = rom.formattedSize,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // AI Metadata / Trivia Section
            Text(
                text = "VANGUARD INTELLIGENCE",
                style = MaterialTheme.typography.labelSmall,
                color = NeonGreen.copy(alpha = 0.7f),
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This classic ${rom.platform.displayName} title was identified by the Elysium scraper. " +
                        "It features authentic ${rom.platform.abbreviation} architecture compatibility. " +
                        "Multi-disc grouping is active for this session.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Action Buttons
            Button(
                onClick = onLaunch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = DeepBlack
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "LAUNCH CORE",
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
