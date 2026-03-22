/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.mcp

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * Entry point for the gbkt MCP server.
 *
 * Starts a stdio-based MCP server that exposes Game Boy emulator tools for AI agents. The server
 * manages a single [McpEmulatorSession] and registers 11 tools for frame-by-frame game control,
 * variable inspection, screenshot capture, and metadata query.
 *
 * Usage:
 * ```
 * java -jar gbkt-mcp-server-all.jar            # headless (CI)
 * java -jar gbkt-mcp-server-all.jar --headed    # opens LCD viewer window
 * ```
 *
 * Configure in Claude Code's `.claude/mcp_servers.json`:
 * ```json
 * {
 *   "gbkt-emulator": {
 *     "type": "stdio",
 *     "command": "java",
 *     "args": ["-jar", "path/to/gbkt-mcp-server-all.jar", "--headed"]
 *   }
 * }
 * ```
 *
 * When `--headed` is passed, every `emulator_start` call opens a Swing window showing the Game Boy
 * LCD in real time. The agent still controls all input — the developer just watches.
 */
fun main(args: Array<String>) {
    val headed = "--headed" in args
    val session = McpEmulatorSession(headed = headed)

    val server =
        Server(
            Implementation(name = "gbkt-emulator", version = "1.0.0"),
            ServerOptions(
                capabilities =
                    ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false))
            ),
        )

    server.registerEmulatorTools(session)

    val transport =
        StdioServerTransport(
            inputStream = System.`in`.asSource().buffered(),
            outputStream = System.out.asSink().buffered(),
        )

    runBlocking {
        val mcpSession = server.createSession(transport)
        val done = Job()
        mcpSession.onClose {
            launch { session.stop() }
            done.complete()
        }
        done.join()
    }
}
