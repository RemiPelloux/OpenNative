package app.gamenative.mods

import app.gamenative.data.ModInstall
import app.gamenative.data.ModInstallStatus
import app.gamenative.data.ModProfile
import app.gamenative.data.ModProfileInstallState
import app.gamenative.db.dao.ModDao
import java.util.Locale

object ModProfileManager {
    const val DEFAULT_PROFILE_NAME = "Default"

    suspend fun ensureActiveProfile(dao: ModDao, appId: String): ModProfile {
        dao.getActiveProfileForApp(appId)?.let { return it }
        val existingDefault = dao.getProfilesForApp(appId).firstOrNull { it.name == DEFAULT_PROFILE_NAME }
        val profile = existingDefault?.copy(active = true, updatedAt = System.currentTimeMillis())
            ?: ModProfile(
                profileId = defaultProfileId(appId),
                appId = appId,
                name = DEFAULT_PROFILE_NAME,
                active = true,
            )
        dao.upsertProfile(profile)
        dao.activateProfile(appId, profile.profileId)
        return profile
    }

    suspend fun ensureStateForInstall(
        dao: ModDao,
        profile: ModProfile,
        installId: String,
        enabled: Boolean = true,
        priority: Int? = null,
    ): ModProfileInstallState =
        dao.ensureProfileInstallState(profile, installId, enabled, priority)

    suspend fun ensureStatesForInstalls(
        dao: ModDao,
        profile: ModProfile,
        installs: List<ModInstall>,
    ): List<ModProfileInstallState> {
        val existingStates = dao.getProfileInstallStates(profile.appId, profile.profileId)
        val statesByInstallId = existingStates.associateByTo(LinkedHashMap()) { it.installId }
        val profileHasExistingStates = existingStates.isNotEmpty()
        var nextPriority = (existingStates.maxOfOrNull { it.priority } ?: -1) + 1
        val now = System.currentTimeMillis()

        installs.forEach { install ->
            val existing = statesByInstallId[install.installId]
            when {
                existing == null -> {
                    statesByInstallId[install.installId] = ModProfileInstallState(
                        profileId = profile.profileId,
                        installId = install.installId,
                        appId = profile.appId,
                        enabled = defaultMissingStateEnabled(profile, install, profileHasExistingStates),
                        priority = nextPriority++,
                        updatedAt = now,
                    )
                }
                install.status == ModInstallStatus.DISABLED.name && existing.enabled -> {
                    statesByInstallId[install.installId] = existing.copy(enabled = false, updatedAt = now)
                }
            }
        }

        val normalizedPriorities = normalizedPriorityByInstallId(statesByInstallId.values.toList(), installs)
        val finalStates = statesByInstallId.values.map { state ->
            val normalized = normalizedPriorities[state.installId]
            if (normalized != null && state.priority != normalized) {
                state.copy(priority = normalized, updatedAt = now)
            } else {
                state
            }
        }.sortedWith(compareBy<ModProfileInstallState> { it.priority }.thenBy { it.installId })

        val existingById = existingStates.associateBy { it.installId }
        val changedStates = finalStates.filter { existingById[it.installId] != it }
        if (changedStates.isNotEmpty()) dao.upsertProfileInstallStates(changedStates)
        return finalStates
    }

    internal fun defaultMissingStateEnabled(
        profile: ModProfile,
        install: ModInstall,
        profileHasExistingStates: Boolean,
    ): Boolean =
        !profileHasExistingStates &&
            install.status != ModInstallStatus.DISABLED.name &&
            install.createdAt <= profile.createdAt

    internal suspend fun normalizePriorities(
        dao: ModDao,
        states: List<ModProfileInstallState>,
        installs: List<ModInstall>,
    ): List<ModProfileInstallState> {
        val normalizedPriorities = normalizedPriorityByInstallId(states, installs)
        if (normalizedPriorities.isEmpty()) return states

        val now = System.currentTimeMillis()
        val normalizedStates = states.map { state ->
            val normalized = normalizedPriorities[state.installId]
            if (normalized != null && state.priority != normalized) {
                state.copy(priority = normalized, updatedAt = now)
            } else {
                state
            }
        }
        val changedStates = normalizedStates.filterIndexed { index, state -> state != states[index] }
        if (changedStates.isNotEmpty()) dao.upsertProfileInstallStates(changedStates)
        return normalizedStates.sortedWith(compareBy<ModProfileInstallState> { it.priority }.thenBy { it.installId })
    }

    internal fun normalizedPriorityByInstallId(
        states: List<ModProfileInstallState>,
        installs: List<ModInstall>,
    ): Map<String, Int> {
        val installById = installs.associateBy { it.installId }
        val orderedTopToBottom = states
            .filter { it.installId in installById }
            .sortedWith(
                compareByDescending<ModProfileInstallState> { it.priority }
                    .thenBy { installById[it.installId]?.modName?.lowercase(Locale.US).orEmpty() }
                    .thenBy { installById[it.installId]?.fileName?.lowercase(Locale.US).orEmpty() }
                    .thenBy { it.installId },
            )
        return orderedTopToBottom
            .mapIndexed { index, state -> state.installId to orderedTopToBottom.lastIndex - index }
            .toMap()
    }

    fun defaultProfileId(appId: String): String =
        "${appId.trim().ifBlank { "app" }}:default"
}
