package com.example.todoapplication.data.model

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AlarmItem(
    val id: Int? = null,
    val time: LocalDateTime?=null,
    val message: String?=null,
    val isEnable: Boolean = true
){

    companion object{
        fun getFormattedTime(time: LocalDateTime): String {
            val formatter = DateTimeFormatter.ofPattern("H:mm") // "H" cho 24h, "h" cho 12h
            return time.format(formatter) ?: ""
        }

        fun formatLocalDateTime(time: LocalDateTime?): String {
            if (time == null) return ""

            // Định dạng: Giờ:Phút Ngày/Tháng/Năm (Ví dụ: 14:45 03/06/2026)
            val formatter = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", Locale.getDefault())

            return time.format(formatter)
        }
    }

}