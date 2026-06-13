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

import com.intellij.lang.Language

/**
 * Language definition for gbkt (Game Boy Kotlin) DSL files.
 *
 * gbkt files use Kotlin syntax with domain-specific constructs for Game Boy development. This
 * language definition enables the IDE to recognize and provide intelligent assistance for .gbkt.kts
 * files.
 */
object GbktLanguage : Language("gbkt") {
    // readResolve() is the Java serialization hook that returns the singleton when a deserialized
    // instance is resolved. Required for Kotlin objects used as IntelliJ Language singletons to
    // survive plugin classloader reload without creating a second Language instance — the method
    // itself is invoked by the JVM serialization machinery, not by Kotlin call-sites.
    @Suppress("UnusedPrivateMember") private fun readResolve(): Any = GbktLanguage

    override fun getDisplayName(): String = "gbkt"

    override fun isCaseSensitive(): Boolean = true
}
