package com.keepdev.routeemock

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.roundToInt

private val SPEED_PRESETS = listOf(30f, 60f, 120f, 300f)
private const val MAX_SPEED = 300f

/** 지도를 흑백으로 바꾼 뒤 반전, 루티 다크 지도 느낌 */
private val DARK_TILES = android.graphics.ColorMatrixColorFilter(
    floatArrayOf(
        -0.24f, -0.47f, -0.09f, 0f, 228f,
        -0.24f, -0.47f, -0.09f, 0f, 230f,
        -0.24f, -0.47f, -0.09f, 0f, 238f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

class MainActivity : ComponentActivity() {
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        // 다크 화면이라 상태바·내비게이션바 아이콘을 밝게
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        val wanted = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.launch(wanted.toTypedArray())

        setContent {
            RouteeTheme {
                Surface(Modifier.fillMaxSize(), color = Routee.Black) { MockApp() }
            }
        }
    }
}

private fun isMockApp(context: Context): Boolean {
    val ops = context.getSystemService(AppOpsManager::class.java)
    val mode = if (Build.VERSION.SDK_INT >= 29) {
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName)
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

@Composable
private fun MockApp() {
    val context = LocalContext.current
    val status by MockController.status.collectAsStateWithLifecycle()
    val (attribution, routes) = remember { RouteCatalog.load(context) }
    var mockAllowed by remember { mutableStateOf(isMockApp(context)) }
    var picked by remember { mutableStateOf<MockRoute?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            mockAllowed = isMockApp(context)
            delay(2_000)
        }
    }

    val route = status.route
    if (status.running && route != null) {
        PlayingScreen(status, route)
    } else {
        RouteListScreen(routes, attribution, mockAllowed, status.error, onPick = { picked = it })
    }

    picked?.let {
        StartRouteDialog(
            route = it,
            initial = MockController.engine.settings,
            onDismiss = { picked = null },
            onStart = { settings ->
                picked = null
                MockController.play(context, it, settings)
            },
        )
    }
}

// ───────────────────────── 경로 목록 ─────────────────────────

@Composable
private fun RouteListScreen(
    routes: List<MockRoute>,
    attribution: String,
    mockAllowed: Boolean,
    error: String?,
    onPick: (MockRoute) -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Routee Mock", style = Routee.displaySb28, color = Routee.Mint300)
        Text("경로를 고르면 그 산을 빠르게 지나가요.\n재생 중에 루티나 네이버 지도를 열어 확인하세요.", style = Routee.labelM14, color = Routee.Grey200)
        Spacer(Modifier.height(4.dp))

        if (!mockAllowed) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Routee.Grey900)
                    .border(1.dp, Routee.Error, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("모의 위치 앱으로 지정되지 않았어요", style = Routee.labelSb16, color = Routee.Error)
                Text("개발자 옵션 → 모의 위치 앱 선택 → 루티 모의위치", style = Routee.labelM14, color = Routee.Grey200)
                SecondaryButton("개발자 옵션 열기") {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                }
            }
        }
        error?.let { Text(it, style = Routee.labelM14, color = Routee.Error) }

        routes.forEach { route -> RouteCard(route, enabled = mockAllowed) { onPick(route) } }

        // 테스트가 끝나고 네이버 지도 등에서 진짜 위치가 안 잡힐 때
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Routee.Grey900)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("진짜 위치로 돌아가기", style = Routee.labelSb16, color = Routee.White)
            Text(
                "남아 있는 가짜 위치를 모두 지워요. 그래도 안 잡히면 개발자 옵션의 모의 위치 앱을 '없음'으로 바꾸고, 네이버 지도를 껐다 켜세요.",
                style = Routee.labelM14,
                color = Routee.Grey200,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("가짜 위치 지우기") {
                    MockService.resetToReal(context)
                    android.widget.Toast.makeText(context, "가짜 위치를 지웠어요", android.widget.Toast.LENGTH_SHORT).show()
                }
                SecondaryButton("개발자 옵션") {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "고도는 위성 지형 데이터(30m 격자)라 실제 정상보다 수십 m 낮아요. 여의도공원은 평지 확인용으로 20m 고정.",
            style = Routee.labelR12,
            color = Routee.Grey300,
        )
        Text(attribution, style = Routee.labelR12, color = Routee.Grey500)
    }
}

@Composable
private fun RouteCard(route: MockRoute, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Routee.Grey900)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(route.name, style = Routee.titleSb18, color = Routee.White)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Routee.Black60)
                .padding(vertical = 12.dp),
        ) {
            Metric("거리(km)", "%.2f".format(route.distanceMeters / 1000.0))
            Metric("시작 고도(m)", "${route.points.first().ele.roundToInt()}")
            Metric("최고 고도(m)", "${route.maxEle}")
        }
        Text(route.purpose, style = Routee.labelM14, color = Routee.Grey200)
    }
}

/** 루티 RouteeWorkoutMetricCard 의 한 칸 */
@Composable
private fun RowScope.Metric(label: String, value: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Routee.labelSb12, color = Routee.Mint300, maxLines = 1)
        Text(value, style = Routee.displaySb22, color = Routee.Mint300, maxLines = 1)
    }
}

// ───────────────────────── 경로 시작 창 ─────────────────────────

/** 위치 모방자의 「경로 시작」 창 구성 */
@Composable
private fun StartRouteDialog(
    route: MockRoute,
    initial: MockSettings,
    onDismiss: () -> Unit,
    onStart: (MockSettings) -> Unit,
) {
    var speed by remember { mutableStateOf(initial.speedKmh) }
    var loop by remember { mutableStateOf(initial.loopMode) }
    var editing by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Routee.Grey800,
        title = { Text("경로 시작", style = Routee.titleSb20, color = Routee.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(route.name, style = Routee.labelSb16, color = Routee.White)
                Text("거리: ${km(route.distanceMeters.toDouble())}", style = Routee.labelM14, color = Routee.Grey200)
                Row(
                    Modifier.fillMaxWidth().clickable { editing = true },
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(formatSpeed(speed), style = Routee.displaySb28, color = Routee.Mint300)
                    Text("  km/h", style = Routee.labelSb14, color = Routee.Mint300, modifier = Modifier.padding(bottom = 6.dp))
                    Spacer(Modifier.weight(1f))
                    Text("탭하여 편집", style = Routee.labelR12, color = Routee.Grey300, modifier = Modifier.padding(bottom = 6.dp))
                }
                SpeedSlider(speed) { speed = it }
                SpeedPresets(speed) { speed = it }
                Text("예상 시간 ${duration(route.distanceMeters / (speed / 3.6))}", style = Routee.labelM14, color = Routee.Grey200)
                Spacer(Modifier.height(4.dp))
                Text("반복", style = Routee.labelSb14, color = Routee.White)
                listOf(LoopMode.STOP to "중지", LoopMode.REVERSE to "반전", LoopMode.RESTART to "다시 시작").forEach { (mode, label) ->
                    Row(Modifier.fillMaxWidth().clickable { loop = mode }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = loop == mode,
                            onClick = { loop = mode },
                            colors = RadioButtonDefaults.colors(selectedColor = Routee.Mint300, unselectedColor = Routee.Grey300),
                        )
                        Text(label, style = Routee.labelM14, color = Routee.White)
                    }
                }
            }
        },
        confirmButton = {
            PrimaryButton("시작", Modifier.width(120.dp)) { onStart(initial.copy(speedKmh = speed, loopMode = loop)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소", style = Routee.labelSb16, color = Routee.Grey200) }
        },
    )

    if (editing) SpeedInputDialog(speed, onDismiss = { editing = false }, onDone = { speed = it; editing = false })
}

@Composable
private fun SpeedSlider(speed: Float, onChange: (Float) -> Unit) {
    Slider(
        value = speed.coerceAtMost(MAX_SPEED),
        onValueChange = { onChange((it / 5).roundToInt() * 5f) },
        valueRange = 5f..MAX_SPEED,
        colors = SliderDefaults.colors(
            thumbColor = Routee.Mint300,
            activeTrackColor = Routee.Mint500,
            inactiveTrackColor = Routee.Grey600,
        ),
    )
}

@Composable
private fun SpeedPresets(speed: Float, onPick: (Float) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SPEED_PRESETS.forEach { kmh -> Chip("${kmh.roundToInt()}", selected = speed == kmh) { onPick(kmh) } }
    }
}

@Composable
private fun SpeedInputDialog(current: Float, onDismiss: () -> Unit, onDone: (Float) -> Unit) {
    var text by remember { mutableStateOf(formatSpeed(current)) }
    val value = text.toFloatOrNull()?.takeIf { it in 1f..1000f }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Routee.Grey800,
        title = { Text("속도 (km/h)", style = Routee.titleSb18, color = Routee.White) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = Routee.displaySb22,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(enabled = value != null, onClick = { value?.let(onDone) }) { Text("확인", style = Routee.labelSb16, color = Routee.Mint300) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소", style = Routee.labelSb16, color = Routee.Grey200) } },
    )
}

// ───────────────────────── 재생 화면 ─────────────────────────

/** 루티 운동 중 화면(RT-RUN-003) 배치: 다크 지도 위에 수치 카드, 하단 정지 버튼 */
@Composable
private fun PlayingScreen(status: MockStatus, route: MockRoute) {
    val context = LocalContext.current
    val engine = MockController.engine
    var settings by remember { mutableStateOf(engine.settings) }
    var editingSpeed by remember { mutableStateOf(false) }

    fun updateSettings(next: MockSettings) {
        settings = next
        engine.settings = next
    }

    Box(Modifier.fillMaxSize()) {
        RouteMap(route.points, current = status.lastFix?.let { LatLng(it.lat, it.lng) }, modifier = Modifier.fillMaxSize())

        // 상단: 경로 이름, 수치 카드, 속도
        Column(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(route.name, style = Routee.titleSb18, color = Routee.White, modifier = Modifier.weight(1f))
                StateBadge(status, route)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Routee.Black60)
                    .padding(top = 14.dp, bottom = 12.dp),
            ) {
                Metric("진행(km)", "%.2f".format(status.traveledMeters / 1000))
                Metric("지금 고도(m)", "${status.lastFix?.altitude?.roundToInt() ?: 0}")
                Metric("최고 고도(m)", "${status.maxAltitude.roundToInt()}")
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Routee.Black80)
                    .clickable { editingSpeed = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("속도 ", style = Routee.labelM14, color = Routee.Grey200)
                Text("${formatSpeed(settings.speedKmh)} km/h", style = Routee.labelSb14, color = Routee.Mint300)
                Text("  · 남은 ${duration((route.distanceMeters - status.traveledMeters).coerceAtLeast(0.0) / (settings.speedKmh / 3.6))}", style = Routee.labelM14, color = Routee.Grey200)
            }
        }

        // 하단: 재생 버튼
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Routee.Black80)
                .safeDrawingPadding()
                .padding(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundButton("⏮") { engine.restart(); engine.playing = true }
                // 루티 운동 중 화면의 흰색 "정지" 버튼 모양
                Box(
                    Modifier
                        .height(52.dp)
                        .width(160.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Routee.White)
                        .clickable {
                            if (engine.playing) {
                                engine.playing = false
                            } else {
                                if (engine.finished) engine.restart()
                                engine.playing = true
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (status.playing) "❚❚  멈춤" else "▶  재개", style = Routee.labelSb16, color = Routee.Black)
                }
                RoundButton("■", background = Routee.Grey600) { MockService.stop(context) }
            }
        }
    }

    if (editingSpeed) {
        AlertDialog(
            onDismissRequest = { editingSpeed = false },
            containerColor = Routee.Grey800,
            title = { Text("속도 바꾸기", style = Routee.titleSb18, color = Routee.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${formatSpeed(settings.speedKmh)} km/h", style = Routee.displaySb28, color = Routee.Mint300)
                    SpeedSlider(settings.speedKmh) { updateSettings(settings.copy(speedKmh = it)) }
                    SpeedPresets(settings.speedKmh) { updateSettings(settings.copy(speedKmh = it)) }
                }
            },
            confirmButton = { TextButton(onClick = { editingSpeed = false }) { Text("닫기", style = Routee.labelSb16, color = Routee.Mint300) } },
        )
    }
}

@Composable
private fun StateBadge(status: MockStatus, route: MockRoute) {
    val (label, color) = when {
        !status.sending -> "위치 끊김" to Routee.Error
        status.playing -> "재생 중" to Routee.Mint300
        status.traveledMeters >= route.distanceMeters - 1 -> "도착" to Routee.Lime300
        else -> "멈춤" to Routee.Grey200
    }
    Text(
        label,
        style = Routee.labelSb12,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Routee.Black80)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ───────────────────────── 공통 컴포넌트 ─────────────────────────

/** 루티 RouteeCtaPrimaryButton */
@Composable
private fun PrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(Routee.Mint300)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Routee.labelSb16, color = Routee.Black) }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Routee.SurfaceTinted)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) { Text(text, style = Routee.labelSb14, color = Routee.Mint300) }
}

@Composable
private fun Chip(text: String, selected: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        style = Routee.labelSb14,
        color = if (selected) Routee.Black else Routee.Mint300,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Routee.Mint300 else Routee.SurfaceTinted)
            .border(1.dp, Routee.Mint300.copy(alpha = 0.4f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun RoundButton(text: String, background: Color = Routee.Grey800, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Routee.labelSb16, color = Routee.White) }
}

/** 오픈스트리트맵 타일을 반전해서 루티의 다크 지도처럼 보이게 */
@SuppressLint("ClickableViewAccessibility")
@Composable
private fun RouteMap(points: List<RoutePoint>, current: LatLng?, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.clipToBounds(),
        factory = { context ->
            MapView(context).apply {
                setMultiTouchControls(true)
                zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                overlayManager.tilesOverlay.setColorFilter(DARK_TILES)
                overlayManager.tilesOverlay.loadingBackgroundColor = android.graphics.Color.rgb(0x1D, 0x1F, 0x24)
                val geo = points.map { GeoPoint(it.lat, it.lng) }
                overlays.add(
                    Polyline().apply {
                        setPoints(geo)
                        outlinePaint.color = android.graphics.Color.rgb(0x01, 0xDE, 0xEF)
                        outlinePaint.strokeWidth = 12f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    },
                )
                addOnFirstLayoutListener { _, _, _, _, _ ->
                    if (geo.size >= 2) zoomToBoundingBox(BoundingBox.fromGeoPoints(geo).increaseByScale(1.6f), false)
                }
            }
        },
        update = { map ->
            map.overlays.removeAll { it is Marker }
            current?.let {
                map.overlays.add(
                    Marker(map).apply {
                        position = GeoPoint(it.lat, it.lng)
                        icon = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(android.graphics.Color.rgb(0x01, 0xDE, 0xEF))
                            setStroke(8, android.graphics.Color.WHITE)
                            setSize(56, 56)
                        }
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setInfoWindow(null)
                    },
                )
            }
            map.invalidate()
        },
    )
}

private fun km(meters: Double) = if (meters >= 1000) "%.2fkm".format(meters / 1000) else "${meters.roundToInt()}m"

private fun formatSpeed(kmh: Float) = if (kmh % 1f == 0f) kmh.roundToInt().toString() else "%.1f".format(kmh)

private fun duration(seconds: Double): String {
    val s = seconds.roundToInt()
    return when {
        s >= 3600 -> "${s / 3600}시간 ${s % 3600 / 60}분"
        s >= 60 -> "${s / 60}분 ${s % 60}초"
        else -> "${s}초"
    }
}
