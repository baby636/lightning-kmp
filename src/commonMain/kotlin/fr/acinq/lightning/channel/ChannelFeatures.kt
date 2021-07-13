package fr.acinq.lightning.channel

import fr.acinq.lightning.Feature
import fr.acinq.lightning.FeatureSupport
import fr.acinq.lightning.Features
import kotlinx.serialization.Serializable

/**
 * Subset of Bolt 9 features used to configure a channel and applicable over the lifetime of that channel.
 * Even if one of these features is later disabled at the connection level, it will still apply to the channel until the
 * channel is upgraded or closed.
 */
@Serializable
data class ChannelFeatures(val activated: Set<Feature>) {

    val channelType: ChannelType = when {
        activated.contains(Feature.AnchorOutputs) -> ChannelType.AnchorOutputs
        activated.contains(Feature.StaticRemoteKey) -> ChannelType.StaticRemoteKey
        else -> ChannelType.Standard
    }

    fun hasFeature(feature: Feature): Boolean = activated.contains(feature)

}

/** A channel type is a specific set of feature bits that represent persistent channel features as defined in Bolt 2. */
@Serializable
sealed class ChannelType {

    abstract val name: String
    abstract val features: Set<Feature>

    fun toFeatures(): Features = Features(features.associateWith { FeatureSupport.Mandatory })

    @Serializable
    object Standard : ChannelType() {
        override val name: String get() = "standard"
        override val features: Set<Feature> get() = setOf()
    }

    @Serializable
    object StaticRemoteKey : ChannelType() {
        override val name: String get() = "static_remotekey"
        override val features: Set<Feature> get() = setOf(Feature.StaticRemoteKey)
    }

    @Serializable
    object AnchorOutputs : ChannelType() {
        override val name: String get() = "anchor_outputs"
        override val features: Set<Feature> get() = setOf(Feature.StaticRemoteKey, Feature.AnchorOutputs)
    }

    companion object {

        // NB: Bolt 2: features must exactly match in order to identify a channel type.
        fun fromFeatures(features: Features): ChannelType? = when (features) {
            Features(Feature.StaticRemoteKey to FeatureSupport.Mandatory, Feature.AnchorOutputs to FeatureSupport.Mandatory) -> AnchorOutputs
            Features(Feature.StaticRemoteKey to FeatureSupport.Mandatory) -> StaticRemoteKey
            Features.empty -> Standard
            else -> null
        }

    }

}