package com.example.todoapplication.ui.screens

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.todoapplication.data.local.PlaceEntity
import com.example.todoapplication.data.local.TaskLocationEntity
import com.example.todoapplication.data.location.LocationReminders
import com.example.todoapplication.data.location.LocationTrigger
import com.example.todoapplication.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * Mục "📍 Nhắc theo địa điểm": chọn địa điểm đã lưu + "khi đến" / "khi rời". Xin quyền theo đúng 2 bước của
 * Android 10+: vị trí khi dùng app (để lấy vị trí hiện tại) rồi mới vị trí NỀN (để geofence chạy khi app đóng).
 */
@Composable
fun LocationSection(
    enabled: Boolean,
    places: List<PlaceEntity>,
    current: TaskLocationEntity?,
    onSet: (placeId: String, trigger: String) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var addingPlace by remember { mutableStateOf(false) }
    var askBackground by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<String, String>?>(null) } // chờ cấp quyền rồi mới đặt

    val backgroundPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.let { (place, trigger) -> onSet(place, trigger) }
        pending = null
        if (!granted) Toast.makeText(context, "Chưa có quyền vị trí nền — nhắc chỉ hoạt động khi bạn cấp \"Luôn cho phép\"", Toast.LENGTH_LONG).show()
        LocationReminders.sync(context, force = true)
    }
    val foregroundPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            if (!LocationReminders.hasBackgroundPermission(context)) askBackground = true else {
                pending?.let { (place, trigger) -> onSet(place, trigger) }
                pending = null
            }
        } else {
            pending = null
            Toast.makeText(context, "Cần quyền vị trí để nhắc theo địa điểm", Toast.LENGTH_SHORT).show()
        }
    }

    fun ensurePermissionsThen(action: () -> Unit) {
        when {
            !LocationReminders.hasForegroundPermission(context) -> foregroundPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            !LocationReminders.hasBackgroundPermission(context) -> askBackground = true
            else -> action()
        }
    }

    if (!enabled) {
        Text("Lưu việc trước để đặt nhắc theo địa điểm.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    if (places.isEmpty()) {
        Text("Chưa có địa điểm nào. Lưu vị trí hiện tại thành \"Nhà\", \"Công ty\"…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            places.forEach { p ->
                FilterChip(
                    selected = current?.placeId == p.id,
                    onClick = {
                        val trigger = current?.trigger ?: LocationTrigger.ENTER
                        pending = p.id to trigger
                        ensurePermissionsThen { onSet(p.id, trigger); pending = null }
                    },
                    label = { Text("📍 " + p.name) }
                )
            }
        }
        if (current != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                FilterChip(selected = current.trigger == LocationTrigger.ENTER, onClick = { onSet(current.placeId, LocationTrigger.ENTER) }, label = { Text("Khi đến") })
                FilterChip(selected = current.trigger == LocationTrigger.EXIT, onClick = { onSet(current.placeId, LocationTrigger.EXIT) }, label = { Text("Khi rời") })
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onClear) { Icon(Icons.Default.Close, contentDescription = "Bỏ nhắc theo địa điểm") }
            }
        }
    }
    TextButton(onClick = {
        if (LocationReminders.hasForegroundPermission(context)) addingPlace = true
        else foregroundPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }) {
        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Lưu vị trí hiện tại thành địa điểm")
    }

    if (askBackground) {
        AlertDialog(
            onDismissRequest = { askBackground = false; pending = null },
            title = { Text("Cho phép vị trí khi đóng app?") },
            text = {
                Text(
                    "Để nhắc đúng lúc bạn đến / rời nơi này kể cả khi không mở app, hãy chọn \"Luôn cho phép\" ở màn tiếp theo. " +
                        "Vị trí chỉ dùng trên máy, không gửi lên máy chủ."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askBackground = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }) { Text("Tiếp tục") }
            },
            dismissButton = { TextButton(onClick = { askBackground = false; pending = null }) { Text("Để sau") } }
        )
    }

    if (addingPlace) {
        var name by remember { mutableStateOf("") }
        var radius by remember { mutableIntStateOf(200) }
        var saving by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { addingPlace = false },
            title = { Text("Địa điểm mới") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, label = { Text("Tên (vd: Công ty)") }, singleLine = true)
                    Text("Bán kính", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(100, 200, 500).forEach { r -> FilterChip(selected = radius == r, onClick = { radius = r }, label = { Text("${r}m") }) }
                    }
                    if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Dùng vị trí hiện tại của bạn.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && !saving, onClick = {
                    saving = true
                    scope.launch {
                        val loc = LocationReminders.currentLocation(context)
                        saving = false
                        if (loc == null) {
                            Toast.makeText(context, "Không lấy được vị trí — bật GPS rồi thử lại", Toast.LENGTH_SHORT).show()
                        } else {
                            ServiceLocator.placeRepository.addPlace(name, loc.latitude, loc.longitude, radius)
                            addingPlace = false
                        }
                    }
                }) { Text("Lưu") }
            },
            dismissButton = { TextButton(onClick = { addingPlace = false }) { Text("Hủy") } }
        )
    }
}
