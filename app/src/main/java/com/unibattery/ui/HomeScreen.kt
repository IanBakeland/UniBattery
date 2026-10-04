@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.unibattery.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unibattery.R
import com.unibattery.bluetooth.BatteryState
import com.unibattery.bluetooth.BtDevice
import com.unibattery.bluetooth.BtStatus
import com.unibattery.notify.BatteryNotificationService
import com.unibattery.repository

@Composable
fun HomeScreen() {
  val context = LocalContext.current
  val activity = LocalActivity.current
  val repo = context.repository
  val state by repo.state.collectAsStateWithLifecycle()
  var showSettings by rememberSaveable { mutableStateOf(false) }
  var pairedExpanded by rememberSaveable { mutableStateOf(false) }
  var permissionBlocked by rememberSaveable { mutableStateOf(false) }

  val requestConnect = rememberLauncherForActivityResult(RequestPermission()) { granted ->
    // A second denial means Android won't show the dialog again; send the user to settings instead.
    permissionBlocked = !granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
      activity?.shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT) == false
    repo.refresh(manual = false)
    BatteryNotificationService.sync(context)
  }
  val enableBluetooth = rememberLauncherForActivityResult(StartActivityForResult()) { repo.refresh(manual = false) }

  val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  val pullState = rememberPullToRefreshState()

  Scaffold(
    modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    topBar = {
      LargeFlexibleTopAppBar(
        title = { Text(stringResource(R.string.title)) },
        subtitle = {
          AnimatedContent(subtitle(state), label = "subtitle") { Text(it) }
        },
        actions = {
          IconButton(onClick = { repo.refresh() }, enabled = state.status == BtStatus.On && !state.refreshing) {
            Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.refresh))
          }
          IconButton(onClick = { showSettings = true }) {
            Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings))
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainer,
          scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        scrollBehavior = scroll,
      )
    },
  ) { padding ->
    PullToRefreshBox(
      isRefreshing = state.refreshing,
      onRefresh = { repo.refresh() },
      state = pullState,
      modifier = Modifier.padding(padding).fillMaxSize(),
      indicator = {
        PullToRefreshDefaults.LoadingIndicator(
          state = pullState,
          isRefreshing = state.refreshing,
          modifier = Modifier.align(Alignment.TopCenter),
        )
      },
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        when (state.status) {
          BtStatus.NoAdapter -> item("message") {
            Message(R.drawable.ic_bluetooth_off, R.string.no_adapter_title, R.string.no_adapter_body)
          }
          BtStatus.NoPermission -> item("message") {
            Message(R.drawable.ic_bluetooth, R.string.permission_title, R.string.permission_body) {
              if (permissionBlocked) {
                OutlinedButton(onClick = {
                  context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                  )
                }) { Text(stringResource(R.string.permission_settings_button)) }
              } else {
                Button(onClick = { requestConnect.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
                  Text(stringResource(R.string.permission_button))
                }
              }
            }
          }
          BtStatus.Off -> item("message") {
            Message(R.drawable.ic_bluetooth_off, R.string.off_title, R.string.off_body) {
              Button(onClick = {
                runCatching { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
              }) { Text(stringResource(R.string.off_button)) }
            }
          }
          BtStatus.On -> {
            val connected = state.connected
            if (connected.isEmpty()) {
              item("message") { Message(R.drawable.ic_bluetooth, R.string.empty_title, R.string.empty_body, spin = true) }
            } else {
              item("overview") { Overview(connected, Modifier.animateItem()) }
              item("connected-header") { SectionHeader(stringResource(R.string.section_connected), Modifier.animateItem()) }
              connected.forEach { device ->
                item(device.address) { DeviceCard(device, Modifier.animateItem()) }
              }
            }
            val paired = state.paired
            if (paired.isNotEmpty()) {
              item("paired-header") {
                PairedHeader(paired.size, pairedExpanded, Modifier.animateItem()) { pairedExpanded = !pairedExpanded }
              }
              if (pairedExpanded) {
                item("paired-list") {
                  Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    paired.forEachIndexed { i, d -> PairedRow(d, i, paired.size) }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showSettings) SettingsSheet(state.status, onDismiss = { showSettings = false })
}

@Composable
private fun subtitle(state: BatteryState): String = when (state.status) {
  BtStatus.On ->
    if (state.connected.isEmpty()) stringResource(R.string.no_devices_connected)
    else pluralStringResource(R.plurals.devices_connected, state.connected.size, state.connected.size)
  BtStatus.Off -> stringResource(R.string.off_title)
  BtStatus.NoPermission -> stringResource(R.string.permission_title)
  BtStatus.NoAdapter -> stringResource(R.string.no_adapter_title)
}

/** Battery colour roles: error when critical, tertiary when getting low, primary otherwise. */
@Composable
private fun levelColors(level: Int?): Pair<Color, Color> = with(MaterialTheme.colorScheme) {
  when {
    level == null -> surfaceContainerHighest to onSurfaceVariant
    level <= 15 -> errorContainer to onErrorContainer
    level <= 30 -> tertiaryContainer to onTertiaryContainer
    else -> primaryContainer to onPrimaryContainer
  }
}

@Composable
private fun levelAccent(level: Int?): Color = with(MaterialTheme.colorScheme) {
  when {
    level == null -> outline
    level <= 15 -> error
    level <= 30 -> tertiary
    else -> primary
  }
}

@Composable
private fun animatedProgress(level: Int): Float {
  val p by animateFloatAsState(level / 100f, MaterialTheme.motionScheme.slowSpatialSpec(), label = "battery")
  return p.coerceIn(0f, 1f)
}

@Composable
private fun Overview(devices: List<BtDevice>, modifier: Modifier = Modifier) {
  val reporting = devices.filter { it.battery != null }
  val lowest = reporting.minByOrNull { it.battery!! }
  val (container, onContainer) = levelColors(lowest?.battery)
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(36.dp),
    colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
  ) {
    if (lowest == null) {
      Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_info), null)
        Spacer(Modifier.width(16.dp))
        Text(stringResource(R.string.overview_none), style = MaterialTheme.typography.bodyLarge)
      }
      return@Card
    }
    val level = lowest.battery!!
    Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
        val progress = animatedProgress(level)
        CircularWavyProgressIndicator(
          progress = { progress },
          modifier = Modifier.size(132.dp),
          color = onContainer,
          trackColor = onContainer.copy(alpha = 0.12f),
        )
        Text(
          stringResource(R.string.percent, level),
          style = MaterialTheme.typography.headlineLargeEmphasized,
        )
      }
      Spacer(Modifier.width(20.dp))
      Column(Modifier.weight(1f)) {
        Text(
          if (level > 30) stringResource(R.string.overview_all_good) else stringResource(R.string.overview_lowest, lowest.name, level),
          style = MaterialTheme.typography.titleLargeEmphasized,
        )
        Spacer(Modifier.height(12.dp))
        // One mini ring per reporting device: a glanceable "everything" view next to the lowest one.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          reporting.take(4).forEach { d ->
            val p = animatedProgress(d.battery!!)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.semantics {
              contentDescription = "${d.name} ${d.battery}%"
            }) {
              CircularWavyProgressIndicator(
                progress = { p },
                modifier = Modifier.size(40.dp),
                color = onContainer,
                trackColor = onContainer.copy(alpha = 0.12f),
                amplitude = { 0f },
              )
              Icon(painterResource(d.kind.icon), null, Modifier.size(18.dp))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    style = MaterialTheme.typography.labelLargeEmphasized,
    color = MaterialTheme.colorScheme.primary,
    modifier = modifier.padding(start = 8.dp, top = 8.dp),
  )
}

@Composable
private fun DeviceIcon(device: BtDevice, size: Int = 56) {
  val (container, onContainer) = levelColors(if (device.connected) device.battery else null)
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier.size(size.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(container),
  ) {
    Icon(painterResource(device.kind.icon), null, tint = onContainer, modifier = Modifier.size((size * 0.46f).dp))
  }
}

@Composable
private fun DeviceCard(device: BtDevice, modifier: Modifier = Modifier) {
  val level = device.battery
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(28.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
  ) {
    Column(Modifier.padding(20.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        DeviceIcon(device)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
          Text(
            device.name,
            style = MaterialTheme.typography.titleMediumEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(Modifier.height(4.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF2E9E5B)))
            Spacer(Modifier.width(6.dp))
            Text(
              stringResource(R.string.connected),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        AnimatedContent(
          targetState = level,
          transitionSpec = {
            val up = (targetState ?: 0) > (initialState ?: 0)
            (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
              (slideOutVertically { if (up) -it else it } + fadeOut())
          },
          label = "level",
        ) { l ->
          Text(
            if (l != null) stringResource(R.string.percent, l) else "—",
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = if (l != null) levelAccent(l) else MaterialTheme.colorScheme.outline,
          )
        }
      }
      Spacer(Modifier.height(16.dp))
      if (level != null) {
        val progress = animatedProgress(level)
        LinearWavyProgressIndicator(
          progress = { progress },
          modifier = Modifier.fillMaxWidth().height(14.dp),
          color = levelAccent(level),
          trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
      } else {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
          Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
              painterResource(R.drawable.ic_info), null,
              tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
              Text(stringResource(R.string.battery_unavailable), style = MaterialTheme.typography.labelLarge)
              Text(
                stringResource(R.string.battery_unavailable_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PairedHeader(count: Int, expanded: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
  val rotation by animateFloatAsState(if (expanded) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "chevron")
  Row(
    modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onToggle)
      .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      stringResource(R.string.section_paired, count),
      style = MaterialTheme.typography.labelLargeEmphasized,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.weight(1f),
    )
    Icon(painterResource(R.drawable.ic_expand), null, Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.primary)
  }
}

/** Expressive grouped list: large outer corners, tight inner corners. */
@Composable
private fun PairedRow(device: BtDevice, index: Int, count: Int) {
  val top = if (index == 0) 24.dp else 6.dp
  val bottom = if (index == count - 1) 24.dp else 6.dp
  Surface(
    shape = RoundedCornerShape(top, top, bottom, bottom),
    color = MaterialTheme.colorScheme.surfaceBright,
  ) {
    ListItem(
      headlineContent = { Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
      supportingContent = { Text(stringResource(R.string.not_connected)) },
      leadingContent = { DeviceIcon(device, size = 40) },
      colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
  }
}

@Composable
private fun Message(icon: Int, title: Int, body: Int, spin: Boolean = false, action: (@Composable () -> Unit)? = null) {
  val rotation = if (spin) {
    val t = rememberInfiniteTransition(label = "spin")
    t.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "r").value
  } else 0f
  Column(
    Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(168.dp)) {
      Box(
        Modifier.fillMaxSize().rotate(rotation).clip(MaterialShapes.Cookie12Sided.toShape())
          .background(MaterialTheme.colorScheme.primaryContainer)
      )
      Icon(
        painterResource(icon), null,
        tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(64.dp),
      )
    }
    Spacer(Modifier.height(28.dp))
    Text(
      stringResource(title),
      style = MaterialTheme.typography.headlineSmallEmphasized,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
      stringResource(body),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    if (action != null) {
      Spacer(Modifier.height(24.dp))
      action()
    }
  }
}

@Composable
private fun SettingsSheet(status: BtStatus, onDismiss: () -> Unit) {
  val context = LocalContext.current
  var enabled by rememberSaveable { mutableStateOf(BatteryNotificationService.isEnabled(context)) }
  fun set(on: Boolean) {
    enabled = on
    BatteryNotificationService.setEnabled(context, on)
  }
  val requestNotifications = rememberLauncherForActivityResult(RequestPermission()) { granted -> if (granted) set(true) }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
      Text(
        stringResource(R.string.settings),
        style = MaterialTheme.typography.headlineSmallEmphasized,
        modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
      )
      Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        ListItem(
          headlineContent = { Text(stringResource(R.string.settings_notification)) },
          supportingContent = { Text(stringResource(R.string.settings_notification_body)) },
          trailingContent = {
            Switch(
              checked = enabled,
              enabled = status != BtStatus.NoPermission && status != BtStatus.NoAdapter,
              onCheckedChange = { on ->
                val needsPermission = on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                  ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                  PackageManager.PERMISSION_GRANTED
                if (needsPermission) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) else set(on)
              },
            )
          },
          colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
      }
      Spacer(Modifier.height(16.dp))
      Row(Modifier.padding(horizontal = 8.dp)) {
        Icon(
          painterResource(R.drawable.ic_info), null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
          stringResource(R.string.settings_about),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
