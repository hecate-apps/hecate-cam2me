package io.macula.cam2me.onboarding

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ownerPhoneNumberDataStore by preferencesDataStore(name = "owner_phone_number")
private val PHONE_NUMBER = stringPreferencesKey("phone_number")

/**
 * The phone number of the person who owns this device -- captured once
 * during onboarding, broadcast (hashed, never in the clear) on the
 * presence heartbeat so contacts can recognise this device as theirs.
 * Null until onboarding completes; [io.macula.cam2me.MainActivity] shows
 * [OnboardingScreen] for exactly that long.
 */
object OwnerPhoneNumber {
    fun observe(context: Context): Flow<String?> =
        context.ownerPhoneNumberDataStore.data.map { it[PHONE_NUMBER] }

    suspend fun set(context: Context, phoneNumber: String) {
        context.ownerPhoneNumberDataStore.edit { it[PHONE_NUMBER] = phoneNumber }
    }
}
