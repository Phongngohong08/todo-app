package com.example.todoapplication.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun Test(
    pick:()-> Unit
){
    Box(modifier = Modifier.fillMaxSize()) {
        Button(onClick = pick, modifier = Modifier.align(Alignment.Center)) {
            Text("hẹn giờ")
        }
    }
}