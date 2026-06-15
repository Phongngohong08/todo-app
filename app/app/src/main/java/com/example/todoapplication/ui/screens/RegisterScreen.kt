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
import com.example.todoapplication.data.model.RegisterInput
import com.example.todoapplication.ui.components.AppTextField
import com.example.todoapplication.ui.navigation.Screen
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiService = remember { NetworkClient.getApiService(context) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(tertiary, MaterialTheme.colorScheme.primaryContainer)))
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
                    .height(240.dp)
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
                        Text("🚀", fontSize = 34.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Bắt đầu hành trình",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        "Quản lý công việc thông minh hơn",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
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
                        "Tạo tài khoản",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "Điền thông tin để bắt đầu ✨",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(4.dp))

                    AppTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Họ và tên",
                        modifier = Modifier.fillMaxWidth()
                    )
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
                        label = "Mật khẩu (ít nhất 6 ký tự)",
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
                                if (!isLoading) Brush.horizontalGradient(listOf(tertiary, primary))
                                else Brush.horizontalGradient(listOf(
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                ))
                            )
                            .clickable(enabled = !isLoading) {
                                if (name.isBlank() || email.isBlank() || password.isBlank()) {
                                    Toast.makeText(context, "Vui lòng điền đầy đủ thông tin", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                if (password.length < 6) {
                                    Toast.makeText(context, "Mật khẩu phải dài ít nhất 6 ký tự", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                isLoading = true
                                coroutineScope.launch {
                                    try {
                                        val response = apiService.register(RegisterInput(email, password, name))
                                        if (response.isSuccessful) {
                                            Toast.makeText(context, "Đăng ký thành công! Hãy đăng nhập.", Toast.LENGTH_SHORT).show()
                                            navController.navigate(Screen.Login.route) {
                                                popUpTo(Screen.Register.route) { inclusive = true }
                                            }
                                        } else {
                                            Toast.makeText(context, "Email có thể đã tồn tại", Toast.LENGTH_LONG).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Lỗi kết nối: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
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
                                "Tạo tài khoản",
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
                            "Đã có tài khoản? ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { navController.navigate(Screen.Login.route) }) {
                            Text(
                                "Đăng nhập",
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
