/*
 * Copyright 2026 Michal Svacha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.gbkt.intellij

import com.intellij.DynamicBundle
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

/**
 * Message bundle for gbkt plugin i18n support.
 *
 * Usage:
 * ```kotlin
 * val text = GbktBundle.message("action.build.text")
 * val formatted = GbktBundle.message("notification.sdk.detected", "/path/to/gbdk")
 * ```
 */
object GbktBundle {
    @NonNls private const val BUNDLE = "messages.GbktBundle"

    private val instance = DynamicBundle(GbktBundle::class.java, BUNDLE)

    /**
     * Gets a localized message for the given key.
     *
     * @param key The message key from GbktBundle.properties
     * @param params Optional parameters for message formatting
     * @return The localized message string
     */
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        instance.getMessage(key, *params)

    /**
     * Gets a localized message supplier for lazy evaluation.
     *
     * @param key The message key from GbktBundle.properties
     * @param params Optional parameters for message formatting
     * @return A supplier that returns the localized message
     */
    @JvmStatic
    fun messagePointer(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any,
    ): () -> String = { message(key, *params) }
}
