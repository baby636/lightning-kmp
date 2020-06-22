package fr.acinq.eklair.payment.relay

import fr.acinq.bitcoin.ByteVector32
import fr.acinq.eklair.MilliSatoshi
import fr.acinq.eklair.utils.UUID

sealed class Origin {
    /** Our node is the origin of the payment. */
    data class Local(val id: UUID) : Origin() // we don't persist reference to local actors

    /** Our node forwarded a single incoming HTLC to an outgoing channel. */
    data class Relayed(val originChannelId: ByteVector32, val originHtlcId: Long, val amountIn: MilliSatoshi, val amountOut: MilliSatoshi) : Origin()

    /**
     * Our node forwarded an incoming HTLC set to a remote outgoing node (potentially producing multiple downstream HTLCs).
     *
     * @param origins       origin channelIds and htlcIds.
     */
    data class TrampolineRelayed(val origins: List<Pair<ByteVector32, Long>>) : Origin()

}