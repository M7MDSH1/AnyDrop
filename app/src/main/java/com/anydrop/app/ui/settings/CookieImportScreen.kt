package com.anydrop.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anydrop.app.R
import com.anydrop.app.data.repository.LoginPlatform
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.DarkGray
import com.anydrop.app.ui.theme.Hairline
import com.anydrop.app.ui.theme.MediumGray
import com.anydrop.app.ui.theme.StatusGreen
import com.anydrop.app.ui.theme.StatusRed
import com.anydrop.app.ui.theme.White

@Composable
fun CookieImportScreen(
    viewModel: CookieImportViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val platforms by viewModel.platforms.collectAsState()
    val loggedInDomains by viewModel.loggedInDomains.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var activeLogin by remember { mutableStateOf<LoginPlatform?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<Pair<SessionAction, LoginPlatform>?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importCookies(it) }
    }

    // In-app login flow takes over the whole screen while active.
    activeLogin?.let { site ->
        WebLoginScreen(
            loginUrl = site.loginUrl,
            cookieDomainUrl = "https://${site.domain}",
            onCancel = { activeLogin = null },
            onCookiesCaptured = { raw ->
                // Instagram needs sessionid; custom platforms accept any cookies.
                val required = if (site.domain.contains("instagram")) "sessionid" else null
                viewModel.saveWebViewCookies(site.domain, raw, required)
                activeLogin = null
            }
        )
        return
    }

    if (showAddDialog) {
        AddPlatformDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, url ->
                viewModel.addPlatform(name, url)
                showAddDialog = false
            }
        )
    }

    confirmAction?.let { (action, platform) ->
        ConfirmSessionDialog(
            action = action,
            platformName = platform.name,
            onConfirm = {
                when (action) {
                    SessionAction.DISCONNECT -> viewModel.disconnect(platform)
                    SessionAction.DELETE -> viewModel.deletePlatform(platform)
                }
                confirmAction = null
            },
            onDismiss = { confirmAction = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkGray)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back_desc),
                    tint = Amber
                )
            }
        }
        Text(
            text = stringResource(R.string.settings_session_signal),
            style = MaterialTheme.typography.headlineMedium,
            color = Amber
        )
        Text(
            text = stringResource(R.string.session_signal_subtitle),
            color = White.copy(alpha = 0.5f),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Platform list with ✓ for logged-in ones. Tap to (re)login.
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(platforms, key = { it.name }) { platform ->
                val loggedIn = loggedInDomains.contains(platform.domain)
                PlatformRow(
                    platform = platform,
                    loggedIn = loggedIn,
                    onClick = { activeLogin = platform },
                    // Sign out, keep the platform (every platform, Instagram included).
                    onDisconnect = if (loggedIn) {
                        { confirmAction = SessionAction.DISCONNECT to platform }
                    } else null,
                    // Sign out AND remove from the list (only platforms the user added).
                    onDelete = if (!platform.builtIn) {
                        { confirmAction = SessionAction.DELETE to platform }
                    } else null
                )
            }

            item {
                // + Add custom platform
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.add_platform_btn), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        statusMessage?.let {
            Text(
                text = it.text,
                color = if (it.isError) StatusRed else StatusGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Fallback: import a cookies.txt file.
        Text(
            text = stringResource(R.string.cookies_or_import),
            color = White.copy(alpha = 0.35f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedButton(
            onClick = { filePickerLauncher.launch("text/*") },
            shape = RoundedCornerShape(4.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = White.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.btn_select_cookies), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

enum class SessionAction { DISCONNECT, DELETE }

@Composable
private fun PlatformRow(
    platform: LoginPlatform,
    loggedIn: Boolean,
    onClick: () -> Unit,
    onDisconnect: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (loggedIn) StatusGreen.copy(alpha = 0.35f) else Hairline, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Black)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(platform.name, color = White, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    Text(
                        platform.domain,
                        color = White.copy(alpha = 0.4f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
                if (loggedIn) {
                    Text(stringResource(R.string.logged_in), color = StatusGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                } else {
                    Text(stringResource(R.string.log_in), color = Amber, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
            if (onDisconnect != null || onDelete != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    onDisconnect?.let {
                        SessionButton(
                            icon = Icons.AutoMirrored.Filled.Logout,
                            label = stringResource(R.string.session_disconnect),
                            color = Amber,
                            onClick = it
                        )
                    }
                    onDelete?.let {
                        SessionButton(
                            icon = Icons.Filled.DeleteOutline,
                            label = stringResource(R.string.session_delete),
                            color = StatusRed,
                            onClick = it
                        )
                    }
                }
            }
        }
    }
}

/** Small outlined icon+label button used for DISCONNECT / DELETE on a platform card. */
@Composable
private fun SessionButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = Modifier.height(36.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}

@Composable
private fun ConfirmSessionDialog(
    action: SessionAction,
    platformName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDelete = action == SessionAction.DELETE
    val accent = if (isDelete) StatusRed else Amber
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MediumGray,
        icon = {
            Icon(
                if (isDelete) Icons.Filled.DeleteOutline else Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                tint = accent
            )
        },
        title = {
            Text(
                stringResource(if (isDelete) R.string.delete_platform_title else R.string.disconnect_confirm_title, platformName),
                color = White,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp
            )
        },
        text = {
            Text(
                stringResource(if (isDelete) R.string.delete_platform_message else R.string.disconnect_confirm_message, platformName),
                color = White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Icon(
                    if (isDelete) Icons.Filled.DeleteOutline else Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    stringResource(if (isDelete) R.string.session_delete else R.string.session_disconnect),
                    color = accent,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
            }
        }
    )
}

@Composable
private fun AddPlatformDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MediumGray,
        title = { Text(stringResource(R.string.add_platform_title), color = Amber, fontFamily = FontFamily.Monospace) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.add_platform_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.add_platform_url)) },
                    placeholder = { Text("https://www.facebook.com/login") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.add_platform_auto_domain),
                    color = White.copy(alpha = 0.4f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && url.isNotBlank()) onAdd(name, url) },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text(stringResource(R.string.btn_add), color = Amber, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
            }
        }
    )
}
