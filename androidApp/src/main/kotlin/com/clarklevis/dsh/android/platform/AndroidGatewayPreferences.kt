package com.clarklevis.dsh.android.platform

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.clarklevis.dsh.shared.platform.GatewayPreferences
import com.clarklevis.dsh.shared.platform.GatewayPreferencesSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

class AndroidGatewayPreferences(context: Context, gatewayId: String? = null) : GatewayPreferences {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(gatewayId?.let { "host_$it" } ?: FILE_NAME) }
    )

    override val snapshots: Flow<GatewayPreferencesSnapshot> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw error
        }
        .map(::toSnapshot)

    override suspend fun load(): GatewayPreferencesSnapshot = snapshots.first()

    override suspend fun update(snapshot: GatewayPreferencesSnapshot) {
        dataStore.edit { preferences ->
            preferences[ENDPOINT] = snapshot.endpoint
            snapshot.selectedWorkspaceId.setOrRemove(preferences, SELECTED_WORKSPACE_ID)
            snapshot.sessionsJson.setOrRemove(preferences, SESSIONS_JSON)
            snapshot.workspacesJson.setOrRemove(preferences, WORKSPACES_JSON)
        }
    }

    private fun toSnapshot(preferences: Preferences): GatewayPreferencesSnapshot =
        GatewayPreferencesSnapshot(
            endpoint = preferences[ENDPOINT] ?: DEFAULT_ENDPOINT,
            selectedWorkspaceId = preferences[SELECTED_WORKSPACE_ID],
            sessionsJson = preferences[SESSIONS_JSON],
            workspacesJson = preferences[WORKSPACES_JSON]
        )

    private fun String?.setOrRemove(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        key: Preferences.Key<String>
    ) {
        if (this == null) preferences.remove(key) else preferences[key] = this
    }

    companion object {
        const val DEFAULT_ENDPOINT = "ws://127.0.0.1:3080/ws/mobile"

        private const val FILE_NAME = "gateway.preferences_pb"
        private val ENDPOINT = stringPreferencesKey("gateway.endpoint")
        private val SELECTED_WORKSPACE_ID = stringPreferencesKey("gateway.selected_workspace_id")
        private val SESSIONS_JSON = stringPreferencesKey("gateway.sessions_json")

        /**
         * 工作区映射缓存。与 [SESSIONS_JSON] 分开存放：会话列表随会话活动变化，
         * 工作区只在宿主推 `workspaces` 帧时变，混在一份会让两者的写入时机互相牵制。
         */
        private val WORKSPACES_JSON = stringPreferencesKey("gateway.workspaces_json")
    }
}
