package com.redwind.hyperorig.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.redwind.hyperorig.R
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.extra.SuperDropdown
import top.yukonga.miuix.kmp.extra.SuperSwitch

@Composable
fun SettingsPage(
    modifier: Modifier = Modifier,
    themeMode: MutableState<Int> = mutableStateOf(0),
    onThemeModeChange: (Int) -> Unit = {},
    openorig: MutableState<Boolean> = mutableStateOf(false),
    onOpenorigChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val showorigWarning = remember { mutableStateOf(false) }
    val themeOptions = listOf(
        stringResource(R.string.theme_follow_system),
        stringResource(R.string.theme_light),
        stringResource(R.string.theme_dark)
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                SuperDropdown(
                    title = stringResource(R.string.theme_title),
                    items = themeOptions,
                    selectedIndex = themeMode.value,
                    onSelectedIndexChange = { onThemeModeChange(it) }
                )
            }
        }

        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 0.dp)) {
                SuperSwitch(
                    title = stringResource(R.string.open_orig),
                    summary = stringResource(R.string.open_orig_summary),
                    checked = openorig.value,
                    onCheckedChange = {
                        if (it) {
                            showorigWarning.value = true
                        } else {
                            onOpenorigChange(false)
                        }
                    }
                )
            }
        }

        item {
            HorizontalDivider(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 12.dp, bottom = 12.dp)
            )
        }

        item {
            Text(
                text = "我们",
                modifier = Modifier.padding(start = 28.dp, top = 2.dp, bottom = 0.dp),
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                BasicComponent(
                    title = "开发者",
                )
                BasicComponent(
                    title = "KiriChen | 陈有为",
                    summary = "追风团队成员",
                    onClick = {
                        Intent(Intent.ACTION_VIEW).apply {
                            this.data = Uri.parse("https://www.coolapk.com/u/22989975")
                            context.startActivity(this)
                        }
                    }
                )
                BasicComponent(
                    title = "加入内部测试、问题报告",
                )
                BasicComponent(
                    title = "陈有为私房菜",
                    summary = "官方 Q Group",
                    onClick = {
                        Intent(Intent.ACTION_VIEW).apply {
                            this.data = Uri.parse("https://qun.qq.com/universal-share/share?ac=1&authKey=veiuXz4u6NenB8EoNjITTU5l%2Bzs8i1dbgL1w65GRBaP2Uj4z734aJKhPcgt0A2P6&busi_data=eyJncm91cENvZGUiOiI3NDEyNjcyOTgiLCJ0b2tlbiI6IlNNbkVGekFYaUVxRVZicnRUQ0ozRGRoS2dpNDdack9oUzhaVGVjTGNwZzBoN2ZuRjFPdzJSc3ZhYlA4UjZJNU4iLCJ1aW4iOiIxODQxOTM4MDQwIn0%3D&data=8ODFZUZTUtydXUELqSTZnuyFLThRvTZ3Ail6wXx3-VMJr8B14JSzrX2SMI5Q0_yF5MqzKrm2kq8LA7IZ4ci1Cw&svctype=4&tempid=h5_group_info")
                            context.startActivity(this)
                        }
                    }
                )
            }
        }
        item {
            Text(
                text = "开放",
                modifier = Modifier.padding(start = 28.dp, top = 0.dp, bottom = 0.dp),
                fontSize = 14.sp,
                color = Color.Gray
            )
        }

        item {
            Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                BasicComponent(
                    title = "项目开放",
                )
                BasicComponent(
                    title = "HyperOriG",
                    summary = "https://github.com/KiriChen-Wind/HyperOriG",
                    onClick = {
                        Intent(Intent.ACTION_VIEW).apply {
                            this.data = Uri.parse("https://github.com/KiriChen-Wind/HyperOriG")
                            context.startActivity(this)
                        }
                    }
                )
                BasicComponent(
                    title = "本项目引用的开放代码",
                )
                BasicComponent(
                    title = "HyperPods",
                    summary = "https://github.com/Art-Chen/HyperPods",
                    onClick = {
                        Intent(Intent.ACTION_VIEW).apply {
                            this.data = Uri.parse("https://github.com/Art-Chen/HyperPods")
                            context.startActivity(this)
                        }
                    }
                )
                BasicComponent(
                    title = "OppoPods",
                    summary = "https://github.com/Leaf-lsgtky/OppoPods",
                    onClick = {
                        Intent(Intent.ACTION_VIEW).apply {
                            this.data = Uri.parse("https://github.com/Leaf-lsgtky/OppoPods")
                            context.startActivity(this)
                        }
                    }
                )
                BasicComponent(
                    title = "NiceHCK Controller",
                    summary = "https://github.com/ZaeXT/NiceHCK_Controller",
                    onClick = {
                        Intent(Intent.ACTION_VIEW).apply {
                            this.data = Uri.parse("https://github.com/ZaeXT/NiceHCK_Controller")
                            context.startActivity(this)
                        }
                    }
                )
                BasicComponent(
                    title = "Miuix",
                    summary = "https://github.com/compose-miuix-ui/miuix",
                    onClick = {
                        Intent(Intent.ACTION_VIEW).apply {
                            this.data = Uri.parse("https://github.com/compose-miuix-ui/miuix")
                            context.startActivity(this)
                        }
                    }
                )
                BasicComponent(
                    title = "Yuki Hook API",
                    summary = "https://github.com/HighCapable/YukiHookAPI",
                    onClick = {
                        Intent(Intent.ACTION_VIEW).apply {
                            this.data = Uri.parse("https://github.com/HighCapable/YukiHookAPI")
                            context.startActivity(this)
                        }
                    }
                )
            }
        }
    }

    SuperDialog(
        title = stringResource(R.string.orig_warning_title),
        show = showorigWarning,
        onDismissRequest = {
            showorigWarning.value = false
        }
    ) {
        Text(
            text = stringResource(R.string.orig_warning),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Start
        )
        TextButton(
            text = stringResource(R.string.confirm),
            onClick = {
                showorigWarning.value = false
                onOpenorigChange(true)
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColorsPrimary()
        )
    }
}
