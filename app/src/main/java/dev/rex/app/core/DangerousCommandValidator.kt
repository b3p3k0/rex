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

package dev.rex.app.core

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DangerousCommandValidator @Inject constructor() {

    private val dangerousPatterns = listOf(
        // File system destruction
        Regex("\\b(rm|del|delete)\\s+(-[rf]+\\s*)*\\s*[/~]", RegexOption.IGNORE_CASE),
        Regex("\\b(rm|del|delete)\\s+.*\\s+(--recursive|--force|-r|-f)", RegexOption.IGNORE_CASE),

        // Format/filesystem operations
        Regex("\\b(mkfs|format|fdisk|parted|gparted)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bdd\\s+(if|of)=", RegexOption.IGNORE_CASE),

        // System operations
        Regex("\\b(shutdown|reboot|halt|poweroff|init\\s+[06])\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(killall|pkill)\\s", RegexOption.IGNORE_CASE),

        // Fork bombs and resource exhaustion
        Regex(":\\(\\)\\{.*\\};:", RegexOption.IGNORE_CASE), // :(){ :|:& };:
        Regex("\\b(while\\s+true|for\\s*\\(\\s*;;\\s*\\))", RegexOption.IGNORE_CASE),
        Regex("\\|.*\\|.*&.*&", RegexOption.IGNORE_CASE), // pipe bomb patterns with multiple pipes and backgrounds

        // Network/firewall disruption
        Regex("\\b(iptables|ufw|firewall-cmd)\\s.*(-F|--flush|disable)", RegexOption.IGNORE_CASE),
        Regex("\\bifconfig\\s+\\w+\\s+down", RegexOption.IGNORE_CASE),

        // Privilege escalation and indirect execution
        Regex("\\bsudo\\s+(rm|dd|mkfs|shutdown|reboot)", RegexOption.IGNORE_CASE),
        Regex("\\b(sh|bash|zsh|fish|csh)\\s+-c\\s+['\"].*\\b(rm|dd|mkfs)", RegexOption.IGNORE_CASE),
        Regex("\\beval\\s+['\"].*\\b(rm|dd|mkfs)", RegexOption.IGNORE_CASE),
        Regex("\\bexec\\s+.*\\b(rm|dd|mkfs)", RegexOption.IGNORE_CASE),

        // Package management destruction
        Regex("\\b(apt|yum|dnf|pacman|pkg)\\s+(remove|purge|uninstall)\\s+.*\\*", RegexOption.IGNORE_CASE),
        Regex("\\b(apt|yum|dnf)\\s+autoremove\\s+--purge", RegexOption.IGNORE_CASE),

        // Critical file/directory targets
        Regex("\\b(rm|del|delete)\\s+.*\\b(etc|usr|var|boot|sys|proc|dev)", RegexOption.IGNORE_CASE),
        Regex(">/\\s*(dev|proc|sys)/", RegexOption.IGNORE_CASE),

        // Destructive output redirection
        Regex(">\\s*/dev/(null|zero|random|urandom)\\s*<", RegexOption.IGNORE_CASE),
        Regex("\\*\\s*>\\s*/dev/", RegexOption.IGNORE_CASE),

        // Container/VM escape attempts
        Regex("\\b(docker|podman)\\s+.*--privileged", RegexOption.IGNORE_CASE),
        Regex("\\bmount\\s+.*proc", RegexOption.IGNORE_CASE),

        // Memory/disk bombing
        Regex("\\byes\\s+.*\\|", RegexOption.IGNORE_CASE),
        Regex("\\bcat\\s+/dev/(zero|urandom)\\s*>", RegexOption.IGNORE_CASE)
    )

    fun isDangerous(command: String): Boolean {
        val normalizedCommand = command.trim()
        if (normalizedCommand.isBlank()) return false

        // Skip comments - lines starting with #
        if (normalizedCommand.startsWith("#")) return false

        // Skip quoted echo statements
        if (normalizedCommand.matches(Regex("echo\\s+['\"].*['\"]", RegexOption.IGNORE_CASE))) return false

        // Skip common safe commands
        if (normalizedCommand.matches(Regex("^(ls|cat|grep|ps|whoami|pwd|date|uptime)\\b.*", RegexOption.IGNORE_CASE))) return false

        // Check against all dangerous patterns
        return dangerousPatterns.any { pattern ->
            pattern.containsMatchIn(normalizedCommand)
        }
    }

    fun getDangerReason(command: String): String? {
        val normalizedCommand = command.trim()
        if (normalizedCommand.isBlank()) return null

        // Skip comments - lines starting with #
        if (normalizedCommand.startsWith("#")) return null

        // Skip quoted echo statements
        if (normalizedCommand.matches(Regex("echo\\s+['\"].*['\"]", RegexOption.IGNORE_CASE))) return null

        // Skip common safe commands
        if (normalizedCommand.matches(Regex("^(ls|cat|grep|ps|whoami|pwd|date|uptime)\\b.*", RegexOption.IGNORE_CASE))) return null

        return when {
            dangerousPatterns.subList(0, 2).any { it.containsMatchIn(normalizedCommand) } ->
                "File deletion command detected"

            dangerousPatterns.subList(2, 4).any { it.containsMatchIn(normalizedCommand) } ->
                "Filesystem formatting command detected"

            dangerousPatterns.subList(4, 6).any { it.containsMatchIn(normalizedCommand) } ->
                "System control command detected"

            dangerousPatterns.subList(6, 9).any { it.containsMatchIn(normalizedCommand) } ->
                "Resource exhaustion attack detected"

            dangerousPatterns.subList(9, 11).any { it.containsMatchIn(normalizedCommand) } ->
                "Network disruption command detected"

            dangerousPatterns.subList(11, 15).any { it.containsMatchIn(normalizedCommand) } ->
                "Indirect execution of dangerous command detected"

            dangerousPatterns.subList(15, 17).any { it.containsMatchIn(normalizedCommand) } ->
                "Package removal command detected"

            dangerousPatterns.subList(17, 19).any { it.containsMatchIn(normalizedCommand) } ->
                "Critical system file operation detected"

            dangerousPatterns.subList(19, 21).any { it.containsMatchIn(normalizedCommand) } ->
                "Destructive output redirection detected"

            dangerousPatterns.subList(21, 23).any { it.containsMatchIn(normalizedCommand) } ->
                "Container escape attempt detected"

            dangerousPatterns.subList(23, 25).any { it.containsMatchIn(normalizedCommand) } ->
                "System resource bombing detected"

            else -> "Potentially dangerous command detected"
        }
    }
}