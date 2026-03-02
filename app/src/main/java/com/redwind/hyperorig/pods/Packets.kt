package com.redwind.hyperorig.pods

object OriGPackets {

    fun buildPacket(opCode: Int, vararg params: Byte): ByteArray {
        val payloadLength = 3 + params.size
        val packet = ByteArray(3 + payloadLength)
        packet[0] = 0x4E
        packet[1] = (payloadLength and 0xFF).toByte()
        packet[2] = ((payloadLength shr 8) and 0xFF).toByte()
        packet[3] = 0x00
        packet[4] = (opCode and 0xFF).toByte()
        packet[5] = ((opCode shr 8) and 0xFF).toByte()
        params.copyInto(packet, 6)
        return packet
    }
}

object AncMode {
    const val OFF = 0x00
    const val TRANSPARENT = 0x01
    const val NORMAL = 0x02
    const val DEEP = 0x03
    const val EXPERIMENT = 0x10
    const val WIND_SUPPRESSION = 0x11
}

enum class NoiseControlMode {
    OFF, TRANSPARENT, NORMAL, DEEP, EXPERIMENT, WIND_SUPPRESSION
}

enum class EqMode(val value: Int, val label: String) {
    BLUE(0x00, "悔恨之泪"),
    BALANCED(0x01, "均衡中正"),
    BASS(0x02, "欧美澎湃"),
    PURE(0x03, "真律还原"),
    FINE(0x05, "细腻佳音"),
    VOCAL(0x06, "温婉人声"),
    GAME(0x04, "游戏优化");

    companion object {
        fun fromValue(value: Int): EqMode {
            return values().find { it.value == value } ?: BALANCED
        }
    }
}

object Op {
    const val VERSION = 0x0003
    const val BATTERY = 0x0005
    const val ANC_SET = 0x0201
    const val ANC_QUERY = 0x0101
    const val EQ_SET = 0x0207
    const val EQ_QUERY = 0x0107
    const val GAME_MODE_SET = 0x0208
    const val GAME_MODE_QUERY = 0x0108
    const val LOW_LATENCY_SET = 0x0206
    const val LOW_LATENCY_QUERY = 0x0106
    const val DUAL_CONN_SET = 0x0205
    const val DUAL_CONN_QUERY = 0x0105
    const val IN_EAR_SET = 0x0209
    const val IN_EAR_QUERY = 0x0109
    const val CODEC = 0x0204
    const val WIND_SUPPRESSION_SET = 0x02E1
    const val WIND_SUPPRESSION_QUERY = 0x01E1
    const val FULL_STATE = 0x0103
}

object Enums {
    val ANC_OFF: ByteArray = OriGPackets.buildPacket(Op.ANC_SET, AncMode.OFF.toByte(), 0x00)
    val ANC_TRANSPARENT: ByteArray = OriGPackets.buildPacket(Op.ANC_SET, AncMode.TRANSPARENT.toByte(), 0x00)
    val ANC_NORMAL: ByteArray = OriGPackets.buildPacket(Op.ANC_SET, AncMode.NORMAL.toByte(), 0x00)
    val ANC_DEEP: ByteArray = OriGPackets.buildPacket(Op.ANC_SET, AncMode.DEEP.toByte(), 0x00)
    val ANC_EXPERIMENT: ByteArray = OriGPackets.buildPacket(Op.ANC_SET, AncMode.EXPERIMENT.toByte(), 0x00)
    val ANC_WIND_SUPPRESSION: ByteArray = OriGPackets.buildPacket(Op.ANC_SET, AncMode.WIND_SUPPRESSION.toByte(), 0x00)

    val LOW_LATENCY_ON: ByteArray = OriGPackets.buildPacket(Op.LOW_LATENCY_SET, 0x01)
    val LOW_LATENCY_OFF: ByteArray = OriGPackets.buildPacket(Op.LOW_LATENCY_SET, 0x00)

    val DUAL_CONN_ON: ByteArray = OriGPackets.buildPacket(Op.DUAL_CONN_SET, 0x01)
    val DUAL_CONN_OFF: ByteArray = OriGPackets.buildPacket(Op.DUAL_CONN_SET, 0x00)

    fun EQ_SET_VALUE(eqValue: Int): ByteArray = OriGPackets.buildPacket(Op.EQ_SET, eqValue.toByte(), 0x00)

    val WIND_SUPPRESSION_ON: ByteArray = OriGPackets.buildPacket(Op.WIND_SUPPRESSION_SET, 0x01)
    val WIND_SUPPRESSION_OFF: ByteArray = OriGPackets.buildPacket(Op.WIND_SUPPRESSION_SET, 0x00)

    val IN_EAR_DETECTION_ON: ByteArray = OriGPackets.buildPacket(Op.IN_EAR_SET, 0x01)
    val IN_EAR_DETECTION_OFF: ByteArray = OriGPackets.buildPacket(Op.IN_EAR_SET, 0x00)

    val QUERY_BATTERY: ByteArray = byteArrayOf(0x4E, 0x03, 0x00, 0x00, 0x05, 0x00)
    val QUERY_ANC: ByteArray = byteArrayOf(0x4E, 0x03, 0x00, 0x00, 0x01, 0x01)
    val QUERY_GAME_MODE: ByteArray = byteArrayOf(0x4E, 0x03, 0x00, 0x00, 0x08, 0x01)
    val QUERY_VERSION: ByteArray = byteArrayOf(0x4E, 0x03, 0x00, 0x00, 0x03, 0x00)
    val QUERY_FULL_STATE: ByteArray = byteArrayOf(0x4E, 0x03, 0x00, 0x00, 0x03, 0x01)

    val GAME_MODE_ON: ByteArray = OriGPackets.buildPacket(Op.GAME_MODE_SET, 0x01)
    val GAME_MODE_OFF: ByteArray = OriGPackets.buildPacket(Op.GAME_MODE_SET, 0x00)

    val QUERY_STATUS: ByteArray = QUERY_FULL_STATE

    val QUERY_IN_EAR_DETECTION: ByteArray = byteArrayOf(0x4E, 0x03, 0x00, 0x00, 0x09, 0x01)
}

object BatteryParser {

    data class BatteryInfo(
        val level: Int,
        val isCharging: Boolean = false
    )

    data class BatteryResult(
        val left: BatteryInfo?,
        val right: BatteryInfo?,
        val case: BatteryInfo?
    )

    fun parse(data: ByteArray): BatteryResult? {
        if (data.size < 9) return null
        if ((data[0].toInt() and 0xFF) != 0x4E) return null

        val opCode = ((data[5].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        if (opCode != Op.BATTERY) return null

        val left = data[6].toInt() and 0xFF
        val right = data[7].toInt() and 0xFF
        val case = data[8].toInt() and 0xFF

        return BatteryResult(
            left = if (left > 0) BatteryInfo(left) else null,
            right = if (right > 0) BatteryInfo(right) else null,
            case = if (case > 0) BatteryInfo(case) else null
        )
    }

    fun parseActiveReport(data: ByteArray): BatteryResult? = null
}

object AncModeParser {

    fun parse(data: ByteArray): NoiseControlMode? {
        if (data.size < 7) return null
        if ((data[0].toInt() and 0xFF) != 0x4E) return null

        val opCode = ((data[5].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        if (opCode != Op.ANC_QUERY) return null

        val modeValue = data[6].toInt() and 0xFF
        return when (modeValue) {
            AncMode.OFF -> NoiseControlMode.OFF
            AncMode.TRANSPARENT -> NoiseControlMode.TRANSPARENT
            AncMode.NORMAL -> NoiseControlMode.NORMAL
            AncMode.DEEP -> NoiseControlMode.DEEP
            AncMode.EXPERIMENT -> NoiseControlMode.EXPERIMENT
            AncMode.WIND_SUPPRESSION -> NoiseControlMode.WIND_SUPPRESSION
            else -> NoiseControlMode.OFF
        }
    }
}

object GameModeParser {

    fun parse(data: ByteArray): Boolean? {
        if (data.size < 7) return null
        if ((data[0].toInt() and 0xFF) != 0x4E) return null

        val opCode = ((data[5].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        if (opCode != Op.GAME_MODE_QUERY) return null

        val status = data[6].toInt() and 0xFF
        return status == 0x01
    }
}

object LowLatencyParser {
    fun parse(data: ByteArray): Boolean? {
        if (data.size < 7) return null
        if ((data[0].toInt() and 0xFF) != 0x4E) return null

        val opCode = ((data[5].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        if (opCode != Op.LOW_LATENCY_QUERY) return null

        val status = data[6].toInt() and 0xFF
        return status == 0x01
    }
}

object DualConnParser {
    fun parse(data: ByteArray): Boolean? {
        if (data.size < 7) return null
        if ((data[0].toInt() and 0xFF) != 0x4E) return null

        val opCode = ((data[5].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        if (opCode != Op.DUAL_CONN_QUERY) return null

        val status = data[6].toInt() and 0xFF
        return status == 0x01
    }
}

object WindSuppressionParser {
    fun parse(data: ByteArray): Boolean? {
        if (data.size < 7) return null
        if ((data[0].toInt() and 0xFF) != 0x4E) return null

        val opCode = ((data[5].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        if (opCode != Op.WIND_SUPPRESSION_QUERY) return null

        val status = data[6].toInt() and 0xFF
        return status == 0x01
    }
}

object EqParser {
    fun parse(data: ByteArray): EqMode? {
        if (data.size < 7) return null
        if ((data[0].toInt() and 0xFF) != 0x4E) return null

        val opCode = ((data[5].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        if (opCode != Op.EQ_QUERY) return null

        val modeValue = data[6].toInt() and 0xFF
        return EqMode.fromValue(modeValue)
    }
}

object InEarDetectionParser {
    fun parse(data: ByteArray): Boolean? {
        if (data.size < 7) return null
        if ((data[0].toInt() and 0xFF) != 0x4E) return null

        val opCode = ((data[5].toInt() and 0xFF) shl 8) or (data[4].toInt() and 0xFF)
        if (opCode != Op.IN_EAR_QUERY) return null

        val status = data[6].toInt() and 0xFF
        return status == 0x01
    }
}
