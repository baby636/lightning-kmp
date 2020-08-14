package fr.acinq.eklair.router

import fr.acinq.bitcoin.*
import fr.acinq.eklair.CltvExpiryDelta
import fr.acinq.eklair.MilliSatoshi
import fr.acinq.eklair.ShortChannelId
import fr.acinq.eklair.utils.currentTimestampSeconds
import fr.acinq.eklair.wire.ChannelUpdate
import kotlin.experimental.and
import kotlin.experimental.or

object Announcements {
    @Suppress("UNUSED_PARAMETER")
    fun channelUpdateWitnessEncode(
        chainHash
        : ByteVector32,
        shortChannelId: ShortChannelId,
        timestamp: Long,
        messageFlags: Byte,
        channelFlags: Byte,
        cltvExpiryDelta: CltvExpiryDelta,
        htlcMinimumMsat: MilliSatoshi,
        feeBaseMsat: MilliSatoshi,
        feeProportionalMillionths: Long,
        htlcMaximumMsat: MilliSatoshi?,
        unknownFields: ByteVector): ByteVector32
    {
        // TODO: implement channel update witness serialization
        return ByteVector32.Zeroes
    }

    /**
     * BOLT 7:
     * The creating node MUST set node-id-1 and node-id-2 to the public keys of the
     * two nodes who are operating the channel, such that node-id-1 is the numerically-lesser
     * of the two DER encoded keys sorted in ascending numerical order,
     *
     * @return true if localNodeId is node1
     */
    fun isNode1(localNodeId: PublicKey, remoteNodeId: PublicKey) = LexicographicalOrdering.isLessThan(localNodeId.value, remoteNodeId.value)

    /**
     * BOLT 7:
     * The creating node [...] MUST set the direction bit of flags to 0 if
     * the creating node is node-id-1 in that message, otherwise 1.
     *
     * @return true if the node who sent these flags is node1
     */
    fun isNode1(channelFlags: Byte): Boolean = (channelFlags and 1) == 0.toByte()

    /**
     * A node MAY create and send a channel_update with the disable bit set to
     * signal the temporary unavailability of a channel
     *
     * @return
     */
    fun isEnabled(channelFlags: Byte): Boolean = (channelFlags and 2) == 0.toByte()

    fun makeMessageFlags(hasOptionChannelHtlcMax: Boolean): Byte {
        var result: Byte = 0
        if (hasOptionChannelHtlcMax) result = result or 1
        return result
    }

    fun makeChannelFlags(isNode1: Boolean, enable: Boolean): Byte {
        var result: Byte = 0
        if (!isNode1) result = result or 1
        if (!enable) result = result or 2
        return result
    }

    fun makeChannelUpdate(
        chainHash: ByteVector32,
        nodeSecret: PrivateKey,
        remoteNodeId: PublicKey,
        shortChannelId: ShortChannelId,
        cltvExpiryDelta: CltvExpiryDelta,
        htlcMinimumMsat: MilliSatoshi,
        feeBaseMsat: MilliSatoshi,
        feeProportionalMillionths: Long,
        htlcMaximumMsat: MilliSatoshi,
        enable: Boolean = true,
        timestamp: Long = currentTimestampSeconds()
    ): ChannelUpdate {
        val messageFlags = makeMessageFlags(hasOptionChannelHtlcMax = true) // NB: we always support option_channel_htlc_max
        val channelFlags = makeChannelFlags(isNode1 = isNode1(nodeSecret.publicKey(), remoteNodeId), enable = enable)
        val htlcMaximumMsatOpt = htlcMaximumMsat

        val witness = channelUpdateWitnessEncode(
            chainHash,
            shortChannelId,
            timestamp,
            messageFlags,
            channelFlags,
            cltvExpiryDelta,
            htlcMinimumMsat,
            feeBaseMsat,
            feeProportionalMillionths,
            htlcMaximumMsatOpt,
            unknownFields = ByteVector.empty
        )
        val sig = Crypto.sign(witness, nodeSecret)
        return ChannelUpdate(
            signature = sig,
            chainHash = chainHash,
            shortChannelId = shortChannelId,
            timestamp = timestamp,
            messageFlags = messageFlags,
            channelFlags = channelFlags,
            cltvExpiryDelta = cltvExpiryDelta,
            htlcMinimumMsat = htlcMinimumMsat,
            feeBaseMsat = feeBaseMsat,
            feeProportionalMillionths = feeProportionalMillionths,
            htlcMaximumMsat = htlcMaximumMsatOpt
        )
    }

    @Suppress("UNUSED_PARAMETER")
    fun checkSig(upd: ChannelUpdate, nodeId: PublicKey): Boolean = true
}