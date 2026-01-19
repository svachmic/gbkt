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
package io.github.gbkt.intellij.project

import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.ModuleTypeManager
import io.github.gbkt.intellij.GbktIcons
import javax.swing.Icon

/** Module type for gbkt game projects. */
class GbktModuleType : ModuleType<GbktModuleBuilder>(ID) {

    override fun createModuleBuilder(): GbktModuleBuilder = GbktModuleBuilder()

    override fun getName(): String = "gbkt Game"

    override fun getDescription(): String =
        "Game Boy Color game project using the gbkt Kotlin DSL framework"

    override fun getNodeIcon(isOpened: Boolean): Icon = GbktIcons.FILE

    companion object {
        const val ID = "GBKT_MODULE"

        val INSTANCE: GbktModuleType by lazy {
            (ModuleTypeManager.getInstance().findByID(ID) as? GbktModuleType) ?: GbktModuleType()
        }
    }
}
