package com.example.todoapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.todoapplication.data.api.NetworkClient
import com.example.todoapplication.data.model.ChatInput
import com.example.todoapplication.ui.theme.BackgroundObsidian
import com.example.todoapplication.ui.theme.PrimaryIndigo
import com.example.todoapplication.ui.theme.SurfaceGlass
import kotlinx.coroutines.launch

// Client side representation of chat messages
data class ChatUIModel(
    val text: String,
    val isUser: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AICoachScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(context) }

    val messages = remember {
        mutableStateListOf(
            ChatUIModel(
                "Xin chào! Tôi là AI Coach của bạn. Tôi có thể giúp bạn sắp xếp kế hoạch, tìm động lực hoặc phân tích các thói quen trì hoãn. Hôm nay bạn muốn chia sẻ điều gì?",
                isUser = false
            )
        )
    }

    var messageText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Coach cá nhân", color = Color.White, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundObsidian)
            )
        },
        bottomBar = {
            Column {
                // Text Input Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceGlass)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Trò chuyện với AI Coach...", color = Color.Gray) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryIndigo,
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        maxLines = 3,
                        enabled = !isThinking
                    )

                    IconButton(
                        onClick = {
                            if (messageText.isBlank()) return@IconButton
                            val userMsg = messageText
                            messageText = ""
                            messages.add(ChatUIModel(userMsg, isUser = true))
                            
                            isThinking = true
                            coroutineScope.launch {
                                try {
                                    val response = apiService.chat(ChatInput(userMsg))
                                    if (response.isSuccessful && response.body() != null) {
                                        messages.add(ChatUIModel(response.body()!!.reply, isUser = false))
                                    } else {
                                        messages.add(ChatUIModel("Tôi gặp sự cố kết nối tới máy chủ AI. Vui lòng thử lại sau.", isUser = false))
                                    }
                                } catch (e: Exception) {
                                    messages.add(ChatUIModel("Lỗi: ${e.message}", isUser = false))
                                } finally {
                                    isThinking = false
                                }
                            }
                        },
                        enabled = messageText.isNotBlank() && !isThinking,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = PrimaryIndigo)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Gửi")
                    }
                }
                BottomNavigationBar(navController, activeTab = 2)
            }
        },
        containerColor = BackgroundObsidian
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(messages) { msg ->
                    ChatBubble(msg)
                }

                if (isThinking) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = PrimaryIndigo,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI Coach đang suy nghĩ...", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatBubble(message: ChatUIModel) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val containerColor = if (message.isUser) PrimaryIndigo else SurfaceGlass
    val textColor = Color.White
    val shape = if (message.isUser) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = message.text,
                color = textColor,
                fontSize = 14.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
