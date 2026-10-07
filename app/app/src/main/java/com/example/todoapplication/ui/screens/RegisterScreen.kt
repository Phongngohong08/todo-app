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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.todoapplication.ui.components.AppTextField
import com.example.todoapplication.ui.components.AppLogo
import com.example.todoapplication.ui.navigation.Screen
import com.example.todoapplication.ui.viewmodel.AuthEvent
import com.example.todoapplication.ui.viewmodel.RegisterViewModel
import androidx.compose.ui.res.stringResource
import com.example.todoapplication.R
import com.example.todoapplication.i18n.str

/** [TẦNG UI · MÀN HÌNH] Đăng ký tài khoản — gọi RegisterViewModel.register(), thành công thì về màn đăng nhập. */
@Composable
fun RegisterScreen(
    navController: NavController,
    registerViewModel: RegisterViewModel = viewModel(factory = RegisterViewModel.Factory)
) {
    // Cấu trúc màn này GIỐNG HỆT LoginScreen (state cục bộ cho ô nhập + collect events) — xem chú thích ở đó.
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isLoading by registerViewModel.isLoading.collectAsStateWithLifecycle()

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val bgColor = MaterialTheme.colorScheme.background

    LaunchedEffect(Unit) {
        registerViewModel.events.collect { event ->
            when (event) {
                is AuthEvent.Success -> {
                    // Đăng ký xong KHÔNG tự đăng nhập — đưa về màn Login (và xóa màn Register khỏi lịch sử).
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                }
                is AuthEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

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
                    AppLogo(size = 88.dp)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(R.string.register_hero),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        stringResource(R.string.register_hero_sub),
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
                        stringResource(R.string.register_create_account),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        stringResource(R.string.register_subtitle),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(4.dp))

                    AppTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(R.string.field_full_name),
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = stringResource(R.string.field_email),
                        keyboardType = KeyboardType.Email,
                        modifier = Modifier.fillMaxWidth()
                    )
                    AppTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.field_password_min),
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
                                if (password.isNotBlank() && password.length < 6) {
                                    Toast.makeText(context, str(R.string.register_password_too_short), Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }
                                registerViewModel.register(name, email, password)
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
                                stringResource(R.string.register_create_account),
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
                            stringResource(R.string.register_have_account),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = { navController.navigate(Screen.Login.route) }) {
                            Text(
                                stringResource(R.string.login_sign_in),
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
