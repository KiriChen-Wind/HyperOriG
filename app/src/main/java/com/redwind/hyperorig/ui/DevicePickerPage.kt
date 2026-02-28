package com.redwind.hyperorig.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.redwind.hyperorig.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme

@SuppressLint("MissingPermission")
@Composable
fun DevicePickerPage(onDeviceSelected: (BluetoothDevice) -> Unit) {
    val context = LocalContext.current
    var hasConnectPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasScanPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val connectPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasConnectPermission = granted }
    val scanPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasScanPermission = granted }

    // 下拉刷新状态
    val isRefreshing = remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        if (!hasConnectPermission) {
            connectPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (!hasScanPermission) {
            scanPermissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
        }
    }

    if (!hasConnectPermission || !hasScanPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.bt_permission_required))
                Spacer(Modifier.height(18.dp))
                TextButton(
                    text = stringResource(R.string.grant_permission),
                    onClick = {
                        if (!hasConnectPermission) {
                            connectPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                        if (!hasScanPermission) {
                            scanPermissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
                        }
                    },
                    modifier = Modifier.width(145.dp).height(54.dp)
                )
            }
        }
        return
    }

    val btManager = context.getSystemService(BluetoothManager::class.java)
    val adapter = btManager?.adapter
    
    // 刷新设备列表的函数
    fun refreshDevices() {
        isRefreshing.value = true
    }
    
    // 监听刷新状态变化
    LaunchedEffect(isRefreshing.value) {
        if (isRefreshing.value) {
            // 延迟一点时间模拟刷新
            kotlinx.coroutines.delay(1000)
            isRefreshing.value = false
        }
    }
    
    // 每五秒自动刷新设备列表
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000)
            refreshDevices()
        }
    }
    
    val connectedDevices = remember(hasConnectPermission, hasScanPermission, isRefreshing.value) {
        val bondedDevices = adapter?.bondedDevices ?: emptySet()
        val connected = mutableListOf<BluetoothDevice>()
        
        // 筛选出已连接的设备
        for (device in bondedDevices) {
            val isConnected = try {
                // 尝试通过反射获取设备的连接状态
                val method = BluetoothDevice::class.java.getMethod("isConnected")
                method.invoke(device) as? Boolean ?: false
            } catch (e: Exception) {
                false
            }
            
            if (isConnected) {
                connected.add(device)
            }
        }
        
        // 按设备名称排序，优先显示匹配的设备
        connected.sortedByDescending {
            val name = it.name?.lowercase() ?: ""
            name.contains("yuandao") || name.contains("orig") || name.contains("nicehck")
        }
    }
    
    // 分组设备：已发现的设备和未识别的设备
    val (discoveredDevices, unidentifiedDevices) = connectedDevices.partition {
        val name = it.name?.lowercase() ?: ""
        name.contains("yuandao") || name.contains("orig") || name.contains("nicehck")
    }

    Column(Modifier.fillMaxSize()) {
        // 页面标题
        Text(
            stringResource(R.string.select_device),
            color = MiuixTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 25.dp, top = 12.dp, bottom = 8.dp)
        )
        
        PullToRefresh(
            isRefreshing = isRefreshing.value,
            onRefresh = ::refreshDevices,
            pullToRefreshState = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            refreshTexts = listOf(
                "下拉以搜索设备",
                "松开以搜索设备",
                "正在搜索设备",
                "搜索完成"
            )
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                if (connectedDevices.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillParentMaxSize()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.no_paired_devices),
                                color = MiuixTheme.colorScheme.onBackground,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    // 已发现的设备部分
                    if (discoveredDevices.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.discovered_devices),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(start = 26.dp, top = 4.dp, bottom = 4.dp),
                                fontSize = 14.sp
                            )
                        }
                        items(discoveredDevices, key = { it.address }) { device ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                BasicComponent(
                                    title = device.alias ?: device.name ?: stringResource(R.string.unknown_device),
                                    summary = device.address,
                                    onClick = { onDeviceSelected(device) }
                                )
                            }
                        }
                    }
                    
                    // 未识别的设备部分
                    if (unidentifiedDevices.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.unidentified_devices),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(start = 26.dp, top = 4.dp, bottom = 4.dp),
                                fontSize = 14.sp
                            )
                        }
                        items(unidentifiedDevices, key = { it.address }) { device ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                BasicComponent(
                                    title = device.alias ?: device.name ?: stringResource(R.string.unknown_device),
                                    summary = device.address,
                                    onClick = { onDeviceSelected(device) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
