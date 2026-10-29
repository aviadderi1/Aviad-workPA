package com.aviad.workpa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviad.workpa.AppState
import kotlinx.coroutines.launch

@Composable
fun UpdateBanner(state: AppState) {
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth().background(AmberDark, RoundedCornerShape(12.dp)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("גרסה חדשה זמינה", color = Amber, fontWeight = FontWeight.Bold)
                Text("העדכון שומר את כל הנתונים שלך", fontSize = 12.sp, color = Color(0xFFE0C98A))
            }
            Spacer(Modifier.width(8.dp))
            val p = state.updateProgress
            if (p == null) {
                Button(
                    onClick = { scope.launch { state.installUpdate() } },
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color(0xFF1A1A1A)),
                ) { Text("עדכן", fontWeight = FontWeight.Bold) }
            } else {
                Text("${(p * 100).toInt()}%", color = Amber)
            }
        }
        state.updateProgress?.let {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { it }, color = Amber, modifier = Modifier.fillMaxWidth())
        }
        state.updateMsg?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 12.sp, color = Color(0xFFE0C98A))
        }
    }
}
