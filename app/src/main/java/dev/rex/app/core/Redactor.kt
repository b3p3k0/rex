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

/**
 * Redacts sensitive information from strings using regex patterns from SPEC §7
 */
object Redactor {
    
    private const val REPLACEMENT = "[REDACTED]"
    
    private val patterns = listOf(
        Regex("(?i)(password|passwd|pwd)\\s*=\\s*[^\\s]+"),
        Regex("(?i)(token|apikey|api_key|secret|authorization)\\s*[:=]\\s*[^\\s]+"),
        Regex("(?i)bearer\\s+[a-z0-9\\.\\-_]+"),
        Regex("\\b[0-9a-f]{32,}\\b")
    )
    
    /**
     * Redacts sensitive information from the input string
     */
    fun redact(input: String): String {
        var result = input
        for (pattern in patterns) {
            result = pattern.replace(result, REPLACEMENT)
        }
        return result
    }
    
    /**
     * Checks if the input string contains any sensitive patterns
     */
    fun containsSensitiveData(input: String): Boolean {
        return patterns.any { it.containsMatchIn(input) }
    }
}