package com.evsearch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.evsearch.app.data.api.BffApiService
import com.evsearch.app.data.local.AppDatabase
import com.evsearch.app.data.repository.ChargerRepository
import com.evsearch.app.presentation.common.AppleHairline
import com.evsearch.app.presentation.common.ApplePillButton
import com.evsearch.app.presentation.common.PillStyle
import com.evsearch.app.presentation.detail.StationDetailScreen
import com.evsearch.app.presentation.detail.StationDetailViewModel
import com.evsearch.app.presentation.favorites.FavoritesScreen
import com.evsearch.app.presentation.favorites.FavoritesViewModel
import com.evsearch.app.presentation.map.MapScreen
import com.evsearch.app.presentation.map.MapViewModel
import com.evsearch.app.presentation.saved.SavedChargersScreen
import com.evsearch.app.presentation.saved.SavedChargersViewModel
import com.evsearch.app.presentation.theme.Apple
import com.evsearch.app.presentation.theme.EVSearchTheme
import com.evsearch.app.widget.ChargerWidgetReceiver
import com.evsearch.app.widget.WidgetSyncScheduler
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 시스템 바는 글로벌 내비와 같은 순수 검정
        val systemBarColor = android.graphics.Color.BLACK
        window.statusBarColor = systemBarColor
        window.navigationBarColor = systemBarColor
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        // Initialize Kakao Maps SDK v2
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
            val kakaoAppKey = appInfo.metaData?.getString("com.kakao.vectormap.APP_KEY") ?: "760c5fddd14c3352d7f4b889e880254f"
            com.kakao.vectormap.KakaoMapSdk.init(this, kakaoAppKey)
        } catch (e: Exception) {
            e.printStackTrace()
            com.kakao.vectormap.KakaoMapSdk.init(this, "760c5fddd14c3352d7f4b889e880254f")
        }

        val database = AppDatabase.getInstance(applicationContext)
        val apiService = BffApiService.create()
        val repository = ChargerRepository(apiService, database.savedChargerDao(), applicationContext)

        ChargerWidgetReceiver.scheduleBackgroundWork(applicationContext)

        lifecycleScope.launch {
            com.evsearch.app.widget.WidgetUpdateHelper.updateAllWidgets(applicationContext)
            // 앱 진입 시 서버 감시 대상(위젯·즐겨찾기)을 최신 상태로 맞춘다.
            repository.syncAlertSubscription()
        }

        // 빈자리 알림: 알림 채널 초기화 + FCM 토큰 확보/저장
        com.evsearch.app.alert.AlertNotifications.ensureChannel(applicationContext)
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    com.evsearch.app.alert.AlertPrefs.setToken(applicationContext, token)
                }
        } catch (e: Exception) {
            // Firebase 미설정 등: 알림 비활성 (앱 동작에는 영향 없음)
        }

        // 알림 탭으로 진입한 경우 해당 충전소 상세로 바로 이동
        val initialStatId = intent?.getStringExtra("open_statId") ?: intent?.getStringExtra("statId")

        setContent {
            EVSearchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Apple.C.Canvas
                ) {
                    FoldableAdaptiveAppNavigation(
                        repository = repository,
                        initialStatId = initialStatId
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WidgetSyncScheduler.syncNow(applicationContext)
        lifecycleScope.launch {
            com.evsearch.app.widget.WidgetUpdateHelper.updateAllWidgets(applicationContext)
        }
    }
}

private const val TAB_MAP = 0
private const val TAB_FAVORITES = 1
private const val TAB_WIDGET = 2

/**
 * 넓은 화면(폴더블 펼침): 지도 좌측 + 상세/목록 우측
 * 좁은 화면: 하단 탭 (검색 / 즐겨찾기 / 위젯) + 상세 스택
 */
@Composable
fun FoldableAdaptiveAppNavigation(
    repository: ChargerRepository,
    initialStatId: String? = null
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp
        var selectedTab by remember { mutableIntStateOf(TAB_MAP) }
        val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext

        if (isWideScreen) {
            var selectedStatId by remember { mutableStateOf(initialStatId) }
            var rightPaneTab by remember { mutableIntStateOf(TAB_FAVORITES) }
            val mapViewModel: MapViewModel = viewModel(factory = MapViewModel.Factory(repository))
            val savedViewModel: SavedChargersViewModel =
                viewModel(factory = SavedChargersViewModel.Factory(repository, appContext))
            val favoritesViewModel: FavoritesViewModel =
                viewModel(factory = FavoritesViewModel.Factory(repository, appContext))

            BackHandler(enabled = selectedStatId != null) { selectedStatId = null }

            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    MapScreen(
                        viewModel = mapViewModel,
                        onStationClick = { statId -> selectedStatId = statId }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Apple.C.Canvas)
                ) {
                    if (selectedStatId != null) {
                        val detailViewModel: StationDetailViewModel = viewModel(
                            key = selectedStatId,
                            factory = StationDetailViewModel.Factory(repository, selectedStatId!!)
                        )
                        StationDetailScreen(
                            viewModel = detailViewModel,
                            onBackClick = { selectedStatId = null }
                        )
                    } else {
                        // 두 pane의 헤더 높이를 맞추기 위해 pane 전환 컨트롤을 내비 안에 넣는다.
                        val paneSwitch: @Composable () -> Unit = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Apple.Sp.xxs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ApplePillButton(
                                    text = "즐겨찾기",
                                    compact = true,
                                    style = if (rightPaneTab == TAB_FAVORITES) PillStyle.Primary else PillStyle.Ghost,
                                    onClick = { rightPaneTab = TAB_FAVORITES }
                                )
                                ApplePillButton(
                                    text = "위젯",
                                    compact = true,
                                    style = if (rightPaneTab == TAB_WIDGET) PillStyle.Primary else PillStyle.Ghost,
                                    onClick = { rightPaneTab = TAB_WIDGET }
                                )
                            }
                        }

                        if (rightPaneTab == TAB_WIDGET) {
                            SavedChargersScreen(
                                viewModel = savedViewModel,
                                onStationClick = { statId -> selectedStatId = statId },
                                navLeading = paneSwitch
                            )
                        } else {
                            FavoritesScreen(
                                viewModel = favoritesViewModel,
                                onStationClick = { statId -> selectedStatId = statId },
                                navLeading = paneSwitch
                            )
                        }
                    }
                }
            }
        } else {
            val navController = rememberNavController()
            val currentBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = currentBackStackEntry?.destination?.route

            Scaffold(
                containerColor = Apple.C.Canvas,
                bottomBar = {
                    AppleBottomTabBar(
                        selectedTab = selectedTab,
                        onTabSelected = { tab ->
                            selectedTab = tab
                            if (tab == TAB_MAP && currentRoute?.startsWith("detail") == true) {
                                navController.popBackStack("map", inclusive = false)
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (selectedTab) {
                        TAB_MAP -> CompactMapNavHost(
                            repository = repository,
                            navController = navController,
                            initialStatId = initialStatId
                        )
                        TAB_FAVORITES -> {
                            val favoritesViewModel: FavoritesViewModel =
                                viewModel(factory = FavoritesViewModel.Factory(repository, appContext))
                            FavoritesScreen(
                                viewModel = favoritesViewModel,
                                onStationClick = { statId ->
                                    selectedTab = TAB_MAP
                                    navController.navigate("detail/$statId")
                                }
                            )
                        }
                        else -> {
                            val savedViewModel: SavedChargersViewModel =
                                viewModel(factory = SavedChargersViewModel.Factory(repository, appContext))
                            SavedChargersScreen(
                                viewModel = savedViewModel,
                                onStationClick = { statId ->
                                    selectedTab = TAB_MAP
                                    navController.navigate("detail/$statId")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Compact 단일 패널 모드의 지도 ↔ 상세 내비게이션 */
@Composable
private fun CompactMapNavHost(
    repository: ChargerRepository,
    navController: NavHostController,
    initialStatId: String?
) {
    NavHost(
        navController = navController,
        startDestination = if (initialStatId != null) "detail/$initialStatId" else "map"
    ) {
        composable("map") {
            val mapViewModel: MapViewModel = viewModel(factory = MapViewModel.Factory(repository))
            MapScreen(
                viewModel = mapViewModel,
                onStationClick = { statId -> navController.navigate("detail/$statId") }
            )
        }

        composable("detail/{statId}") { backStackEntry ->
            val statId = backStackEntry.arguments?.getString("statId") ?: return@composable
            val detailViewModel: StationDetailViewModel = viewModel(
                key = statId,
                factory = StationDetailViewModel.Factory(repository, statId)
            )
            StationDetailScreen(
                viewModel = detailViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Apple 계열 하단 내비 — 순수 검정 바, 상단 헤어라인 하나, 12sp 라벨, 단일 액센트.
 * M3 NavigationBar의 인디케이터·리플·라벨 애니메이션을 쓰지 않고 직접 배치한다.
 */
@Composable
private fun AppleBottomTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(Apple.C.Black)) {
        AppleHairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppleTabItem(TAB_MAP, selectedTab, onTabSelected, Icons.Default.Search, "검색", Modifier.weight(1f))
            AppleTabItem(TAB_FAVORITES, selectedTab, onTabSelected, Icons.Default.Star, "즐겨찾기", Modifier.weight(1f))
            AppleTabItem(TAB_WIDGET, selectedTab, onTabSelected, Icons.AutoMirrored.Filled.List, "위젯", Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppleTabItem(
    tab: Int,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val selected = selectedTab == tab
    val tint = if (selected) Apple.C.Accent else Apple.C.TextFaint
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onTabSelected(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(text = label, style = Apple.T.NavLink, color = tint, maxLines = 1)
    }
}
