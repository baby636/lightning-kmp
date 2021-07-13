package fr.acinq.lightning.channel

import fr.acinq.lightning.Feature
import fr.acinq.lightning.FeatureSupport
import fr.acinq.lightning.Features
import fr.acinq.lightning.tests.utils.LightningTestSuite
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChannelFeaturesTestsCommon : LightningTestSuite() {

    @Test
    fun `channel type uses mandatory features`() {
        assertTrue(ChannelType.Standard.features.isEmpty())
        assertEquals(ChannelType.StaticRemoteKey.toFeatures(), Features(Feature.StaticRemoteKey to FeatureSupport.Mandatory))
        assertEquals(ChannelType.AnchorOutputs.toFeatures(), Features(Feature.StaticRemoteKey to FeatureSupport.Mandatory, Feature.AnchorOutputs to FeatureSupport.Mandatory))
    }

    @Test
    fun `extract channel type from channel features`() {
        assertEquals(ChannelType.Standard, ChannelFeatures(setOf()).channelType)
        assertEquals(ChannelType.Standard, ChannelFeatures(setOf(Feature.ZeroReserveChannels, Feature.ZeroConfChannels)).channelType)
        assertEquals(ChannelType.StaticRemoteKey, ChannelFeatures(setOf(Feature.StaticRemoteKey)).channelType)
        assertEquals(ChannelType.StaticRemoteKey, ChannelFeatures(setOf(Feature.ZeroReserveChannels, Feature.StaticRemoteKey)).channelType)
        assertEquals(ChannelType.AnchorOutputs, ChannelFeatures(setOf(Feature.StaticRemoteKey, Feature.AnchorOutputs)).channelType)
        assertEquals(ChannelType.AnchorOutputs, ChannelFeatures(setOf(Feature.StaticRemoteKey, Feature.AnchorOutputs, Feature.Wumbo, Feature.ZeroConfChannels)).channelType)
    }

    @Test
    fun `extract channel type from features`() {
        assertEquals(ChannelType.Standard, ChannelType.fromFeatures(Features.empty))
        assertEquals(ChannelType.StaticRemoteKey, ChannelType.fromFeatures(Features(Feature.StaticRemoteKey to FeatureSupport.Mandatory)))
        assertEquals(ChannelType.AnchorOutputs, ChannelType.fromFeatures(Features(Feature.StaticRemoteKey to FeatureSupport.Mandatory, Feature.AnchorOutputs to FeatureSupport.Mandatory)))
        // Bolt 2 mandates that features match exactly.
        assertNull(ChannelType.fromFeatures(Features(Feature.ZeroReserveChannels to FeatureSupport.Optional)))
        assertNull(ChannelType.fromFeatures(Features(Feature.ZeroReserveChannels to FeatureSupport.Mandatory)))
        assertNull(ChannelType.fromFeatures(Features(Feature.StaticRemoteKey to FeatureSupport.Optional)))
        assertNull(ChannelType.fromFeatures(Features(Feature.StaticRemoteKey to FeatureSupport.Mandatory, Feature.PaymentSecret to FeatureSupport.Mandatory)))
        assertNull(ChannelType.fromFeatures(Features(Feature.StaticRemoteKey to FeatureSupport.Optional, Feature.AnchorOutputs to FeatureSupport.Optional)))
        assertNull(ChannelType.fromFeatures(Features(Feature.StaticRemoteKey to FeatureSupport.Optional, Feature.AnchorOutputs to FeatureSupport.Mandatory)))
        assertNull(ChannelType.fromFeatures(Features(Feature.StaticRemoteKey to FeatureSupport.Mandatory, Feature.AnchorOutputs to FeatureSupport.Optional)))
        assertNull(ChannelType.fromFeatures(Features(Feature.StaticRemoteKey to FeatureSupport.Mandatory, Feature.AnchorOutputs to FeatureSupport.Mandatory, Feature.Wumbo to FeatureSupport.Mandatory)))
    }

}