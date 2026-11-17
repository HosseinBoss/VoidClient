package com.v2ray.ang.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.handler.DeviceLock
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.ui.compose.colorBrandAccent
import com.v2ray.ang.ui.compose.colorPingRed

private fun readClipboard(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

private fun writeClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("VoidTunnel", text))
}

@Composable
private fun SmallActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = colorBrandAccent,
        modifier = modifier
            .clip(shape)
            .background(colorBrandAccent.copy(alpha = 0.14f))
            .border(1.dp, colorBrandAccent.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, scheme.outlineVariant, shape)
            .padding(16.dp)
    ) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/**
 * "Device lock" tools: show/copy this device's HWID, lock the selected config to someone else's
 * HWID, and import a locked link.
 */
@Composable
internal fun DeviceLockSection(
    selectedGuid: String?,
    onImportConfig: (String) -> Unit
) {
    val context = LocalContext.current
    val hwid = remember { DeviceLock.hwid(context) }
    val profile = remember(selectedGuid) { selectedGuid?.let { MmkvManager.decodeServerConfig(it) } }
    var showCreate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard("YOUR HWID") {
            Text(hwid, fontFamily = FontFamily.Monospace, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Send this code to the person who gives you a config. They lock the config to it, so it opens only on this device.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            SmallActionButton("Copy HWID", onClick = {
                writeClipboard(context, hwid)
                DeviceLock.toast(context, "HWID copied")
            })
        }

        SectionCard("LOCK A CONFIG TO A DEVICE") {
            Text(
                text = when {
                    profile == null -> "Select a server first, then come back here."
                    DeviceLock.isLockedProfile(profile) -> "The selected config is locked and can't be locked again."
                    else -> "Selected: " + profile.remarks.trim().ifBlank { profile.server.orEmpty() }
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            SmallActionButton("Lock to a HWID\u2026", onClick = {
                if (profile == null || DeviceLock.isLockedProfile(profile)) {
                    DeviceLock.toast(context, "Select an unlocked server first")
                } else {
                    showCreate = true
                }
            })
        }

        SectionCard("IMPORT A LOCKED CONFIG") {
            Text(
                "Paste a link that starts with VoidTunnel://. It only opens on the device it was made for.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            SmallActionButton("Import locked config\u2026", onClick = { showImport = true })
        }
    }

    if (showCreate && profile != null) {
        CreateLockedDialog(profile = profile, onDismiss = { showCreate = false })
    }
    if (showImport) {
        ImportLockedDialog(
            onDismiss = { showImport = false },
            onImportConfig = onImportConfig
        )
    }
}

@Composable
private fun CreateLockedDialog(profile: ProfileItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var hwidText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var link by remember { mutableStateOf<String?>(null) }
    val finalLink = link

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock config to a device") },
        text = {
            Column {
                if (finalLink == null) {
                    Text(
                        "Paste the HWID the customer sent you.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = hwidText,
                        onValueChange = {
                            hwidText = it
                            error = null
                        },
                        singleLine = true,
                        isError = error != null,
                        placeholder = { Text("A1B2-C3D4-E5F6-0718-293A-4B5C") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { readClipboard(context)?.let { hwidText = it.trim(); error = null } }) {
                        Text("Paste")
                    }
                    error?.let {
                        Text(it, fontSize = 12.sp, color = colorPingRed)
                    }
                } else {
                    Text(
                        "Locked link created. It only works on the device with that HWID.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = finalLink.take(46) + "\u2026",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            if (finalLink == null) {
                TextButton(onClick = {
                    val uri = DeviceLock.shareUriOf(profile)
                    if (uri == null) {
                        error = "This config type can't be locked."
                    } else {
                        val created = DeviceLock.createLockedLink(uri, hwidText)
                        if (created == null) {
                            error = "Invalid HWID. It looks like A1B2-C3D4-E5F6-0718-293A-4B5C."
                        } else {
                            link = created
                        }
                    }
                }) { Text("Create link") }
            } else {
                Row {
                    TextButton(onClick = {
                        writeClipboard(context, finalLink)
                        DeviceLock.toast(context, "Locked link copied")
                    }) { Text("Copy") }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, finalLink)
                        }
                        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(chooser)
                    }) { Text("Share") }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun ImportLockedDialog(onDismiss: () -> Unit, onImportConfig: (String) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import locked config") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    minLines = 3,
                    maxLines = 5,
                    isError = error != null,
                    placeholder = { Text("VoidTunnel://\u2026") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { readClipboard(context)?.let { text = it.trim(); error = null } }) {
                    Text("Paste")
                }
                error?.let {
                    Text(it, fontSize = 12.sp, color = colorPingRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val input = text.trim()
                if (input.isEmpty()) {
                    error = "Paste a link first."
                } else {
                    val unwrapped = DeviceLock.unwrapForImport(input)
                    if (unwrapped.isNullOrBlank()) {
                        error = "This link is locked to another device or is not valid."
                    } else {
                        onImportConfig(unwrapped)
                        onDismiss()
                    }
                }
            }) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
