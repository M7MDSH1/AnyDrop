package com.anydrop.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.anydrop.app.data.repository.LoginPlatform
import com.anydrop.app.ui.theme.Amber
import com.anydrop.app.ui.theme.Black
import com.anydrop.app.ui.theme.DarkGray
import com.anydrop.app.ui.theme.LedGreen
import com.anydrop.app.ui.theme.LedRed
import com.anydrop.app.ui.theme.MediumGray
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
                    contentDescription = "Back",
                    tint = Amber
                )
            }
        }
        Text(
            text = "SESSION SIGNAL",
            style = MaterialTheme.typography.headlineMedium,
            color = Amber
        )
        Text(
            text = "Log in to platforms to download their content",
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
                    onRemove = if (!platform.builtIn) {
                        { viewModel.removePlatform(platform.name) }
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
                    Text("+  ADD PLATFORM", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        statusMessage?.let {
            Text(
                text = it,
                color = if (it.startsWith("Error")) LedRed else LedGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Fallback: import a cookies.txt file.
        Text(
            text = "— or import a cookies.txt file —",
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
            Text("SELECT cookies.txt", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PlatformRow(
    platform: LoginPlatform,
    loggedIn: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Black)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(platform.name, color = White, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                Text(
                    platform.domain,
                    color = White.copy(alpha = 0.35f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (loggedIn) {
                    Text("✓ LOGGED IN", color = LedGreen, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                } else {
                    Text("LOG IN ›", color = Amber, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
                if (onRemove != null) {
                    Spacer(modifier = Modifier.height(0.dp))
                    TextButton(onClick = onRemove) {
                        Text("✕", color = LedRed, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                    }
                }
            }
        }
    }
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
        title = { Text("ADD PLATFORM", color = Amber, fontFamily = FontFamily.Monospace) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. Facebook)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Login URL") },
                    placeholder = { Text("https://www.facebook.com/login") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "The cookie domain is detected from the URL automatically.",
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
                Text("ADD", color = Amber, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = White.copy(alpha = 0.6f), fontFamily = FontFamily.Monospace)
            }
        }
    )
}
