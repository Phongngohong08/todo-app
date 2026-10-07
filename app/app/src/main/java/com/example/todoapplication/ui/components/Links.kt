package com.example.todoapplication.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Patterns
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.todoapplication.R
import com.example.todoapplication.i18n.str

/** Một liên kết nhận ra trong mô tả việc. */
data class DetectedLink(val kind: Kind, val label: String, val uri: Uri) {
    enum class Kind { WEB, EMAIL, PHONE }
}

/**
 * Nhận ra web / email / số điện thoại trong chữ người dùng gõ (bộ mẫu chuẩn của Android — android.util.Patterns).
 * Số điện thoại phải có ít nhất 9 chữ số để không nhầm ngày "2026-10-02" hay số tiền.
 */
fun extractLinks(text: String): List<DetectedLink> {
    if (text.isBlank()) return emptyList()
    val found = mutableListOf<Pair<IntRange, DetectedLink>>()
    fun taken(r: IntRange) = found.any { it.first.first <= r.last && r.first <= it.first.last }

    Patterns.EMAIL_ADDRESS.matcher(text).let { m ->
        while (m.find()) {
            val v = m.group()
            found += (m.start() until m.end()) to DetectedLink(DetectedLink.Kind.EMAIL, v, Uri.parse("mailto:$v"))
        }
    }
    Patterns.WEB_URL.matcher(text).let { m ->
        while (m.find()) {
            val r = m.start() until m.end()
            val v = m.group().trimEnd('.', ',', ')')
            // WEB_URL nhận cả "a.b" — chỉ lấy khi có http(s):// hoặc www.
            if (taken(r) || !(v.startsWith("http", true) || v.startsWith("www.", true))) continue
            val uri = Uri.parse(if (v.startsWith("http", true)) v else "https://$v")
            found += r to DetectedLink(DetectedLink.Kind.WEB, v, uri)
        }
    }
    Patterns.PHONE.matcher(text).let { m ->
        while (m.find()) {
            val r = m.start() until m.end()
            val v = m.group().trim()
            if (taken(r) || v.count { it.isDigit() } < 9) continue
            found += r to DetectedLink(DetectedLink.Kind.PHONE, v, Uri.parse("tel:" + v.filter { it.isDigit() || it == '+' }))
        }
    }
    return found.sortedBy { it.first.first }.map { it.second }.distinctBy { it.uri }
}

/** Hàng chip liên kết dưới mô tả: bấm để mở trình duyệt / gọi điện / soạn email. */
@Composable
fun LinkChips(text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val links = remember(text) { extractLinks(text) }
    if (links.isEmpty()) return
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        links.forEach { link ->
            AssistChip(
                onClick = {
                    val action = if (link.kind == DetectedLink.Kind.PHONE) Intent.ACTION_DIAL else Intent.ACTION_VIEW
                    try {
                        context.startActivity(Intent(action, link.uri))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(context, str(R.string.link_no_app), Toast.LENGTH_SHORT).show()
                    }
                },
                label = { Text(link.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = {
                    Icon(
                        when (link.kind) {
                            DetectedLink.Kind.WEB -> Icons.Default.Link
                            DetectedLink.Kind.EMAIL -> Icons.Default.Email
                            DetectedLink.Kind.PHONE -> Icons.Default.Call
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }
    }
}
