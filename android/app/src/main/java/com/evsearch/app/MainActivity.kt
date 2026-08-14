package com.evsearch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.evsearch.app.data.local.AppDatabase
import com.evsearch.app.data.api.BffApiService
import com.evsearch.app.data.repository.ChargerRepository
import com.evsearch.app.presentation.detail.StationDetailScreen
import com.evsearch.app.presentation.detail.StationDetailViewModel
import com.evsearch.app.presentation.map.MapScreen
import com.evsearch.app.presentation.map.MapViewModel
import com.evsearch.app.presentation.saved.SavedChargersScreen
import com.evsearch.app.presentation.saved.SavedChargersViewModel
import com.evsearch.app.presentation.theme.EVSearchTheme
import com.evsearch.app.widget.ChargerWidgetReceiver

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        }

        val initialStatId = intent?.getStringExtra("statId")

        setContent {
            EVSearchTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
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
        lifecycleScope.launch {
            com.evsearch.app.widget.WidgetUpdateHelper.updateAllWidgets(applicationContext)
        }
    }
}

/**
 * Galaxy Fold / One UI 9.0 Adaptive Layout with Bottom Tabs:
 * - Unfolded / Wide Screen (>600dp): Map left pane, Detail right pane
 * - Folded / Compact Screen (<=600dp): Tab navigation (Map / Saved Widgets) + Detail stack
 */
@Composable
fun FoldableAdaptiveAppNavigation(
    repository: ChargerRepository,
    initialStatId: String? = null
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 600.dp
        var selectedTab by remember { mutableIntStateOf(0) }

        if (isWideScreen) {
            // Galaxy Fold Unfolded Dual-Pane Mode
            var selectedStatId by remember { mutableStateOf(initialStatId) }
            val mapViewModel: MapViewModel = viewModel(
                factory = MapViewModel.Factory(repository)
            )
            val savedViewModel: SavedChargersViewModel = viewModel(
                factory = SavedChargersViewModel.Factory(repository)
            )

            BackHandler(enabled = selectedStatId != null) {
                selectedStatId = null
            }

            Row(modifier = Modifier.fillMaxSize()) {
                // Left Pane: Map Screen
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    MapScreen(
                        viewModel = mapViewModel,
                        onStationClick = { statId -> selectedStatId = statId }
                    )
                }

                // Right Pane: Saved Widget Chargers + Station Detail
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
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
                        // 상세 미선택 시 위젯 충전기 관리 목록을 오른쪽 패널에 표시
                        SavedChargersScreen(
                            viewModel = savedViewModel,
                            onStationClick = { statId -> selectedStatId = statId }
                        )
                    }
                }
            }
        } else {
            // Compact Mode: Bottom Tab Navigation (Map / Saved Widgets)
            val navController = rememberNavController()
            // 현재 백스택 엔트리의 라우트를 직접 관찰 -> 탭 바 표시 여부 결정
            val currentBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = currentBackStackEntry?.destination?.route

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    OneUI9BottomTabBar(
                        selectedTab = selectedTab,
                        onTabSelected = { tab ->
                            android.util.Log.d("MainActivity", "onTabSelected: tab=$tab, currentRoute=$currentRoute")
                            selectedTab = tab
                            // 상세 화면에서 지도 탭으로 돌아올 때 백스택 정리
                            if (tab == 0 && currentRoute?.startsWith("detail") == true) {
                                android.util.Log.d("MainActivity", "popBackStack to map")
                                navController.popBackStack("map", inclusive = false)
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (selectedTab) {
                        0 -> CompactMapNavHost(
                            repository = repository,
                            navController = navController,
                            initialStatId = initialStatId
                        )
                        else -> {
                            val savedViewModel: SavedChargersViewModel = viewModel(
                                factory = SavedChargersViewModel.Factory(repository)
                            )
                            SavedChargersScreen(
                                viewModel = savedViewModel,
                                onStationClick = { statId ->
                                    selectedTab = 0
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

/**
 * Compact 단일 패널 모드의 지도 ↔ 상세 내비게이션
 */
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
            val mapViewModel: MapViewModel = viewModel(
                factory = MapViewModel.Factory(repository)
            )
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
 * Samsung One UI 9.0 Styled Bottom Tab Bar
 * - 28dp 상단 곡률 컨테이너, 틸 액센트 인디케이터
 */
@Composable
private fun OneUI9BottomTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 3.dp
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp
        ) {
            NavigationBarItem(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "충전소 검색"
                    )
                },
                label = {
                    Text(
                        "검색",
                        fontSize = 12.sp,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            )
            NavigationBarItem(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "위젯 충전기"
                    )
                },
                label = {
                    Text(
                        "위젯",
                        fontSize = 12.sp,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                )
            )
        }
    }
}
