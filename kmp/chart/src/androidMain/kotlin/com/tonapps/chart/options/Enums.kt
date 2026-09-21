package com.tonapps.chart.options

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/** lightweight-charts encodes these enums as plain integers. */
internal abstract class IntEnumSerializer<T>(
    serialName: String,
    private val entries: List<T>,
    private val valueOf: (T) -> Int,
) : KSerializer<T> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: T) {
        encoder.encodeInt(valueOf(value))
    }

    override fun deserialize(decoder: Decoder): T {
        val raw = decoder.decodeInt()
        return entries.first { entry -> valueOf(entry) == raw }
    }
}

@Serializable(with = LineStyle.Companion::class)
enum class LineStyle(val value: Int) {
    Solid(0),
    Dotted(1),
    Dashed(2),
    LargeDashed(3),
    SparseDotted(4);

    internal companion object : IntEnumSerializer<LineStyle>("LineStyle", entries, LineStyle::value)
}

@Serializable(with = CrosshairMode.Companion::class)
enum class CrosshairMode(val value: Int) {
    Normal(0),
    Magnet(1),
    Hidden(2);

    internal companion object : IntEnumSerializer<CrosshairMode>("CrosshairMode", entries, CrosshairMode::value)
}

@Serializable(with = TrackingModeExitMode.Companion::class)
enum class TrackingModeExitMode(val value: Int) {
    OnTouchEnd(0),
    OnNextTap(1);

    internal companion object :
        IntEnumSerializer<TrackingModeExitMode>("TrackingModeExitMode", entries, TrackingModeExitMode::value)
}

@Serializable(with = PriceLineSource.Companion::class)
enum class PriceLineSource(val value: Int) {
    LastBar(0),
    LastVisible(1);

    internal companion object : IntEnumSerializer<PriceLineSource>("PriceLineSource", entries, PriceLineSource::value)
}
