/*
 * Rex — Remote Exec for Android
 * Copyright (C) 2024 Rex Maintainers (b3p3k0)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dev.rex.app.ui.screens

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.rex.app.data.db.LogEntity
import dev.rex.app.data.repo.LogsRepository
import dev.rex.app.data.settings.SettingsStore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogsUiState(
    val logs: List<LogEntity> = emptyList(),
    val filteredLogs: List<LogEntity> = emptyList(),
    val distinctHostNicknames: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val selectedHostFilter: String = "All hosts",
    val statusFilter: String? = null, // null, "success", "failure"
    val searchQuery: String = "",
    val lastPurgeMessage: String? = null
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val logsRepository: LogsRepository,
    private val settingsStore: SettingsStore
) : ViewModel() {

    private val _selectedHostFilter = MutableStateFlow("All hosts")
    private val _statusFilter = MutableStateFlow<String?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _lastPurgeMessage = MutableStateFlow<String?>(null)

    private val rawLogs = logsRepository.getRecentLogs(1000)

    val uiState: StateFlow<LogsUiState> = combine(
        rawLogs,
        _selectedHostFilter,
        _statusFilter,
        _searchQuery,
        _lastPurgeMessage
    ) { logs, hostFilter, statusFilter, searchQuery, purgeMessage ->

        val distinctHosts = logs.map { it.hostNickname }.distinct().sorted()

        val filteredLogs = logs.filter { log ->
            // Host filter
            val matchesHost = hostFilter == "All hosts" || log.hostNickname == hostFilter

            // Status filter
            val matchesStatus = when (statusFilter) {
                "success" -> log.exitCode == 0
                "failure" -> log.exitCode != null && log.exitCode != 0
                else -> true
            }

            // Text search
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                val query = searchQuery.lowercase()
                log.hostNickname.lowercase().contains(query) ||
                log.commandName.lowercase().contains(query) ||
                (log.messageRedacted?.lowercase()?.contains(query) == true)
            }

            matchesHost && matchesStatus && matchesSearch
        }

        LogsUiState(
            logs = logs,
            filteredLogs = filteredLogs,
            distinctHostNicknames = distinctHosts,
            isLoading = false,
            selectedHostFilter = hostFilter,
            statusFilter = statusFilter,
            searchQuery = searchQuery,
            lastPurgeMessage = purgeMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LogsUiState()
    )

    init {
        // Enforce eviction policies on startup
        viewModelScope.launch {
            try {
                settingsStore.allSettings.first().let { settings ->
                    Log.i("Rex", "LogsViewModel: Enforcing retention policies on startup")

                    // Apply eviction policies in sequence
                    logsRepository.deleteOldLogsByCount(settings.logRetentionCount)
                    logsRepository.deleteOldLogsByAge(settings.logRetentionAgeDays)
                    logsRepository.deleteOldLogsBySize(settings.logRetentionSizeMb * 1024L * 1024L)
                }
            } catch (e: Exception) {
                Log.e("Rex", "LogsViewModel: Failed to enforce retention policies", e)
            }
        }
    }

    fun updateHostFilter(hostNickname: String) {
        _selectedHostFilter.value = hostNickname
    }

    fun updateStatusFilter(statusFilter: String?) {
        _statusFilter.value = statusFilter
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearPurgeMessage() {
        _lastPurgeMessage.value = null
    }

    fun performManualPurge() {
        viewModelScope.launch {
            try {
                val settings = settingsStore.allSettings.first()

                Log.i("Rex", "LogsViewModel: Starting manual purge")

                // Count retention
                logsRepository.deleteOldLogsByCount(settings.logRetentionCount)

                // Age retention
                logsRepository.deleteOldLogsByAge(settings.logRetentionAgeDays)

                // Size retention
                logsRepository.deleteOldLogsBySize(settings.logRetentionSizeMb * 1024L * 1024L)

                _lastPurgeMessage.value = "Logs purged according to retention policies"

                Log.i("Rex", "LogsViewModel: Manual purge completed")

            } catch (e: Exception) {
                Log.e("Rex", "LogsViewModel: Manual purge failed", e)
                _lastPurgeMessage.value = "Purge failed: ${e.localizedMessage}"
            }
        }
    }
}