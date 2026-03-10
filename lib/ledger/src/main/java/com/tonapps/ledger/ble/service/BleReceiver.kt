package com.tonapps.ledger.ble.service

import com.tonapps.ledger.ble.extension.toHexString
import com.tonapps.ledger.ble.model.FrameCommand
import com.tonapps.ledger.ble.service.model.BleAnswer

class BleReceiver {
    private var pendingAnswers: MutableList<FrameCommand> = mutableListOf()
    fun handleAnswer(id: String, hexAnswer: String): BleAnswer? {
        val command: FrameCommand = FrameCommand.fromHex(id, hexAnswer)
        pendingAnswers.add(command)

        val isAnswerComplete = if (command.index == 0) {
            command.size == command.apdu.size
        } else {
            val totalReceivedSize = pendingAnswers.sumOf { it.apdu.size }
            pendingAnswers.first().size == totalReceivedSize
        }

        return if (isAnswerComplete) {
            val completeApdu = pendingAnswers.joinToString("") { it.apdu.toHexString() }
            pendingAnswers.clear()
            BleAnswer(id, completeApdu)
        } else {
            null
        }
    }
}