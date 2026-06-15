package com.example.todoapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.todoapplication.data.api.NetworkClient
import com.example.todoapplication.data.model.LoginInput
import com.example.todoapplication.data.repository.SessionManager
import com.example.todoapplication.ui.components.AppTextField
import com.example.todoapplication.ui.navigation.Screen
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sessionManager = remember { SessionManager(context) }
    val apiService = remember { NetworkClient.getApiService(context) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val bgColor = MaterialTheme.colorScheme.background

    fun doLogin() {
        if (email.isBlank() || password.isBlank()) {
            Toast.makeText(context, "Vui lòng điền đầy đủ thông tin", Toast.LENGTH_SHORT).show()
            return
        }
        isLoading = true
        coroutineScope.launch {
            try {
                val response = apiService.login(LoginInput(email, password))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    sessionManager.saveTokens(body.token, body.refreshToken)
                    sessionManager.saveUser(body.user.id, body.user.email, body.user.name)
                    Toast.makeText(context, "Chào mừng quay trở lại, ${body.user.name}!", Toast.LENGTH_SHORT).show()
                    navController.navigate(Screen.TaskList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                } else {
                    Toast.makeText(context, "Tài khoản hoặc mật khẩu không chính xác", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi kết nối: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(primary, MaterialTheme.colorScheme.primaryContainer)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Hero section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(270.dp)
                    .statusBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", fontSize = 38.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "TaskFlow AI",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "Làm chủ lịch trình của bạn",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Form sheet
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
                color = bgColor
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 32.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Đăng nhập",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Chào mừng quay trở lại! 👋",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(4.dp))

                    AppTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email",
                        keyboardType = KeyboardType.Email,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Mật khẩu",
                        keyboardType = KeyboardType.Password,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(4.dp))

                    // Gradient button
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (!isLoading) Brush.horizontalGradient(listOf(primary, tertiary))
                                else Brush.horizontalGradient(listOf(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                ))
                            )
                            .clickable(enabled = !isLoading) { doLogin() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "Đăng nhập",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Chưa có tài khoản? ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { navController.navigate(Screen.Register.route) }) {
                            Text(
                                "Đăng ký ngay",
                                color = primary,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
