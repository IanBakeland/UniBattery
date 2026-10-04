@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.unibattery.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TonalToggleButton
import androidx.graphics.shapes.RoundedPolygon
import androidx.compose.material3.ToggleButton
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import com.unibattery.bluetooth.DeviceKind
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.flow.distinctUntilChanged
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
  var iconFor by rememberSaveable { mutableStateOf<String?>(null) } // device whose icon picker is open
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

  // Persistent notification: shared by the quick action and the settings sheet.
  val haptics = LocalHapticFeedback.current
  var notifyOn by rememberSaveable { mutableStateOf(BatteryNotificationService.isEnabled(context)) }
  fun applyNotify(on: Boolean) {
    haptics.performHapticFeedback(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
    notifyOn = on
    BatteryNotificationService.setEnabled(context, on)
  }
  val requestNotifications = rememberLauncherForActivityResult(RequestPermission()) { granted -> if (granted) applyNotify(true) }
  val setNotify = { on: Boolean ->
    val needsPermission = on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    if (needsPermission) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) else applyNotify(on)
  }

  val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  val pullState = rememberPullToRefreshState()
  // Goes through View.performHapticFeedback, so the system "touch feedback" setting is respected.
  val tap = { haptics.performHapticFeedback(HapticFeedbackType.ContextClick) }

  // Confirm when a device connects, a softer tick when one drops. Skipped on first load so opening
  // the app doesn't buzz for devices that were already connected.
  val connectedAddresses = state.connected.mapTo(HashSet()) { it.address }
  val previousAddresses = remember { mutableStateOf<Set<String>?>(null) }
  LaunchedEffect(connectedAddresses) {
    previousAddresses.value?.let { prev ->
      when {
        (connectedAddresses - prev).isNotEmpty() -> haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        (prev - connectedAddresses).isNotEmpty() -> haptics.performHapticFeedback(HapticFeedbackType.ToggleOff)
      }
    }
    previousAddresses.value = connectedAddresses
  }

  // Pull-to-refresh: one tick when the pull crosses the release threshold (not when a button refresh
  // animates the indicator in).
  LaunchedEffect(pullState) {
    snapshotFlow { pullState.distanceFraction >= 1f && !pullState.isAnimating }
      .distinctUntilChanged()
      .collect { if (it) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) }
  }

  // The refresh icon makes one full turn per refresh, however it was started.
  val refreshTurn = remember { Animatable(0f) }
  val turnSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
  LaunchedEffect(state.refreshing) {
    if (state.refreshing) refreshTurn.animateTo(refreshTurn.targetValue + 360f, turnSpec)
  }
  // Landscape phones have little height: the large collapsing bar would eat half the screen.
  val compactHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < CompactHeight

  Scaffold(
    modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    topBar = {
      val title = @Composable { Text(stringResource(R.string.title)) }
      val subtitle = @Composable { AnimatedContent(subtitle(state), label = "subtitle") { Text(it) } }
      val actions: @Composable RowScope.() -> Unit = {
        IconButton(onClick = { tap(); repo.refresh() }, enabled = state.status == BtStatus.On && !state.refreshing) {
          Icon(
            painterResource(R.drawable.ic_refresh), stringResource(R.string.refresh),
            Modifier.graphicsLayer { rotationZ = refreshTurn.value },
          )
        }
        IconButton(onClick = { tap(); showSettings = true }) {
          Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.settings))
        }
      }
      val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
      )
      if (compactHeight) {
        TopAppBar(title = title, subtitle = subtitle, actions = actions, colors = colors, scrollBehavior = scroll)
      } else {
        LargeFlexibleTopAppBar(title = title, subtitle = subtitle, actions = actions, colors = colors, scrollBehavior = scroll)
      }
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
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactWidth = maxWidth < MediumWidth
        val gutter = if (compactWidth) 16.dp else 24.dp
        val spacing = if (compactWidth) 12.dp else 16.dp
        // Content is centred at a readable width; the side margins stay part of the scrollable area.
        val side = (maxWidth - minOf(maxWidth - gutter * 2, MaxContentWidth)) / 2
        val roomy = !compactWidth && !compactHeight
        val connected = state.connected
        // Expanded windows (unfolded Fold, tablets, landscape phones) pin the overview in its own pane.
        val overviewPane = maxWidth >= ExpandedWidth && state.status == BtStatus.On && connected.isNotEmpty()
        val gridWidth = maxWidth - side * 2 - if (overviewPane) OverviewPaneWidth + spacing else 0.dp
        val columns = ((gridWidth + spacing) / (MinCardWidth + spacing)).toInt().coerceIn(1, 3)

        // Widget tiers. Lowest battery first, so whatever needs charging gets the most space: the first
        // few become medium widgets, the rest compact tiles, and silent devices (no battery report) wide strips.
        // Nothing connected: the same widgets show each device's last known level instead of an empty screen.
        val live = connected.isNotEmpty()
        val remembered = state.paired.filter { it.lastBattery != null }.sortedBy { it.lastBattery }
        val reporting = (if (live) connected else remembered).filter { it.shownLevel != null }.sortedBy { it.shownLevel }
        val silent = if (live) connected.filter { it.battery == null } else emptyList()
        val featuredCount = maxOf(2, columns).let { if (reporting.size == it + 1) it + 1 else it } // no lone tile
        val featured = reporting.take(featuredCount)
        val compact = reporting.drop(featuredCount)
        val mediumSpan = Lanes / columns
        val tilesPerRow = listOf(6, 4, 3, 2).first { it <= TilesPerRow[columns - 1] && it <= maxOf(2, compact.size) }
        // While something is connected, remembered devices get their own section of compact tiles below it.
        val rememberedPerRow = listOf(6, 4, 3, 2).first { it <= TilesPerRow[columns - 1] && it <= maxOf(2, remembered.size) }
        val quickActions = @Composable {
          QuickActions(
            notifyOn = notifyOn,
            onNotifyChange = setNotify,
            onBluetoothSettings = { tap(); runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } },
            center = overviewPane,
          )
        }

        Row(Modifier.fillMaxSize().padding(start = if (overviewPane) side else 0.dp)) {
          if (overviewPane) {
            Column(
              Modifier.width(OverviewPaneWidth).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 32.dp),
            ) {
              Overview(connected, large = !compactHeight, vertical = true)
              Spacer(Modifier.height(spacing))
              quickActions()
            }
            Spacer(Modifier.width(spacing))
          }
          LazyVerticalGrid(
            columns = GridCells.Fixed(Lanes),
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentPadding = PaddingValues(start = if (overviewPane) 0.dp else side, end = side, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalArrangement = Arrangement.spacedBy(spacing),
          ) {
            when (state.status) {
              BtStatus.NoAdapter -> fullItem("message") {
                Message(R.drawable.ic_bluetooth_off, R.string.no_adapter_title, R.string.no_adapter_body, MaterialShapes.PuffyDiamond)
              }
              BtStatus.NoPermission -> fullItem("message") {
                Message(R.drawable.ic_bluetooth, R.string.permission_title, R.string.permission_body, MaterialShapes.Clover4Leaf) {
                  if (permissionBlocked) {
                    OutlinedButton(onClick = {
                      tap()
                      context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                      )
                    }) { Text(stringResource(R.string.permission_settings_button)) }
                  } else {
                    Button(onClick = { tap(); requestConnect.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
                      Text(stringResource(R.string.permission_button))
                    }
                  }
                }
              }
              BtStatus.Off -> fullItem("message") {
                Message(R.drawable.ic_bluetooth_off, R.string.off_title, R.string.off_body, MaterialShapes.SoftBurst) {
                  Button(onClick = {
                    tap()
                    runCatching { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                  }) { Text(stringResource(R.string.off_button)) }
                }
              }
              BtStatus.On -> {
                if (reporting.isEmpty() && silent.isEmpty()) {
                  fullItem("message") { Message(R.drawable.ic_bluetooth, R.string.empty_title, R.string.empty_body, MaterialShapes.Cookie12Sided, spin = true) }
                } else {
                  if (live) {
                    if (!overviewPane) fullItem("overview") { Overview(connected, large = roomy, modifier = itemMotion()) }
                    fullItem("connected-header") { SectionHeader(stringResource(R.string.section_connected), itemMotion()) }
                  } else {
                    fullItem("offline") { OfflineBanner(itemMotion()) }
                    fullItem("remembered-header") { SectionHeader(stringResource(R.string.section_last_known), itemMotion()) }
                  }
                  // Same key across tiers, so a device whose level changes its rank glides to its new spot.
                  featured.forEach { d -> item(d.address, span = { GridItemSpan(mediumSpan) }) { DeviceCard(d, itemMotion()) { tap(); iconFor = d.address } } }
                  compact.forEach { d -> item(d.address, span = { GridItemSpan(Lanes / tilesPerRow) }) { DeviceTile(d, itemMotion()) { tap(); iconFor = d.address } } }
                  silent.forEach { d -> fullItem(d.address) { DeviceStrip(d, itemMotion()) { tap(); iconFor = d.address } } }
                  if (live && remembered.isNotEmpty()) {
                    fullItem("remembered-header") { SectionHeader(stringResource(R.string.section_last_known), itemMotion()) }
                    remembered.forEach { d -> item(d.address, span = { GridItemSpan(Lanes / rememberedPerRow) }) { DeviceTile(d, itemMotion()) { tap(); iconFor = d.address } } }
                  }
                  if (!overviewPane) fullItem("actions") { Box(itemMotion().padding(top = 8.dp)) { quickActions() } }
                }
                // Only devices with nothing remembered; the rest are shown above.
                val paired = state.paired - remembered.toSet()
                if (paired.isNotEmpty()) {
                  fullItem("paired-header") {
                    PairedHeader(paired.size, pairedExpanded, itemMotion()) { tap(); pairedExpanded = !pairedExpanded }
                  }
                  if (pairedExpanded) {
                    if (columns == 1) {
                      fullItem("paired-list") {
                        Column(itemMotion(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                          paired.forEachIndexed { i, d -> PairedRow(d, groupedShape(i, paired.size)) { tap(); iconFor = d.address } }
                        }
                      }
                    } else {
                      // A grouped list can't span grid cells, so each paired device becomes its own tile. Same key
                      // as its connected card, so a device that disconnects glides into this section.
                      paired.forEach { d ->
                        item(d.address, span = { GridItemSpan(mediumSpan) }) { PairedRow(d, RoundedCornerShape(24.dp), itemMotion()) { tap(); iconFor = d.address } }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  if (showSettings) {
    SettingsSheet(state.status, notifyOn, onNotifyChange = setNotify, onDismiss = { showSettings = false })
  }
  state.devices.find { it.address == iconFor }?.let { device -> IconSheet(device, onDismiss = { iconFor = null }) }
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

// Material 3 window breakpoints.
private val MediumWidth = 600.dp
private val ExpandedWidth = 840.dp
private val CompactHeight = 480.dp
private val MaxContentWidth = 1200.dp
private val OverviewPaneWidth = 320.dp
// Narrowest a device card gets before the grid drops a column (name + big percentage must fit).
private val MinCardWidth = 320.dp
// 12 lanes divide evenly into 1-3 medium widgets and 2/3/4/6 compact tiles per row.
private const val Lanes = 12
// Most compact tiles per row for 1, 2 and 3 medium columns.
private val TilesPerRow = listOf(3, 4, 6)

/** List enter/exit/reorder motion, taken from the Material 3 Expressive motion scheme. */
@Composable
private fun LazyGridItemScope.itemMotion(): Modifier = Modifier.animateItem(
  fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
  placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
  fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
)

private fun LazyGridScope.fullItem(key: String, content: @Composable LazyGridItemScope.() -> Unit) =
  item(key, span = { GridItemSpan(maxLineSpan) }, content = content)

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

/**
 * Starts empty so an indicator fills in when a device appears. Returned as a lambda so the per-frame
 * value is only read while drawing, not on every recomposition.
 */
@Composable
private fun animatedProgress(level: Int): () -> Float {
  val p = remember { Animatable(0f) }
  val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
  LaunchedEffect(level) { p.animateTo(level / 100f, spec) }
  return remember(p) { { p.value.coerceIn(0f, 1f) } }
}

/**
 * One small scale bump when [trigger] turns true after first composition: a low-battery warning or a
 * "full" moment, never looped.
 */
@Composable
private fun Modifier.nudge(trigger: Boolean): Modifier {
  val scale = remember { Animatable(1f) }
  val first = remember { booleanArrayOf(true) }
  val spec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
  LaunchedEffect(trigger) {
    if (trigger && !first[0]) {
      scale.animateTo(1.12f, spec)
      scale.animateTo(1f, spec)
    }
    first[0] = false
  }
  return graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/** Percentage that rolls up or down instead of swapping in place (72% → 73%). */
@Composable
private fun AnimatedPercent(level: Int?, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
  val spatial = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
  val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
  AnimatedContent(
    targetState = level,
    modifier = modifier,
    transitionSpec = {
      val up = (targetState ?: 0) > (initialState ?: 0)
      (slideInVertically(spatial) { if (up) it else -it } + fadeIn(effects)) togetherWith
        (slideOutVertically(spatial) { if (up) -it else it } + fadeOut(effects))
    },
    label = "percent",
  ) { l ->
    Text(if (l != null) stringResource(R.string.percent, l) else "—", style = style, color = color)
  }
}

/**
 * [large] grows the ring and shows more mini rings when there's room; [vertical] stacks the ring above the
 * summary for the narrow side pane on expanded windows.
 */
@Composable
private fun Overview(devices: List<BtDevice>, modifier: Modifier = Modifier, large: Boolean = false, vertical: Boolean = false) {
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
    val ringSize = if (large) 160.dp else 132.dp
    val ring = @Composable {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ringSize)) {
        CircularWavyProgressIndicator(
          progress = animatedProgress(level),
          modifier = Modifier.size(ringSize).nudge(level <= 15),
          color = onContainer,
          trackColor = onContainer.copy(alpha = 0.12f),
        )
        AnimatedPercent(
          level,
          style = if (large) MaterialTheme.typography.displaySmallEmphasized else MaterialTheme.typography.headlineLargeEmphasized,
          color = onContainer,
        )
      }
    }
    val summary = @Composable {
      Text(
        if (level > 30) stringResource(R.string.overview_all_good) else stringResource(R.string.overview_lowest, lowest.name, level),
        style = if (large) MaterialTheme.typography.headlineSmallEmphasized else MaterialTheme.typography.titleLargeEmphasized,
        textAlign = if (vertical) TextAlign.Center else TextAlign.Start,
      )
      if (reporting.size > 1) {
        Spacer(Modifier.height(16.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp, if (vertical) Alignment.CenterHorizontally else Alignment.Start),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          StatPill(stringResource(R.string.overview_highest, reporting.maxOf { it.battery!! }), onContainer)
        }
      }
    }
    if (vertical) {
      Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ring()
        Spacer(Modifier.height(20.dp))
        summary()
      }
    } else {
      // Centred as a group so a wide card doesn't leave an empty right half.
      Row(
        Modifier.fillMaxWidth().padding(if (large) 28.dp else 24.dp),
        horizontalArrangement = Arrangement.spacedBy(if (large) 40.dp else 20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ring()
        Column(Modifier.weight(1f, fill = !large)) { summary() }
      }
    }
  }
}

/** Small pill-shaped statistic inside the overview. */
@Composable
private fun StatPill(text: String, onContainer: Color) {
  Surface(shape = CircleShape, color = onContainer.copy(alpha = 0.1f), contentColor = onContainer) {
    Text(text, style = MaterialTheme.typography.labelLargeEmphasized, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
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
internal fun DeviceIcon(device: BtDevice, size: Int = 56) {
  val (container, onContainer) = levelColors(if (device.connected) device.battery else null)
  Box(
    contentAlignment = Alignment.Center,
    modifier = Modifier.size(size.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(container),
  ) {
    Icon(painterResource(device.kind.icon), null, tint = onContainer, modifier = Modifier.size((size * 0.46f).dp))
  }
}

/** Medium widget: a device that needs attention, with a big percentage and a wavy level bar. */
@Composable
private fun DeviceCard(device: BtDevice, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val level = device.shownLevel ?: return
  val accent by animateColorAsState(levelAccent(level), MaterialTheme.motionScheme.defaultEffectsSpec(), label = "accent")
  Card(
    onClick = onClick,
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
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (device.connected) Connected else MaterialTheme.colorScheme.outline))
            Spacer(Modifier.width(6.dp))
            Text(
              if (device.connected) stringResource(R.string.connected) else lastSeenText(device),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        AnimatedPercent(
          level,
          style = MaterialTheme.typography.displaySmallEmphasized,
          color = accent,
          modifier = Modifier.nudge(level <= 15 || level == 100),
        )
      }
      Spacer(Modifier.height(16.dp))
      LinearWavyProgressIndicator(
        progress = animatedProgress(level),
        modifier = Modifier.fillMaxWidth().height(14.dp),
        color = accent,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        amplitude = { if (device.connected) 1f else 0f }, // flat when it's a remembered level, not a live one
      )
    }
  }
}

/** Small widget: ring with the device icon inside, percentage and name. For the "everything else" devices. */
@Composable
private fun DeviceTile(device: BtDevice, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val level = device.shownLevel ?: return
  val accent by animateColorAsState(levelAccent(level), MaterialTheme.motionScheme.defaultEffectsSpec(), label = "accent")
  Surface(
    onClick = onClick,
    modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
    shape = RoundedCornerShape(28.dp),
    color = MaterialTheme.colorScheme.surfaceBright,
  ) {
    Column(Modifier.padding(vertical = 16.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Box(contentAlignment = Alignment.Center, modifier = Modifier.nudge(level <= 15 || level == 100)) {
        CircularWavyProgressIndicator(
          progress = animatedProgress(level),
          modifier = Modifier.size(64.dp),
          color = accent,
          trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
          amplitude = { if (device.connected) 1f else 0f },
        )
        Icon(painterResource(device.kind.icon), null, Modifier.size(24.dp), tint = accent)
      }
      Spacer(Modifier.height(10.dp))
      AnimatedPercent(level, style = MaterialTheme.typography.titleLargeEmphasized, color = accent)
      Text(
        device.name,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!device.connected) {
        Text(
          lastSeenText(device),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.outline,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

private val Connected = Color(0xFF2E9E5B)

/** "Last seen 2 hours ago", or "just now" within the minute. */
@Composable
private fun lastSeenText(device: BtDevice): String {
  val seen = device.lastSeen ?: return stringResource(R.string.not_connected)
  val ago = if (System.currentTimeMillis() - seen < DateUtils.MINUTE_IN_MILLIS) stringResource(R.string.just_now)
    else DateUtils.getRelativeTimeSpanString(seen).toString().replaceFirstChar { it.lowercase() } // "Yesterday" mid-sentence
  return stringResource(R.string.last_seen, ago)
}

/** Shown above the remembered levels, so they can't be mistaken for live ones. */
@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
  Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier.size(44.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.secondary),
        contentAlignment = Alignment.Center,
      ) {
        Icon(painterResource(R.drawable.ic_bluetooth_off), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSecondary)
      }
      Spacer(Modifier.width(14.dp))
      Column {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMediumEmphasized)
        Text(
          stringResource(R.string.offline_body),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
        )
      }
    }
  }
}

/** Wide widget: connection status for a device that doesn't report battery, so it doesn't need a whole card. */
@Composable
private fun DeviceStrip(device: BtDevice, modifier: Modifier = Modifier, onClick: () -> Unit) {
  Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceBright) {
    Row(Modifier.padding(start = 10.dp, end = 24.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      DeviceIcon(device, size = 44)
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text(device.name, style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(8.dp).clip(CircleShape).background(Connected))
          Spacer(Modifier.width(6.dp))
          Text(
            stringResource(R.string.connected) + " · " + stringResource(R.string.battery_unavailable),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/** Pill quick actions; the notification toggle morphs shape when switched (expressive ToggleButton). */
@Composable
private fun QuickActions(notifyOn: Boolean, onNotifyChange: (Boolean) -> Unit, onBluetoothSettings: () -> Unit, center: Boolean) {
  FlowRow(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp, if (center) Alignment.CenterHorizontally else Alignment.Start),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    FilledTonalButton(onClick = onBluetoothSettings, shapes = ButtonDefaults.shapes()) {
      Icon(painterResource(R.drawable.ic_bluetooth), null, Modifier.size(ButtonDefaults.IconSize))
      Spacer(Modifier.width(ButtonDefaults.IconSpacing))
      Text(stringResource(R.string.action_bluetooth_settings))
    }
    TonalToggleButton(checked = notifyOn, onCheckedChange = onNotifyChange) {
      Icon(painterResource(R.drawable.ic_notification), null, Modifier.size(ButtonDefaults.IconSize))
      Spacer(Modifier.width(ButtonDefaults.IconSpacing))
      Text(stringResource(R.string.action_notification))
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
internal fun groupedShape(index: Int, count: Int): Shape {
  val top = if (index == 0) 24.dp else 6.dp
  val bottom = if (index == count - 1) 24.dp else 6.dp
  return RoundedCornerShape(top, top, bottom, bottom)
}

@Composable
private fun PairedRow(device: BtDevice, shape: Shape, modifier: Modifier = Modifier, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    modifier = modifier,
    shape = shape,
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
private fun Message(
  icon: Int,
  title: Int,
  body: Int,
  shape: RoundedPolygon,
  spin: Boolean = false,
  action: (@Composable () -> Unit)? = null,
) {
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
        Modifier.fillMaxSize().rotate(rotation).clip(shape.toShape())
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
      modifier = Modifier.widthIn(max = 480.dp),
    )
    if (action != null) {
      Spacer(Modifier.height(24.dp))
      action()
    }
  }
}

/** Icon picker: tap a device to pin its icon. The chosen one morphs from a circle into the app's cookie shape. */
@Composable
private fun IconSheet(device: BtDevice, onDismiss: () -> Unit) {
  val repo = LocalContext.current.repository
  val haptics = LocalHapticFeedback.current
  var pinned by remember(device.address) { mutableStateOf(repo.kindOverride(device.address)) }
  fun pick(kind: DeviceKind?) {
    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    pinned = kind
    repo.setKind(device.address, kind)
  }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        DeviceIcon(device, size = 56)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
          Text(device.name, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(stringResource(R.string.icon_title), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ToggleButton(checked = pinned == null, onCheckedChange = { if (it) pick(null) }) {
          Text(stringResource(R.string.icon_auto))
        }
      }
      Spacer(Modifier.height(24.dp))
      FlowRow(
        Modifier.fillMaxWidth(),
        maxItemsInEachRow = 4,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        DeviceKind.entries.forEach { kind ->
          KindOption(kind, selected = (pinned ?: device.kind) == kind, Modifier.weight(1f)) { pick(kind) }
        }
      }
    }
  }
}

@Composable
private fun KindOption(kind: DeviceKind, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val progress by animateFloatAsState(if (selected) 1f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "morph")
  val container by animateColorAsState(
    if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    MaterialTheme.motionScheme.defaultEffectsSpec(), label = "container",
  )
  val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
  Column(
    modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      Modifier.size(60.dp).clip(remember(progress) { MorphShape(CircleToCookie, progress) }).background(container),
      contentAlignment = Alignment.Center,
    ) {
      Icon(painterResource(kind.icon), null, tint = content, modifier = Modifier.size(26.dp))
    }
    Spacer(Modifier.height(6.dp))
    Text(
      stringResource(kind.label),
      style = if (selected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
      color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
    )
  }
}

private val CircleToCookie = Morph(MaterialShapes.Circle.normalized(), MaterialShapes.Cookie9Sided.normalized())

/** A [Morph] frozen at [progress], scaled to the component's size. */
private class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
    val path = morph.toPath(progress)
    path.transform(android.graphics.Matrix().apply { setScale(size.width, size.height) })
    return Outline.Generic(path.asComposePath())
  }
}

@Composable
private fun SettingsSheet(status: BtStatus, notifyOn: Boolean, onNotifyChange: (Boolean) -> Unit, onDismiss: () -> Unit) {
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
              checked = notifyOn,
              enabled = status != BtStatus.NoPermission && status != BtStatus.NoAdapter,
              onCheckedChange = onNotifyChange,
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
