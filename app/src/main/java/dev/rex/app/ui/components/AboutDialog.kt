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

package dev.rex.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.rex.app.BuildConfig
import dev.rex.app.R

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val githubUrl = stringResource(R.string.about_github_url)
    val redditUrl = stringResource(R.string.about_reddit_url)

    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        fontWeight = FontWeight.Medium
    )

    val bodyText = buildAnnotatedString {
        append(stringResource(R.string.about_line1))
        append("\n\n")
        append(stringResource(R.string.about_line2))
        append("\n\n")

        append(stringResource(R.string.about_repo_prefix))
        pushStringAnnotation("url", githubUrl)
        withStyle(linkStyle) {
            append(stringResource(R.string.about_github_label))
        }
        pop()

        append("\n")
        append(stringResource(R.string.about_chat_prefix))
        pushStringAnnotation("url", redditUrl)
        withStyle(linkStyle) {
            append(stringResource(R.string.about_reddit_label))
        }
        pop()
    }

    val footerText = "${stringResource(R.string.about_license)} ${stringResource(R.string.about_version_fmt, BuildConfig.VERSION_NAME)}"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.about_title),
                style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp) // Ensure dialog fits ≤ 60% screen height on most devices
                    .verticalScroll(rememberScrollState())
            ) {
                ClickableText(
                    text = bodyText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    onClick = { offset ->
                        bodyText.getStringAnnotations("url", offset, offset)
                            .firstOrNull()?.let { annotation ->
                                uriHandler.openUri(annotation.item)
                            }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = footerText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_close))
            }
        }
    )
}
