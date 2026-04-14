package com.elysium.console.ui.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elysium.console.ui.theme.*
import com.elysium.console.viewmodel.SettingsViewModel

/**
 * NUCLEUS: The central management hub for Omni Elysium.
 * Handles ROM directories, BIOS/Keys installation, and GPU driver swapping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val romFolders by viewModel.romFolders.collectAsState()
    val biosPath by viewModel.biosPath.collectAsState()
    val keysPath by viewModel.keysPath.collectAsState()
    val driverPath by viewModel.driverPath.collectAsState()

    val context = LocalContext.current

    // Folder Picker Launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            // Take persistable permission
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.addRomFolder(it.toString())
        }
    }

    // Generic File Picker Launcher
    var pickingType by remember { mutableStateOf<String?>(null) }
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            when (pickingType) {
                "bios" -> viewModel.setBiosPath(it.toString())
                "keys" -> viewModel.setKeysPath(it.toString())
                "driver" -> viewModel.setDriverPath(it.toString())
            }
        }
    }

    Scaffold(
        containerColor = DeepBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "NUCLEUS SYNC",
                        style = MaterialTheme.typography.labelLarge,
                        letterSpacing = 3.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NeonGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section: Storage
            item {
                SettingsHeader(title = "STORAGE NODES", icon = Icons.Default.Storage)
            }

            items(romFolders.toList()) { folder ->
                SettingsItem(
                    title = "ROM Folder",
                    subtitle = folder,
                    icon = Icons.Default.Folder,
                    onDelete = { viewModel.removeRomFolder(folder) }
                )
            }

            item {
                SettingsActionItem(
                    title = "Add Storage Node",
                    subtitle = "Select a directory containing your games",
                    icon = Icons.Default.AddCircle,
                    onClick = { folderPickerLauncher.launch(null) }
                )
            }

            // Section: Firmware & Cryptography
            item {
                SettingsHeader(title = "FIRMWARE & ENCRYPTION", icon = Icons.Default.VpnKey)
            }

            item {
                val status = if (biosPath != null) "INSTALLED" else "MISSING"
                val color = if (biosPath != null) NeonGreen else NeonRed
                SettingsActionItem(
                    title = "System BIOS (PS2/Other)",
                    subtitle = biosPath ?: "No BIOS directory selected",
                    icon = Icons.Default.Memory,
                    statusText = status,
                    statusColor = color,
                    onClick = { 
                        pickingType = "bios"
                        filePickerLauncher.launch(arrayOf("*/*")) 
                    }
                )
            }

            item {
                val status = if (keysPath != null) "SYNCED" else "KEYLESS"
                val color = if (keysPath != null) NeonGreen else NeonRed
                SettingsActionItem(
                    title = "Encryption Keys (Switch)",
                    subtitle = keysPath ?: "prod.keys not found",
                    icon = Icons.Default.Security,
                    statusText = status,
                    statusColor = color,
                    onClick = { 
                        pickingType = "keys"
                        filePickerLauncher.launch(arrayOf("*/*")) 
                    }
                )
            }

            // Section: Hardware Acceleration
            item {
                SettingsHeader(title = "CORE ARCHITECTURE", icon = Icons.Default.DeveloperBoard)
            }

            item {
                val status = if (driverPath != null) "CUSTOM" else "SYSTEM"
                val color = if (driverPath != null) NeonGreen else TextSecondary
                SettingsActionItem(
                    title = "GPU Driver (Turnip/Vulkan)",
                    subtitle = driverPath ?: "Using default system driver",
                    icon = Icons.Default.SettingsInputComponent,
                    statusText = status,
                    statusColor = color,
                    onClick = { 
                        pickingType = "driver"
                        filePickerLauncher.launch(arrayOf("*/*")) 
                    }
                )
            }

            item {
                val visualEffectId by viewModel.visualEffectId.collectAsState()
                val isCrt = visualEffectId == 1
                SettingsToggleItem(
                    title = "CRT Retro Scanlines",
                    subtitle = "Apply classic TV scanline filter",
                    icon = Icons.Default.SettingsBrightness,
                    checked = isCrt,
                    onCheckedChange = { checked ->
                        viewModel.setVisualEffectId(if (checked) 1 else 0)
                    }
                )
            }
            
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}

@Composable
fun SettingsHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            letterSpacing = 2.sp
        )
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceCard)
            .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextSecondary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle, 
                style = MaterialTheme.typography.bodySmall, 
                color = TextTertiary,
                maxLines = 1
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = NeonRed.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun SettingsActionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    statusText: String? = null,
    statusColor: Color = NeonGreen,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.horizontalGradient(
                    listOf(SurfaceCard, SurfaceElevated)
                )
            )
            .border(1.dp, SurfaceBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = NeonGreen)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (statusText != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "[$statusText]", 
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontSize = 10.sp
                    )
                }
            }
            Text(
                subtitle, 
                style = MaterialTheme.typography.bodySmall, 
                color = TextTertiary,
                maxLines = 1
            )
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextTertiary)
    }
}
@Composable
fun SettingsToggleItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(SurfaceElevated, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(20.dp))
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            }
            
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NeonGreen,
                    checkedTrackColor = NeonGreen.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = SurfaceElevated
                )
            )
        }
    }
}
