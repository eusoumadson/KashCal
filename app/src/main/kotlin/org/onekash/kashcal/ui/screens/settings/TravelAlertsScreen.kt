package org.onekash.kashcal.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.onekash.kashcal.R
import org.onekash.kashcal.ui.components.SettingsTopAppBar

/**
 * Settings screen for departure alerts (time-to-leave reminders based on real-time location).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelAlertsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TravelAlertsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val tomtomKey by viewModel.tomtomApiKey.collectAsStateWithLifecycle()
    val tomtomEnabled by viewModel.tomtomEnabled.collectAsStateWithLifecycle()
    val stadiaKey by viewModel.stadiaApiKey.collectAsStateWithLifecycle()
    val stadiaEnabled by viewModel.stadiaEnabled.collectAsStateWithLifecycle()
    val valhallaEnabled by viewModel.valhallaEnabled.collectAsStateWithLifecycle()
    val rushHourEnabled by viewModel.rushHourEnabled.collectAsStateWithLifecycle()
    val isCircuitOpen by viewModel.isValhallaCircuitOpen.collectAsStateWithLifecycle()
    val hasLocationPerm by viewModel.hasLocationPermission.collectAsStateWithLifecycle()
    val hasBackgroundPerm by viewModel.hasBackgroundPermission.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshPermissions()
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    Scaffold(
        topBar = {
            SettingsTopAppBar(
                title = stringResource(R.string.travel_alerts_settings_title),
                onNavigateBack = onNavigateBack
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.travel_alerts_info_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Location Permissions Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.travel_permissions_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        val allGranted = hasLocationPerm && hasBackgroundPerm
                        TravelStatusBadge(
                            text = stringResource(if (allGranted) R.string.travel_permissions_granted else R.string.travel_permissions_missing),
                            containerColor = if (allGranted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            contentColor = if (allGranted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Text(
                        text = stringResource(R.string.travel_permissions_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!hasLocationPerm) {
                            Button(
                                onClick = {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            ) {
                                Text(stringResource(R.string.travel_permissions_btn_grant))
                            }
                        } else if (!hasBackgroundPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            Button(
                                onClick = {
                                    permissionLauncher.launch(
                                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                    )
                                }
                            ) {
                                Text(stringResource(R.string.travel_permissions_btn_grant))
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null)
                                    )
                                    context.startActivity(intent)
                                }
                            ) {
                                Text(stringResource(R.string.travel_permissions_open_settings))
                            }
                        }
                    }
                }
            }

            // Providers Section Header
            Text(
                text = stringResource(R.string.travel_providers_header),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Provider 1: TomTom (Live Traffic)
            ProviderConfigCard(
                title = stringResource(R.string.travel_provider_tomtom_title),
                description = stringResource(R.string.travel_provider_tomtom_desc),
                linkUrl = "https://developer.tomtom.com",
                linkText = "developer.tomtom.com",
                isEnabled = tomtomEnabled,
                onToggle = viewModel::setTomTomEnabled,
                currentApiKey = tomtomKey,
                onSaveApiKey = viewModel::setTomTomApiKey,
                keyLabel = stringResource(R.string.travel_provider_tomtom_key_label),
                keyHint = stringResource(R.string.travel_provider_tomtom_key_hint)
            )

            // Provider 2: Stadia Maps (Valhalla Cloud)
            ProviderConfigCard(
                title = stringResource(R.string.travel_provider_stadia_title),
                description = stringResource(R.string.travel_provider_stadia_desc),
                linkUrl = "https://stadiamaps.com",
                linkText = "stadiamaps.com",
                isEnabled = stadiaEnabled,
                onToggle = viewModel::setStadiaEnabled,
                currentApiKey = stadiaKey,
                onSaveApiKey = viewModel::setStadiaApiKey,
                keyLabel = stringResource(R.string.travel_provider_stadia_key_label),
                keyHint = stringResource(R.string.travel_provider_stadia_key_hint)
            )

            // Provider 3: Valhalla OpenStreetMap Demo Server
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.travel_provider_valhalla_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                TravelStatusBadge(
                                    text = stringResource(
                                        if (isCircuitOpen) R.string.travel_provider_status_circuit_open
                                        else R.string.travel_provider_status_circuit_closed
                                    ),
                                    containerColor = if (isCircuitOpen) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = if (isCircuitOpen) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Switch(
                            checked = valhallaEnabled,
                            onCheckedChange = viewModel::setValhallaEnabled
                        )
                    }

                    Text(
                        text = stringResource(R.string.travel_provider_valhalla_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Provider 4: Local Offline Estimator
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.travel_provider_local_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        TravelStatusBadge(
                            text = stringResource(R.string.travel_provider_local_status_active),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    Text(
                        text = stringResource(R.string.travel_provider_local_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.travel_provider_rush_hour_title),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.travel_provider_rush_hour_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = rushHourEnabled,
                            onCheckedChange = viewModel::setRushHourEnabled
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ProviderConfigCard(
    title: String,
    description: String,
    linkUrl: String,
    linkText: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    currentApiKey: String,
    onSaveApiKey: (String) -> Unit,
    keyLabel: String,
    keyHint: String
) {
    val uriHandler = LocalUriHandler.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var keyInput by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable { uriHandler.openUri(linkUrl) }
                    ) {
                        Text(
                            text = linkText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AnimatedVisibility(visible = isEnabled || currentApiKey.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text(keyLabel) },
                        placeholder = { Text(keyHint) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    if (isPasswordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                onSaveApiKey(keyInput)
                                keyboardController?.hide()
                            }
                        )
                    )

                    if (keyInput != currentApiKey) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = {
                                    onSaveApiKey(keyInput)
                                    keyboardController?.hide()
                                }
                            ) {
                                Text(stringResource(R.string.action_save))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TravelStatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = modifier
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor
        )
    }
}

