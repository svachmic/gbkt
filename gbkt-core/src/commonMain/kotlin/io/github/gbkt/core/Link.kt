/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2026 Michal Svacha
 */
package io.github.gbkt.core

import io.github.gbkt.core.dsl.GbktDsl
import io.github.gbkt.core.dsl.RecordingContext
import io.github.gbkt.core.dsl.StatementRecorder
import io.github.gbkt.core.ir.Condition
import io.github.gbkt.core.ir.Expr
import io.github.gbkt.core.ir.GBVar
import io.github.gbkt.core.ir.IRLinkConnected
import io.github.gbkt.core.ir.IRLinkHasData
import io.github.gbkt.core.ir.IRLinkInit
import io.github.gbkt.core.ir.IRLinkIsMaster
import io.github.gbkt.core.ir.IRLinkReceivedData
import io.github.gbkt.core.ir.IRLinkSend
import io.github.gbkt.core.ir.IRLinkUpdate
import io.github.gbkt.core.ir.IRLiteral
import io.github.gbkt.core.ir.IRStatement
import io.github.gbkt.core.ir.IRVar

/** Link cable definition for multiplayer communication. */
data class LinkDefinition(
    val name: String,
    val onReceiveStatements: List<IRStatement>,
    val receiveDataVar: String,
)

/** Handle to a configured link cable for use in scenes. */
class LinkHandle internal constructor(private val definition: LinkDefinition) {
    /** Check if link cable is connected */
    val isConnected: Condition
        get() = Condition(IRLinkConnected)

    /** Check if we're the master (initiated connection) */
    val isMaster: Condition
        get() = Condition(IRLinkIsMaster)

    /** Check if data is available */
    val hasData: Condition
        get() = Condition(IRLinkHasData)

    /** Get received data byte */
    val received: Expr
        get() = Expr(IRLinkReceivedData)

    /** Initialize link cable (call in scene enter) */
    fun init() {
        RecordingContext.require().emit(IRLinkInit)
    }

    /** Update link state (call in every.frame) */
    fun update() {
        RecordingContext.require().emit(IRLinkUpdate)
    }

    /** Send a byte over link cable */
    fun send(data: Int) {
        RecordingContext.require().emit(IRLinkSend(IRLiteral(data)))
    }

    /** Send an expression over link cable */
    fun send(data: Expr) {
        RecordingContext.require().emit(IRLinkSend(data.ir))
    }

    /** Send a variable over link cable */
    fun send(data: GBVar<*>) {
        RecordingContext.require().emit(IRLinkSend(IRVar(data.name)))
    }
}

/** Builder for configuring link cable communication. */
@GbktDsl
class LinkBuilder {
    private var _onReceiveStatements: List<IRStatement> = emptyList()
    private var _receiveDataVar: String = "data"

    /**
     * Register a callback for when data is received. The callback receives a single byte of data.
     */
    fun onReceive(dataVar: String = "data", block: FrameScope.() -> Unit) {
        _receiveDataVar = dataVar
        val recorder = StatementRecorder()
        RecordingContext.record(recorder) { FrameScope("link_receive").block() }
        _onReceiveStatements = recorder.statements
    }

    internal fun build() =
        LinkDefinition(
            name = "link",
            onReceiveStatements = _onReceiveStatements,
            receiveDataVar = _receiveDataVar,
        )
}
