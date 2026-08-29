package io.macula.cam2me.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * The one thing this app needs before it can join presence: this device
 * owner's own phone number, hashed and broadcast on every heartbeat so
 * contacts who already have that number can find this device. Shown once,
 * on first launch -- see MainActivity's onboarding check.
 */
@Composable
fun OnboardingScreen(onPhoneNumberEntered: (String) -> Unit) {
    var phoneNumber by remember { mutableStateOf("") }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Macula Cam2Me", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Enter your own phone number. It's hashed before it ever " +
                    "leaves this device -- only someone who already has your " +
                    "number can recognize you on the mesh.",
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Your phone number") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                modifier = Modifier.padding(top = 16.dp),
                enabled = phoneNumber.isNotBlank(),
                onClick = { onPhoneNumberEntered(phoneNumber.trim()) },
            ) {
                Text("Continue")
            }
        }
    }
}
