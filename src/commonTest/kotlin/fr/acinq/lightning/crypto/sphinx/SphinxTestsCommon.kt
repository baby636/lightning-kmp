package fr.acinq.lightning.crypto.sphinx

import fr.acinq.bitcoin.ByteVector
import fr.acinq.bitcoin.ByteVector32
import fr.acinq.bitcoin.PrivateKey
import fr.acinq.bitcoin.PublicKey
import fr.acinq.lightning.crypto.sphinx.Sphinx.computeEphemeralPublicKeysAndSharedSecrets
import fr.acinq.lightning.crypto.sphinx.Sphinx.generateFiller
import fr.acinq.lightning.crypto.sphinx.Sphinx.peekPayloadLength
import fr.acinq.lightning.tests.utils.LightningTestSuite
import fr.acinq.lightning.utils.Either
import fr.acinq.lightning.utils.msat
import fr.acinq.lightning.wire.*
import fr.acinq.secp256k1.Hex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class SphinxTestsCommon : LightningTestSuite() {
    private val privKeys = listOf(
        PrivateKey(ByteVector32("4141414141414141414141414141414141414141414141414141414141414141")),
        PrivateKey(ByteVector32("4242424242424242424242424242424242424242424242424242424242424242")),
        PrivateKey(ByteVector32("4343434343434343434343434343434343434343434343434343434343434343")),
        PrivateKey(ByteVector32("4444444444444444444444444444444444444444444444444444444444444444")),
        PrivateKey(ByteVector32("4545454545454545454545454545454545454545454545454545454545454545"))
    )
    private val publicKeys = privKeys.map { it.publicKey() }

    // This test vector uses payloads with a fixed size.
    // origin -> node #0 -> node #1 -> node #2 -> node #3 -> node #4
    private val referenceFixedSizePayloads = listOf(
        ByteVector("000000000000000000000000000000000000000000000000000000000000000000"),
        ByteVector("000101010101010101000000000000000100000001000000000000000000000000"),
        ByteVector("000202020202020202000000000000000200000002000000000000000000000000"),
        ByteVector("000303030303030303000000000000000300000003000000000000000000000000"),
        ByteVector("000404040404040404000000000000000400000004000000000000000000000000")
    )

    // This test vector uses variable-size payloads intertwined with fixed-size payloads.
    // origin -> node #0 -> node #1 -> node #2 -> node #3 -> node #4
    private val referenceVariableSizePayloads = listOf(
        ByteVector("000000000000000000000000000000000000000000000000000000000000000000"),
        ByteVector("140101010101010101000000000000000100000001"),
        ByteVector("fd0100000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f505152535455565758595a5b5c5d5e5f606162636465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9fa0a1a2a3a4a5a6a7a8a9aaabacadaeafb0b1b2b3b4b5b6b7b8b9babbbcbdbebfc0c1c2c3c4c5c6c7c8c9cacbcccdcecfd0d1d2d3d4d5d6d7d8d9dadbdcdddedfe0e1e2e3e4e5e6e7e8e9eaebecedeeeff0f1f2f3f4f5f6f7f8f9fafbfcfdfeff"),
        ByteVector("140303030303030303000000000000000300000003"),
        ByteVector("000404040404040404000000000000000400000004000000000000000000000000")
    )

    // This test vector uses variable-sized payloads and fills the whole onion packet.
    // origin -> node #0 -> node #1 -> node #2 -> node #3 -> node #4
    private val variableSizePayloadsFull = listOf(
        ByteVector("8b09000000000000000030000000000000000000000000000000000000000000000000000000000025000000000000000000000000000000000000000000000000250000000000000000000000000000000000000000000000002500000000000000000000000000000000000000000000000025000000000000000000000000000000000000000000000000"),
        ByteVector("fd012a08000000000000009000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000200000000000000000000000000000000000000020000000000000000000000000000000000000002000000000000000000000000000000000000000200000000000000000000000000000000000000020000000000000000000000000000000000000002000000000000000000000000000000000000000200000000000000000000000000000000000000020000000000000000000000000000000000000002000000000000000000000000000000000000000"),
        ByteVector("620800000000000000900000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"),
        ByteVector("fc120000000000000000000000240000000000000000000000000000000000000000000000240000000000000000000000000000000000000000000000240000000000000000000000000000000000000000000000240000000000000000000000000000000000000000000000240000000000000000000000000000000000000000000000240000000000000000000000000000000000000000000000240000000000000000000000000000000000000000000000240000000000000000000000000000000000000000000000240000000000000000000000000000000000000000000000240000000000000000000000000000000000000000000000"),
        ByteVector("fd01582200000000000000000000000000000000000000000022000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000")
    )

    // This test vector uses a single variable-sized payload filling the whole onion payload.
    // origin -> recipient
    private val variableSizeOneHopPayload = listOf(
        ByteVector(
            "fd04f16500000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
        )
    )

    // This test vector uses trampoline variable-size payloads.
    private val trampolinePayloads = listOf(
        ByteVector("2a 02020231 040190 f8210324653eac434488002cc06bbfb7f10fe18991e35f9fe4302dbea6d2353dc0ab1c"),
        ByteVector("35 fa 33 010000000000000000000000040000000000000000000000000ff0000000000000000000000000000000000000000000000000"),
        ByteVector("23 f8 21 032c0b7cf95324a07d05398b240174dc0c2be444d96b159aa6c7f7b1e668680991"),
        ByteVector("00 0303030303030303 0000000000000003 00000003 000000000000000000000000"),
        ByteVector("23 f8 21 02eec7245d6b7d2ccb30380bfbe2a3648cd7a942653f5aa340edcea1f283686619")
    )

    private val sessionKey: PrivateKey = PrivateKey(ByteVector32("4141414141414141414141414141414141414141414141414141414141414141"))
    private val associatedData = ByteVector32("4242424242424242424242424242424242424242424242424242424242424242")

    init {
        require(
            publicKeys == listOf(
                PublicKey.fromHex("02eec7245d6b7d2ccb30380bfbe2a3648cd7a942653f5aa340edcea1f283686619"),
                PublicKey.fromHex("0324653eac434488002cc06bbfb7f10fe18991e35f9fe4302dbea6d2353dc0ab1c"),
                PublicKey.fromHex("027f31ebc5462c1fdce1b737ecff52d37d75dea43ce11c74d25aa297165faa2007"),
                PublicKey.fromHex("032c0b7cf95324a07d05398b240174dc0c2be444d96b159aa6c7f7b1e668680991"),
                PublicKey.fromHex("02edabbd16b41c8371b92ef2f04c1185b4f03b6dcd52ba9b78d9d7c89c8f221145")
            )
        )
    }

    @Test
    fun `generate ephemeral keys and secrets (reference test vector)`() {
        val (ephkeys, sharedsecrets) = computeEphemeralPublicKeysAndSharedSecrets(sessionKey, publicKeys)
        assertEquals(ephkeys[0], PublicKey.fromHex("02eec7245d6b7d2ccb30380bfbe2a3648cd7a942653f5aa340edcea1f283686619"))
        assertEquals(sharedsecrets[0], ByteVector32("53eb63ea8a3fec3b3cd433b85cd62a4b145e1dda09391b348c4e1cd36a03ea66"))
        assertEquals(ephkeys[1], PublicKey.fromHex("028f9438bfbf7feac2e108d677e3a82da596be706cc1cf342b75c7b7e22bf4e6e2"))
        assertEquals(sharedsecrets[1], ByteVector32("a6519e98832a0b179f62123b3567c106db99ee37bef036e783263602f3488fae"))
        assertEquals(ephkeys[2], PublicKey.fromHex("03bfd8225241ea71cd0843db7709f4c222f62ff2d4516fd38b39914ab6b83e0da0"))
        assertEquals(sharedsecrets[2], ByteVector32("3a6b412548762f0dbccce5c7ae7bb8147d1caf9b5471c34120b30bc9c04891cc"))
        assertEquals(ephkeys[3], PublicKey.fromHex("031dde6926381289671300239ea8e57ffaf9bebd05b9a5b95beaf07af05cd43595"))
        assertEquals(sharedsecrets[3], ByteVector32("21e13c2d7cfe7e18836df50872466117a295783ab8aab0e7ecc8c725503ad02d"))
        assertEquals(ephkeys[4], PublicKey.fromHex("03a214ebd875aab6ddfd77f22c5e7311d7f77f17a169e599f157bbcdae8bf071f4"))
        assertEquals(sharedsecrets[4], ByteVector32("b5756b9b542727dbafc6765a49488b023a725d631af688fc031217e90770c328"))
    }

    @Test
    fun `generate filler with fixed-size payloads (reference test vector)`() {
        val (_, sharedsecrets) = computeEphemeralPublicKeysAndSharedSecrets(sessionKey, publicKeys)
        val filler = generateFiller("rho", sharedsecrets.dropLast(1), referenceFixedSizePayloads.dropLast(1).map { it.toByteArray() }, OnionRoutingPacket.PaymentPacketLength)
        assertEquals(
            Hex.encode(filler),
            "c6b008cf6414ed6e4c42c291eb505e9f22f5fe7d0ecdd15a833f4d016ac974d33adc6ea3293e20859e87ebfb937ba406abd025d14af692b12e9c9c2adbe307a679779259676211c071e614fdb386d1ff02db223a5b2fae03df68d321c7b29f7c7240edd3fa1b7cb6903f89dc01abf41b2eb0b49b6b8d73bb0774b58204c0d0e96d3cce45ad75406be0bc009e327b3e712a4bd178609c00b41da2daf8a4b0e1319f07a492ab4efb056f0f599f75e6dc7e0d10ce1cf59088ab6e873de377343880f7a24f0e36731a0b72092f8d5bc8cd346762e93b2bf203d00264e4bc136fc142de8f7b69154deb05854ea88e2d7506222c95ba1aab065c8a851391377d3406a35a9af3ac"
        )
    }

    @Test
    fun `generate filler with variable-size payloads`() {
        val (_, sharedsecrets) = computeEphemeralPublicKeysAndSharedSecrets(sessionKey, publicKeys)
        val filler = generateFiller("rho", sharedsecrets.dropLast(1), referenceVariableSizePayloads.dropLast(1).map { it.toByteArray() }, OnionRoutingPacket.PaymentPacketLength)
        assertEquals(
            Hex.encode(filler),
            "b77d99c935d3f32469844f7e09340a91ded147557bdd0456c369f7e449587c0f5666faab58040146db49024db88553729bce12b860391c29c1779f022ae48a9cb314ca35d73fc91addc92632bcf7ba6fd9f38e6fd30fabcedbd5407b6648073c38331ee7ab0332f41f550c180e1601f8c25809ed75b3a1e78635a2ef1b828e92c9658e76e49f995d72cf9781eec0c838901d0bdde3ac21c13b4979ac9e738a1c4d0b9741d58e777ad1aed01263ad1390d36a18a6b92f4f799dcf75edbb43b7515e8d72cb4f827a9af0e7b9338d07b1a24e0305b5535f5b851b1144bad6238b9d9482b5ba6413f1aafac3cdde5067966ed8b78f7c1c5f916a05f874d5f17a2b7d0ae75d66a5f1bb6ff932570dc5a0cf3ce04eb5d26bc55c2057af1f8326e20a7d6f0ae644f09d00fac80de60f20aceee85be41a074d3e1dda017db79d0070b99f54736396f206ee3777abd4c00a4bb95c871750409261e3b01e59a3793a9c20159aae4988c68397a1443be6370fd9614e46108291e615691729faea58537209fa668a172d066d0efff9bc77c2bd34bd77870ad79effd80140990e36731a0b72092f8d5bc8cd346762e93b2bf203d00264e4bc136fc142de8f7b69154deb05854ea88e2d7506222c95ba1aab065c8a"
        )
    }

    @Test
    fun `peek at per-hop payload length`() {
        val testCases = mapOf(
            34 to Hex.decode("01"),
            41 to Hex.decode("08"),
            65 to Hex.decode("00"),
            285 to Hex.decode("fc"),
            288 to Hex.decode("fd00fd"),
            65570 to Hex.decode("fdffff")
        )

        testCases.forEach {
            assertEquals(it.key, peekPayloadLength(it.value))
        }
    }

    @Test
    fun `is last packet`() {
        val dummyPayload = ByteArray(OnionRoutingPacket.PaymentPacketLength)
        val emptyMac = ByteArray(32)
        val nonEmptyMac = ByteArray(32) { 1 }
        val packetEmptyMac = OnionRoutingPacketSerializer(OnionRoutingPacket.PaymentPacketLength).read(byteArrayOf(0) + publicKeys.first().value.toByteArray() + dummyPayload + emptyMac)
        val packetNonEmptyMac = OnionRoutingPacketSerializer(OnionRoutingPacket.PaymentPacketLength).read(byteArrayOf(0) + publicKeys.first().value.toByteArray() + dummyPayload + nonEmptyMac)
        val testCases = listOf(
            // Bolt 1.0 payloads use the next packet's hmac to signal termination.
            Pair(true, DecryptedPacket(ByteVector("00"), packetEmptyMac, ByteVector32.One)),
            Pair(false, DecryptedPacket(ByteVector("00"), packetNonEmptyMac, ByteVector32.One)),
            // Bolt 1.1 payloads currently also use the next packet's hmac to signal termination.
            Pair(true, DecryptedPacket(ByteVector("0101"), packetEmptyMac, ByteVector32.One)),
            Pair(false, DecryptedPacket(ByteVector("0101"), packetNonEmptyMac, ByteVector32.One)),
            Pair(false, DecryptedPacket(ByteVector("0100"), packetNonEmptyMac, ByteVector32.One)),
            Pair(false, DecryptedPacket(ByteVector("0101"), packetNonEmptyMac, ByteVector32.One))
        )

        testCases.forEach {
            assertEquals(it.first, it.second.isLastPacket)
        }
    }

    @Test
    fun `bad onion`() {
        val testCases = listOf(
            Pair(InvalidOnionVersion(ByteVector32("2f89b15c6cb0bb256d7a71b66de0d50cd3dd806f77d1cc1a3b0d86a0becd28ce")), OnionRoutingPacketSerializer(65).read(byteArrayOf(1) + ByteArray(33) + ByteArray(65) { 1 } + ByteArray(32))),
            Pair(InvalidOnionKey(ByteVector32("d2602c65fc331d6ae728331ae50e602f35929312ca7a951dc5ce250031b6b999")), OnionRoutingPacketSerializer(65).read(byteArrayOf(0) + ByteArray(33) + ByteArray(65) { 1 } + ByteArray(32))),
            Pair(
                InvalidOnionHmac(ByteVector32("3c01a86e6bc51b44a2718745fbbbc71a5c5dde5f46a489da17046c9d097bb303")),
                OnionRoutingPacketSerializer(42).read(byteArrayOf(0) + publicKeys.first().value.toByteArray() + ByteArray(42) { 1 } + ByteArray(32) { 42 })
            ),
        )

        testCases.forEach {
            val payloadLength = it.second.payload.size()
            val onionErr = (Sphinx.peel(privKeys.first(), associatedData, it.second, payloadLength) as Either.Left).value
            assertEquals(it.first, onionErr)
        }
    }

    @Test
    fun `create packet with fixed-size payloads (reference test vector)`() {
        val packetAndSecrets = Sphinx.create(sessionKey, publicKeys, referenceFixedSizePayloads.map { it.toByteArray() }, associatedData, OnionRoutingPacket.PaymentPacketLength)
        val onion = packetAndSecrets.packet
        assertEquals(
            Hex.encode(OnionRoutingPacketSerializer(OnionRoutingPacket.PaymentPacketLength).write(onion)),
            "0002eec7245d6b7d2ccb30380bfbe2a3648cd7a942653f5aa340edcea1f283686619e5f14350c2a76fc232b5e46d421e9615471ab9e0bc887beff8c95fdb878f7b3a71e87f9aab8f6378c6ff744c1f34b393ad28d065b535c1a8668d85d3b34a1b3befd10f7d61ab590531cf08000178a333a347f8b4072e216400406bdf3bf038659793a1f9e7abc789266cc861cabd95818c0fc8efbdfdc14e3f7c2bc7eb8d6a79ef75ce721caad69320c3a469a202f3e468c67eaf7a7cda226d0fd32f7b48084dca885d014698cf05d742557763d9cb743faeae65dcc79dddaecf27fe5942be5380d15e9a1ec866abe044a9ad635778ba61fc0776dc832b39451bd5d35072d2269cf9b040a2a2fba158a0d8085926dc2e44f0c88bf487da56e13ef2d5e676a8589881b4869ed4c7f0218ff8c6c7dd7221d189c65b3b9aaa71a01484b122846c7c7b57e02e679ea8469b70e14fe4f70fee4d87b910cf144be6fe48eef24da475c0b0bcc6565a9f99728426ce2380a9580e2a9442481ceae7679906c30b1a0e21a10f26150e0645ab6edfdab1ce8f8bea7b1dee511c5fd38ac0e702c1c15bb86b52bca1b71e15b96982d262a442024c33ceb7dd8f949063c2e5e613e873250e2f8708bd4e1924abd45f65c2fa5617bfb10ee9e4a42d6b5811acc8029c16274f937dac9e8817c7e579fdb767ffe277f26d413ced06b620ede8362081da21cf67c2ca9d6f15fe5bc05f82f5bb93f8916bad3d63338ca824f3bbc11b57ce94a5fa1bc239533679903d6fec92a8c792fd86e2960188c14f21e399cfd72a50c620e10aefc6249360b463df9a89bf6836f4f26359207b765578e5ed76ae9f31b1cc48324be576e3d8e44d217445dba466f9b6293fdf05448584eb64f61e02903f834518622b7d4732471c6e0e22e22d1f45e31f0509eab39cdea5980a492a1da2aaac55a98a01216cd4bfe7abaa682af0fbff2dfed030ba28f1285df750e4d3477190dd193f8643b61d8ac1c427d590badb1f61a05d480908fbdc7c6f0502dd0c4abb51d725e92f95da2a8facb79881a844e2026911adcc659d1fb20a2fce63787c8bb0d9f6789c4b231c76da81c3f0718eb7156565a081d2be6b4170c0e0bcebddd459f53db2590c974bca0d705c055dee8c629bf854a5d58edc85228499ec6dde80cce4c8910b81b1e9e8b0f43bd39c8d69c3a80672729b7dc952dd9448688b6bd06afc2d2819cda80b66c57b52ccf7ac1a86601410d18d0c732f69de792e0894a9541684ef174de766fd4ce55efea8f53812867be6a391ac865802dbc26d93959df327ec2667c7256aa5a1d3c45a69a6158f285d6c97c3b8eedb09527848500517995a9eae4cd911df531544c77f5a9a2f22313e3eb72ca7a07dba243476bc926992e0d1e58b4a2fc8c7b01e0cad726237933ea319bad7537d39f3ed635d1e6c1d29e97b3d2160a09e30ee2b65ac5bce00996a73c008bcf351cecb97b6833b6d121dcf4644260b2946ea204732ac9954b228f0beaa15071930fd9583dfc466d12b5f0eeeba6dcf23d5ce8ae62ee5796359d97a4a15955c778d868d0ef9991d9f2833b5bb66119c5f8b396fd108baed7906cbb3cc376d13551caed97fece6f42a4c908ee279f1127fda1dd3ee77d8de0a6f3c135fa3f1cffe38591b6738dc97b55f0acc52be9753ce53e64d7e497bb00ca6123758df3b68fad99e35c04389f7514a8e36039f541598a417275e77869989782325a15b5342ac5011ff07af698584b476b35d941a4981eac590a07a092bb50342da5d3341f901aa07964a8d02b623c7b106dd0ae50bfa007a22d46c8772fa55558176602946cb1d11ea5460db7586fb89c6d3bcd3ab6dd20df4a4db63d2e7d52380800ad812b8640887e027e946df96488b47fbc4a4fadaa8beda4abe446fafea5403fae2ef"
        )

        val decrypted0 = (Sphinx.peel(privKeys[0], associatedData, onion, OnionRoutingPacket.PaymentPacketLength) as Either.Right).value
        val decrypted1 = (Sphinx.peel(privKeys[1], associatedData, decrypted0.nextPacket, OnionRoutingPacket.PaymentPacketLength) as Either.Right).value
        val decrypted2 = (Sphinx.peel(privKeys[2], associatedData, decrypted1.nextPacket, OnionRoutingPacket.PaymentPacketLength) as Either.Right).value
        val decrypted3 = (Sphinx.peel(privKeys[3], associatedData, decrypted2.nextPacket, OnionRoutingPacket.PaymentPacketLength) as Either.Right).value
        val decrypted4 = (Sphinx.peel(privKeys[4], associatedData, decrypted3.nextPacket, OnionRoutingPacket.PaymentPacketLength) as Either.Right).value
        assertEquals(listOf(decrypted0.payload, decrypted1.payload, decrypted2.payload, decrypted3.payload, decrypted4.payload), referenceFixedSizePayloads)
        assertEquals(listOf(decrypted0.sharedSecret, decrypted1.sharedSecret, decrypted2.sharedSecret, decrypted3.sharedSecret, decrypted4.sharedSecret), packetAndSecrets.sharedSecrets.perHopSecrets.map { it.first })

        val packets = listOf(decrypted0.nextPacket, decrypted1.nextPacket, decrypted2.nextPacket, decrypted3.nextPacket, decrypted4.nextPacket)
        assertEquals(packets[0].hmac, ByteVector32("a93aa4f40241cef3e764e24b28570a0db39af82ab5102c3a04e51bec8cca9394"))
        assertEquals(packets[1].hmac, ByteVector32("5d1b11f1efeaa9be32eb1c74b113c0b46f056bb49e2a35a51ceaece6bd31332c"))
        assertEquals(packets[2].hmac, ByteVector32("19ca6357b5552b28e50ae226854eec874bbbf7025cf290a34c06b4eff5d2bac0"))
        assertEquals(packets[3].hmac, ByteVector32("16d4553c6084b369073d259381bb5b02c16bb2c590bbd9e69346cf7ebd563229"))
        // this means that node #4 is the last node
        assertEquals(packets[4].hmac, ByteVector32("0000000000000000000000000000000000000000000000000000000000000000"))
    }

    @Test
    fun `create packet with variable-size payloads (reference test vector)`() {
        val packetAndSecrets = Sphinx.create(sessionKey, publicKeys, referenceVariableSizePayloads.map { it.toByteArray() }, associatedData, OnionRoutingPacket.PaymentPacketLength)
        val onion = packetAndSecrets.packet
        assertEquals(
            Hex.encode(OnionRoutingPacketSerializer(OnionRoutingPacket.PaymentPacketLength).write(onion)),
            "0002eec7245d6b7d2ccb30380bfbe2a3648cd7a942653f5aa340edcea1f283686619e5f14350c2a76fc232b5e46d421e9615471ab9e0bc887beff8c95fdb878f7b3a710f8eaf9ccc768f66bb5dec1f7827f33c43fe2ddd05614c8283aa78e9e7573f87c50f7d61ab590531cf08000178a333a347f8b4072e1cea42da7552402b10765adae3f581408f35ff0a71a34b78b1d8ecae77df96c6404bae9a8e8d7178977d7094a1ae549f89338c0777551f874159eb42d3a59fb9285ad4e24883f27de23942ec966611e99bee1cee503455be9e8e642cef6cef7b9864130f692283f8a973d47a8f1c1726b6e59969385975c766e35737c8d76388b64f748ee7943ffb0e2ee45c57a1abc40762ae598723d21bd184e2b338f68ebff47219357bd19cd7e01e2337b806ef4d717888e129e59cd3dc31e6201ccb2fd6d7499836f37a993262468bcb3a4dcd03a22818aca49c6b7b9b8e9e870045631d8e039b066ff86e0d1b7291f71cefa7264c70404a8e538b566c17ccc5feab231401e6c08a01bd5edfc1aa8e3e533b96e82d1f91118d508924b923531929aea889fcdf057f5995d9731c4bf796fb0e41c885d488dcbc68eb742e27f44310b276edc6f652658149e7e9ced4edde5d38c9b8f92e16f6b4ab13d710ee5c193921909bdd75db331cd9d7581a39fca50814ed8d9d402b86e7f8f6ac2f3bca8e6fe47eb45fbdd3be21a8a8d200797eae3c9a0497132f92410d804977408494dff49dd3d8bce248e0b74fd9e6f0f7102c25ddfa02bd9ad9f746abbfa3379834bc2380d58e9d23237821475a1874484783a15d68f47d3dc339f38d9bf925655d5c946778680fd6d1f062f84128895aff09d35d6c92cca63d3f95a9ee8f2a84f383b4d6a087533e65de12fc8dcaf85777736a2088ff4b22462265028695b37e70963c10df8ef2458756c73007dc3e544340927f9e9f5ea4816a9fd9832c311d122e9512739a6b4714bba590e31caa143ce83cb84b36c738c60c3190ff70cd9ac286a9fd2ab619399b68f1f7447be376ce884b5913c8496d01cbf7a44a60b6e6747513f69dc538f340bc1388e0fde5d0c1db50a4dcb9cc0576e0e2474e4853af9623212578d502757ffb2e0e749695ed70f61c116560d0d4154b64dcf3cbf3c91d89fb6dd004dc19588e3479fcc63c394a4f9e8a3b8b961fce8a532304f1337f1a697a1bb14b94d2953f39b73b6a3125d24f27fcd4f60437881185370bde68a5454d816e7a70d4cea582effab9a4f1b730437e35f7a5c4b769c7b72f0346887c1e63576b2f1e2b3706142586883f8cf3a23595cc8e35a52ad290afd8d2f8bcd5b4c1b891583a4159af7110ecde092079209c6ec46d2bda60b04c519bb8bc6dffb5c87f310814ef2f3003671b3c90ddf5d0173a70504c2280d31f17c061f4bb12a978122c8a2a618bb7d1edcf14f84bf0fa181798b826a254fca8b6d7c81e0beb01bd77f6461be3c8647301d02b04753b0771105986aa0cbc13f7718d64e1b3437e8eef1d319359914a7932548c91570ef3ea741083ca5be5ff43c6d9444d29df06f76ec3dc936e3d180f4b6d0fbc495487c7d44d7c8fe4a70d5ff1461d0d9593f3f898c919c363fa18341ce9dae54f898ccf3fe792136682272941563387263c51b2a2f32363b804672cc158c9230472b554090a661aa81525d11876eefdcc45442249e61e07284592f1606491de5c0324d3af4be035d7ede75b957e879e9770cdde2e1bbc1ef75d45fe555f1ff6ac296a2f648eeee59c7c08260226ea333c285bcf37a9bbfa57ba2ab8083c4be6fc2ebe279537d22da96a07392908cf22b233337a74fe5c603b51712b43c3ee55010ee3d44dd9ba82bba3145ec358f863e04bbfa53799a7a9216718fd5859da2f0deb77b8e315ad6868fdec9400f45a48e6dc8ddbaeb3"
        )

        val decrypted0 = (Sphinx.peel(privKeys[0], associatedData, onion, OnionRoutingPacket.PaymentPacketLength) as Either.Right).value
        val decrypted1 = (Sphinx.peel(privKeys[1], associatedData, decrypted0.nextPacket, OnionRoutingPacket.PaymentPacketLength) as Either.Right).value
        val decrypted2 = (Sphinx.peel(privKeys[2], associatedData, decrypted1.nextPacket, OnionRoutingPacket.PaymentPacketLength) as Either.Right).value
        val decrypted3 = (Sphinx.peel(privKeys[3], associatedData, decrypted2.nextPacket, OnionRoutingPacket.PaymentPacketLength) as Either.Right).value
        val decrypted4 = (Sphinx.peel(privKeys[4], associatedData, decrypted3.nextPacket, OnionRoutingPacket.PaymentPacketLength) as Either.Right).value
        assertEquals(listOf(decrypted0.payload, decrypted1.payload, decrypted2.payload, decrypted3.payload, decrypted4.payload), referenceVariableSizePayloads)
        assertEquals(listOf(decrypted0.sharedSecret, decrypted1.sharedSecret, decrypted2.sharedSecret, decrypted3.sharedSecret, decrypted4.sharedSecret), packetAndSecrets.sharedSecrets.perHopSecrets.map { it.first })

        val packets = listOf(decrypted0.nextPacket, decrypted1.nextPacket, decrypted2.nextPacket, decrypted3.nextPacket, decrypted4.nextPacket)
        assertEquals(packets[0].hmac, ByteVector32("4ecb91c341543953a34d424b64c36a9cd8b4b04285b0c8de0acab0b6218697fc"))
        assertEquals(packets[1].hmac, ByteVector32("3d8e429a1e8d7bdb2813cd491f17771aa75670d88b299db1954aa015d035408f"))
        assertEquals(packets[2].hmac, ByteVector32("30ad58843d142609ed7ae2b960c8ce0e331f7d45c7d705f67fd3f3978cd7b8f8"))
        assertEquals(packets[3].hmac, ByteVector32("4ee0600ee609f1f3356b85b0af8ead34c2db4ae93e3978d15f983040e8b01acd"))
        // this means that node #4 is the last node
        assertEquals(packets[4].hmac, ByteVector32("0000000000000000000000000000000000000000000000000000000000000000"))
    }

    @Test
    fun `create packet with variable-size payloads filling the onion`() {
        val packetAndSecrets = Sphinx.create(sessionKey, publicKeys, variableSizePayloadsFull.map { it.toByteArray() }, associatedData, OnionRoutingPacket.PaymentPacketLength)
        val onion = packetAndSecrets.packet
        assertEquals(
            Hex.encode(OnionRoutingPacketSerializer(OnionRoutingPacket.PaymentPacketLength).write(onion)),
            "0002eec7245d6b7d2ccb30380bfbe2a3648cd7a942653f5aa340edcea1f2836866196ef84350c2a76fc232b5d46d421e9615471ab9e0bc887beff8c95fdb878f7b3a7141453e5f8d22b6101810ae541ce499a09b4a9d9f80d1845c8960c85fc6d1a87bf74b2ce49922898e9353fa268086c00ae8b7f718405b72ad3829dbb38c85e02a00427eb4bdbda8fcd42b44708a9efde49cf776b75ebb389bf84d0bfbf58590e510e034572a01e409c309396778760423a8d8754c52e9a01a8f0e271cba5068bab5ee5bd0b5cd98276b0e04d60ba6a0f6bafd75ff41903ab352a1f47586eae3c6c8e437d4308766f71052b46ba2efbd87c0a781e8b3f456300fc7efbefc78ab515338666aed2070e674143c30b520b9cc1782ba8b46454db0d4ce72589cfc2eafb2db452ec98573ad08496483741de5376bfc7357fc6ea629e31236ba6ba7703014959129141a1719788ec83884f2e9151a680e2a96d2bcc67a8a2935aa11acee1f9d04812045b4ae5491220313756b5b9a0a6f867f2a95be1fab14870f04eeab694d9594620632b14ec4b424b495914f3dc587f75cd4582c113bb61e34a0fa7f79f97463be4e3c6fb99516889ed020acee419bb173d38e5ba18a00065e11fd733cf9ae46505dbb4ef70ef2f502601f4f6ee1fdb9d17435e15080e962f24760843f35bac1ac079b694ff7c347c1ed6a87f02b0758fbf00917764716c68ed7d6e6c0e75ccdb6dc7fa59554784b3ad906127ea77a6cdd814662ee7d57a939e28d77b3da47efc072436a3fd7f9c40515af8c4903764301e62b57153a5ca03ff5bb49c7dc8d3b2858100fb4aa5df7a94a271b73a76129445a3ea180d84d19029c003c164db926ed6983e5219028721a294f145e3fcc20915b8a2147efc8b5d508339f64970feee3e2da9b9c9348c1a0a4df7527d0ae3f8ae507a5beb5c73c2016ecf387a3cd8b79df80a8e9412e707cb9c761a0809a84c606a779567f9f0edf685b38c98877e90d02aedd096ed841e50abf2114ce01efbff04788fb280f870eca20c7ec353d5c381903e7d08fc57695fd79c27d43e7bd603a876068d3f1c7f45af99003e5eec7e8d8c91e395320f1fc421ef3552ea033129429383304b760c8f93de342417c3223c2112a623c3514480cdfae8ec15a99abfca71b03a8396f19edc3d5000bcfb77b5544813476b1b521345f4da396db09e783870b97bc2034bd11611db30ed2514438b046f1eb7093eceddfb1e73880786cd7b540a3896eaadd0a0692e4b19439815b5f2ec855ec8ececce889442a64037e956452a3f7b86cb3780b3e316c8dde464bc74a60a85b613f849eb0b29daf81892877bd4be9ba5997fc35544d3c2a00e5e1f45dc925607d952c6a89721bd0b6f6aec03314d667166a5b8b18471403be7018b2479aaef6c7c6c554a50a98b717dff06d50be39fb36dc03e678e0a52fc615be46b223e3bee83fa0c7c47a1f29fb94f1e9eebf6c9ecf8fc79ae847df2effb60d07aba301fc536546ec4899eedb4fec9a9bed79e3a83c4b32757745778e977e485c67c0f12bbc82c0b3bb0f4df0bd13d046fed4446f54cd85bfce55ef781a80e5f63d289d08de001237928c2a4e0c8694d0c1e68cc23f2409f30009019085e831a928e7bc5b00a1f29d25482f7fd0b6dad30e6ef8edc68ddf7db404ea7d11540fc2cee74863d64af4c945457e04b7bea0a5fb8636edadb1e1d6f2630d61062b781c1821f46eddadf269ea1fada829547590081b16bc116e074cae0224a375f2d9ce16e836687c89cd285e3b40f1e59ce2caa3d1d8cf37ee4d5e3abe7ef0afd6ffeb4fd6905677b950894863c828ab8d93519566f69fa3c2129da763bf58d9c4d2837d4d9e13821258f7e7098b34f695a589bd9eb568ba51ee3014b2d3ba1d4cf9ebaed0231ed57ecea7bd918216"
        )

        val decrypted0 = (Sphinx.peel(privKeys[0], associatedData, onion, OnionRoutingPacket.PaymentPacketLength) as Either.Right).value
        val decrypted1 = (Sphinx.peel(privKeys[1], associatedData, decrypted0.nextPacket, OnionRoutingPacket.PaymentPacketLength) as Either.Right).value
        val decrypted2 = (Sphinx.peel(privKeys[2], associatedData, decrypted1.nextPacket, OnionRoutingPacket.PaymentPacketLength) as Either.Right).value
        val decrypted3 = (Sphinx.peel(privKeys[3], associatedData, decrypted2.nextPacket, OnionRoutingPacket.PaymentPacketLength) as Either.Right).value
        val decrypted4 = (Sphinx.peel(privKeys[4], associatedData, decrypted3.nextPacket, OnionRoutingPacket.PaymentPacketLength) as Either.Right).value
        assertEquals(listOf(decrypted0.payload, decrypted1.payload, decrypted2.payload, decrypted3.payload, decrypted4.payload), variableSizePayloadsFull)
        assertEquals(listOf(decrypted0.sharedSecret, decrypted1.sharedSecret, decrypted2.sharedSecret, decrypted3.sharedSecret, decrypted4.sharedSecret), packetAndSecrets.sharedSecrets.perHopSecrets.map { it.first })

        val packets = listOf(decrypted0.nextPacket, decrypted1.nextPacket, decrypted2.nextPacket, decrypted3.nextPacket, decrypted4.nextPacket)
        assertEquals(packets[0].hmac, ByteVector32("859cd694cf604442547246f4fae144f255e71e30cb366b9775f488cac713f0db"))
        assertEquals(packets[1].hmac, ByteVector32("259982a8af80bd3b8018443997fa5f74c48b488fff62e531be54b887d53fe0ac"))
        assertEquals(packets[2].hmac, ByteVector32("58110c95368305b73ae15d22b884fda0482c60993d3ba4e506e37ff5021efb13"))
        assertEquals(packets[3].hmac, ByteVector32("f45e7099e32b8973f54cbfd1f6c48e7e0b90718ad7b00a88e1e98cebeb6d3916"))
        // this means that node #4 is the last node
        assertEquals(packets[4].hmac, ByteVector32("0000000000000000000000000000000000000000000000000000000000000000"))
    }

    @Test
    fun `create packet with single variable-size payload filling the onion`() {
        val packetAndSecrets = Sphinx.create(sessionKey, publicKeys.take(1), variableSizeOneHopPayload.map { it.toByteArray() }, associatedData, OnionRoutingPacket.PaymentPacketLength)
        val onion = packetAndSecrets.packet
        assertEquals(
            Hex.encode(OnionRoutingPacketSerializer(OnionRoutingPacket.PaymentPacketLength).write(onion)),
            "0002eec7245d6b7d2ccb30380bfbe2a3648cd7a942653f5aa340edcea1f28368661918f5b235c2a76fc232b5e46d421e9615471ab9e0bc887beff8c95fdb878f7b3a7141453e5f8d22b6351810ae541ce499a09b4a9d9f80d1845c8960c85fc6d1a87bd24b2cc49922898e9353fa268086c00ae8b7f718405b72ad380cdbb38c85e02a00427eb4bdbda8fcd42b44708a9efde49cf753b75ebb389bf84d0bfbf58590e510e034572a01e409c30939e2e4a090ecc89c371820af54e06e4ad5495d4e58718385cca5414552e078fedf284fdc2cc5c070cba21a6a8d4b77525ddbc9a9fca9b2f29aac5783ee8badd709f81c73ff60556cf2ee623af073b5a84799acc1ca46b764f74b97068c7826cc0579794a540d7a55e49eac26a6930340132e946a983240b0cd1b732e305c1042f580c4b26f140fc1cab3ee6f620958e0979f85eddf586c410ce42e93a4d7c803ead45fc47cf4396d284632314d789e73cf3f534126c63fe244069d9e8a7c4f98e7e530fc588e648ef4e641364981b5377542d5e7a4aaab6d35f6df7d3a9d7ca715213599ee02c4dbea4dc78860febe1d29259c64b59b3333ffdaebbaff4e7b31c27a3791f6bf848a58df7c69bb2b1852d2ad357b9919ffdae570b27dc709fba087273d3a4de9e6a6be66db647fb6a8d1a503b3f481befb96745abf5cc4a6bba0f780d5c7759b9e303a2a6b17eb05b6e660f4c474959db183e1cae060e1639227ee0bca03978a238dc4352ed764da7d4f3ed5337f6d0376dff72615beeeeaaeef79ab93e4bcbf18cd8424eb2b6ad7f33d2b4ffd5ea08372e6ed1d984152df17e04c6f73540988d7dd979e020424a163c271151a255966be7edef42167b8facca633649739bab97572b485658cde409e5d4a0f653f1a5911141634e3d2b6079b19347df66f9820755fd517092dae62fb278b0bafcc7ad682f7921b3a455e0c6369988779e26f0458b31bffd7e4e5bfb31944e80f100b2553c3b616e75be18328dc430f6618d55cd7d0962bb916d26ed4b117c46fa29e0a112c02c36020b34a96762db628fa3490828ec2079962ad816ef20ea0bca78fb2b7f7aedd4c47e375e64294d151ff03083730336dea64934003a27730cc1c7dec5049ddba8188123dd191aa71390d43a49fb792a3da7082efa6cced73f00eccea18145fbc84925349f7b552314ab8ed4c491e392aed3b1f03eb79474c294b42e2eba1528da26450aa592cba7ea22e965c54dff0fd6fdfd6b52b9a0f5f762e27fb0e6c3cd326a1ca1c5973de9be881439f702830affeb0c034c18ac8d5c2f135c964bf69de50d6e99bde88e90321ba843d9753c8f83666105d25fafb1a11ea22d62ef6f1fc34ca4e60c35d69773a104d9a44728c08c20b6314327301a2c400a71e1424c12628cf9f4a67990ade8a2203b0edb96c6082d4673b7309cd52c4b32b02951db2f66c6c72bd6c7eac2b50b83830c75cdfc3d6e9c2b592c45ed5fa5f6ec0da85710b7e1562aea363e28665835791dc574d9a70b2e5e2b9973ab590d45b94d244fc4256926c5a55b01cd0aca21fe5f9c907691fb026d0c56788b03ca3f08db0abb9f901098dde2ec4003568bc3ca27475ff86a7cb0aabd9e5136c5de064d16774584b252024109bb02004dba1fabf9e8277de097a0ab0dc8f6e26fcd4a28fb9d27cd4a2f6b13e276ed259a39e1c7e60f3c32c5cc4c4f96bd981edcb5e2c76a517cdc285aa2ca571d1e3d463ecd7614ae227df17af7445305bd7c661cf7dba658b0adcf36b0084b74a5fa408e272f703770ac5351334709112c5d4e4fe987e0c27b670412696f52b33245c229775da550729938268ee4e7a282e4a60b25dbb28ea8877a5069f819e5d1d31d9140bbc627ff3df267d22e5f0e151db066577845d71b7cd4484089f3f59194963c8f02bd7a637"
        )
        val decrypted = (Sphinx.peel(privKeys[0], associatedData, onion, OnionRoutingPacket.PaymentPacketLength) as Either.Right).value
        assertEquals(decrypted.payload, variableSizeOneHopPayload.first())
        assertEquals(decrypted.nextPacket.hmac, ByteVector32("0000000000000000000000000000000000000000000000000000000000000000"))
    }

    @Test
    fun `create trampoline packet`() {
        val packetAndSecrets = Sphinx.create(sessionKey, publicKeys, trampolinePayloads.map { it.toByteArray() }, associatedData, OnionRoutingPacket.TrampolinePacketLength)
        val onion = packetAndSecrets.packet
        assertEquals(
            Hex.encode(OnionRoutingPacketSerializer(OnionRoutingPacket.TrampolinePacketLength).write(onion)),
            "0002eec7245d6b7d2ccb30380bfbe2a3648cd7a942653f5aa340edcea1f283686619cff34152f3a36e52ca94e74927203a560392b9cc7ce3c45809c6be52166c24a595716880f95f178bf5b30ca5f01f7d8f9e2d26348fa73a0cf0e01efaeb4a6ff69f0e8ca2cb7f180d97b5becc99e303f3706509aa43ba7c8a88cba175fccf9a8f5016ef06d3b935dbb15196d7ce16dc1a7157845566901d7b2197e52cab4ce487019d8f59df4c61e85b3c678636701ea8bb55b8bdbd8724d8d39ee47087a648501329db7c5f7eafaa166578c720619561dd14b3277db557ec7dcdb793771aef0f2f667cfdbe148be176e089e1ae07192472031bcdaf47ab6334b98e5b6fcd26b3b47982842019517d7e2ea8c5391cf17d0fe30c80913ed887234ccb48808f7ef9425bcd815c3b9604b5119fbc40ae57b5921bb333f5dd9de0b2638d44bc5e1a863715f96589f3e77eecb277229b4b682322371c0a1dbfcd723a991993df8cc1f2696b84b055b40a1792a29f710295a18fbd351b0f3ff34cd13941131b8278ba79303c89117120eea69173fd2cf5e044e97bcd4060d1ab6da116bdb4136f4d37eb832845b64366dfcbe8729df1dda5708c1c89cd880b0f7c82318bcfe8a27f9e857b1dc453eb555c428c412a1056005319"
        )

        val decrypted0 = (Sphinx.peel(privKeys[0], associatedData, onion, OnionRoutingPacket.TrampolinePacketLength) as Either.Right).value
        val decrypted1 = (Sphinx.peel(privKeys[1], associatedData, decrypted0.nextPacket, OnionRoutingPacket.TrampolinePacketLength) as Either.Right).value
        val decrypted2 = (Sphinx.peel(privKeys[2], associatedData, decrypted1.nextPacket, OnionRoutingPacket.TrampolinePacketLength) as Either.Right).value
        val decrypted3 = (Sphinx.peel(privKeys[3], associatedData, decrypted2.nextPacket, OnionRoutingPacket.TrampolinePacketLength) as Either.Right).value
        val decrypted4 = (Sphinx.peel(privKeys[4], associatedData, decrypted3.nextPacket, OnionRoutingPacket.TrampolinePacketLength) as Either.Right).value
        assertEquals(listOf(decrypted0.payload, decrypted1.payload, decrypted2.payload, decrypted3.payload, decrypted4.payload), trampolinePayloads)
        assertEquals(listOf(decrypted0.sharedSecret, decrypted1.sharedSecret, decrypted2.sharedSecret, decrypted3.sharedSecret, decrypted4.sharedSecret), packetAndSecrets.sharedSecrets.perHopSecrets.map { it.first })
    }

    @Test
    fun `create packet with invalid payload`() {
        // In this test vector, the payload length (encoded as a bigsize in the first bytes) isn't equal to the actual
        // payload length.
        val invalidPayloads = listOf(
            Hex.decode("fd2a0101234567"),
            Hex.decode("000000000000000000000000000000000000000000000000000000000000000000")
        )
        assertFails { Sphinx.create(sessionKey, publicKeys.take(2), invalidPayloads, associatedData, OnionRoutingPacket.PaymentPacketLength) }
    }

    @Test
    fun `encode - decode failure onion`() {
        val testCases = listOf(
            Pair(
                InvalidOnionKey(ByteVector32("2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a")),
                "41a824e2d630111669fa3e52b600a518f369691909b4e89205dc624ee17ed2c10022c0062a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a00de000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            ),
            Pair(
                IncorrectOrUnknownPaymentDetails(42.msat, 1105),
                "5eb766da1b2f45b4182e064dacd8da9eca2c9a33f0dce363ff308e9bdb3ee4e3000e400f000000000000002a0000045100f20000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            )
        )
        testCases.forEach {
            val decoded = FailurePacket.decode(Hex.decode(it.second), ByteVector32.Zeroes)
            assertEquals(it.first, decoded.get())
            val encoded = FailurePacket.encode(it.first, ByteVector32.Zeroes)
            assertEquals(it.second, Hex.encode(encoded))
        }
    }

    @Test
    fun `decode backwards-compatible IncorrectOrUnknownPaymentDetails`() {
        val testCases = listOf(
            // Without any data.
            Pair(
                IncorrectOrUnknownPaymentDetails(0.msat, 0),
                "0d83b55dd5a6086e4033c3659125ed1ff436964ce0e67ed5a03bddb16a9a10410002400f00fe0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            ),
            // With an amount but no height.
            Pair(
                IncorrectOrUnknownPaymentDetails(42.msat, 0),
                "ba6e122b2941619e2106e8437bf525356ffc8439ac3b2245f68546e298a08cc6000a400f000000000000002a00f6000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            ),
            // With amount and height.
            Pair(
                IncorrectOrUnknownPaymentDetails(42.msat, 1105),
                "5eb766da1b2f45b4182e064dacd8da9eca2c9a33f0dce363ff308e9bdb3ee4e3000e400f000000000000002a0000045100f20000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
            )
        )
        testCases.forEach {
            val decoded = FailurePacket.decode(Hex.decode(it.second), ByteVector32.Zeroes)
            assertEquals(it.first, decoded.get())
        }
    }

    @Test
    fun `decode invalid failure onion packet`() {
        val testCases = listOf(
            // Invalid failure message.
            "fd2f3eb163dacfa7fe2ec1a7dc73c33438e7ca97c561475cf0dc96dc15a75039 0020 c005 2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a 00e0 0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
            // Invalid mac.
            "0000000000000000000000000000000000000000000000000000000000000000 0022 c006 2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a2a 00de 000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
            // Padding too small.
            "7bfb2aa46218240684f623322ae48af431d06986c82e210bb0cee83c7ddb2ba8 0002 4001 0002 0000",
            // Padding too big.
            "6f9e2c0e44b3692dac37523c6ff054cc9b26ecab1a78ed6906a46848bffc2bd5 0002 4001 00ff 000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"
        )
        testCases.forEach {
            assertTrue(FailurePacket.decode(Hex.decode(it), ByteVector32.Zeroes).isFailure)
        }
    }

    @Test
    fun `decrypt failure onion`() {
        val expected = DecryptedFailurePacket(publicKeys.first(), InvalidOnionKey(ByteVector32.One))
        val sharedSecrets = listOf(
            ByteVector32("0101010101010101010101010101010101010101010101010101010101010101"),
            ByteVector32("0202020202020202020202020202020202020202020202020202020202020202"),
            ByteVector32("0303030303030303030303030303030303030303030303030303030303030303"),
        )

        val packet1 = FailurePacket.create(sharedSecrets.first(), expected.failureMessage)
        assertEquals(292, packet1.size)
        val decrypted1 = FailurePacket.decrypt(packet1, SharedSecrets(listOf(Pair(sharedSecrets[0], publicKeys[0]))))
        assertEquals(expected, decrypted1.get())

        val packet2 = FailurePacket.wrap(packet1, sharedSecrets[1])
        assertEquals(292, packet2.size)
        val decrypted2 = FailurePacket.decrypt(packet2, SharedSecrets(listOf(1, 0).map { i -> Pair(sharedSecrets[i], publicKeys[i]) }))
        assertEquals(expected, decrypted2.get())

        val packet3 = FailurePacket.wrap(packet2, sharedSecrets[2])
        assertEquals(292, packet3.size)
        val decrypted3 = FailurePacket.decrypt(packet3, SharedSecrets(listOf(2, 1, 0).map { i -> Pair(sharedSecrets[i], publicKeys[i]) }))
        assertEquals(expected, decrypted3.get())
    }

    @Test
    fun `decrypt invalid failure onion`() {
        val sharedSecrets = listOf(
            ByteVector32("0101010101010101010101010101010101010101010101010101010101010101"),
            ByteVector32("0202020202020202020202020202020202020202020202020202020202020202"),
            ByteVector32("0303030303030303030303030303030303030303030303030303030303030303"),
        )
        val packet = FailurePacket.wrap(
            FailurePacket.wrap(
                FailurePacket.create(sharedSecrets.first(), InvalidOnionKey(ByteVector32.One)),
                sharedSecrets[1]
            ),
            sharedSecrets[2]
        )
        assertTrue(FailurePacket.decrypt(packet, SharedSecrets(listOf(0, 2, 1).map { i -> Pair(sharedSecrets[i], publicKeys[i]) })).isFailure)
    }

    @Test
    fun `last node replies with a failure message (reference test vector)`() {
        val testCases = listOf(
            Pair(OnionRoutingPacket.PaymentPacketLength, referenceFixedSizePayloads),
            Pair(OnionRoutingPacket.PaymentPacketLength, referenceVariableSizePayloads),
            Pair(OnionRoutingPacket.PaymentPacketLength, variableSizePayloadsFull),
            Pair(OnionRoutingPacket.TrampolinePacketLength, trampolinePayloads),
        )
        testCases.forEach {
            // route: origin -> node #0 -> node #1 -> node #2 -> node #3 -> node #4
            // origin builds the onion packet
            val packetLength = it.first
            val packetAndSecrets = Sphinx.create(sessionKey, publicKeys, it.second.map { p -> p.toByteArray() }, associatedData, packetLength)

            // each node parses and forwards the packet
            // node #0
            val decrypted0 = Sphinx.peel(privKeys[0], associatedData, packetAndSecrets.packet, packetLength).right!!
            // node #1
            val decrypted1 = Sphinx.peel(privKeys[1], associatedData, decrypted0.nextPacket, packetLength).right!!
            // node #2
            val decrypted2 = Sphinx.peel(privKeys[2], associatedData, decrypted1.nextPacket, packetLength).right!!
            // node #3
            val decrypted3 = Sphinx.peel(privKeys[3], associatedData, decrypted2.nextPacket, packetLength).right!!
            // node #4
            val decrypted4 = Sphinx.peel(privKeys[4], associatedData, decrypted3.nextPacket, packetLength).right!!
            assertTrue(decrypted4.isLastPacket)

            // node #4 want to reply with an error message
            val error4 = FailurePacket.create(decrypted4.sharedSecret, TemporaryNodeFailure)
            assertEquals(
                Hex.encode(error4),
                "a5e6bd0c74cb347f10cce367f949098f2457d14c046fd8a22cb96efb30b0fdcda8cb9168b50f2fd45edd73c1b0c8b33002df376801ff58aaa94000bf8a86f92620f343baef38a580102395ae3abf9128d1047a0736ff9b83d456740ebbb4aeb3aa9737f18fb4afb4aa074fb26c4d702f42968888550a3bded8c05247e045b866baef0499f079fdaeef6538f31d44deafffdfd3afa2fb4ca9082b8f1c465371a9894dd8c243fb4847e004f5256b3e90e2edde4c9fb3082ddfe4d1e734cacd96ef0706bf63c9984e22dc98851bcccd1c3494351feb458c9c6af41c0044bea3c47552b1d992ae542b17a2d0bba1a096c78d169034ecb55b6e3a7263c26017f033031228833c1daefc0dedb8cf7c3e37c9c37ebfe42f3225c326e8bcfd338804c145b16e34e4"
            )
            // error sent back to 3, 2, 1 and 0
            val error3 = FailurePacket.wrap(error4, decrypted3.sharedSecret)
            assertEquals(
                Hex.encode(error3),
                "c49a1ce81680f78f5f2000cda36268de34a3f0a0662f55b4e837c83a8773c22aa081bab1616a0011585323930fa5b9fae0c85770a2279ff59ec427ad1bbff9001c0cd1497004bd2a0f68b50704cf6d6a4bf3c8b6a0833399a24b3456961ba00736785112594f65b6b2d44d9f5ea4e49b5e1ec2af978cbe31c67114440ac51a62081df0ed46d4a3df295da0b0fe25c0115019f03f15ec86fabb4c852f83449e812f141a9395b3f70b766ebbd4ec2fae2b6955bd8f32684c15abfe8fd3a6261e52650e8807a92158d9f1463261a925e4bfba44bd20b166d532f0017185c3a6ac7957adefe45559e3072c8dc35abeba835a8cb01a71a15c736911126f27d46a36168ca5ef7dccd4e2886212602b181463e0dd30185c96348f9743a02aca8ec27c0b90dca270"
            )
            val error2 = FailurePacket.wrap(error3, decrypted2.sharedSecret)
            assertEquals(
                Hex.encode(error2),
                "a5d3e8634cfe78b2307d87c6d90be6fe7855b4f2cc9b1dfb19e92e4b79103f61ff9ac25f412ddfb7466e74f81b3e545563cdd8f5524dae873de61d7bdfccd496af2584930d2b566b4f8d3881f8c043df92224f38cf094cfc09d92655989531524593ec6d6caec1863bdfaa79229b5020acc034cd6deeea1021c50586947b9b8e6faa83b81fbfa6133c0af5d6b07c017f7158fa94f0d206baf12dda6b68f785b773b360fd0497e16cc402d779c8d48d0fa6315536ef0660f3f4e1865f5b38ea49c7da4fd959de4e83ff3ab686f059a45c65ba2af4a6a79166aa0f496bf04d06987b6d2ea205bdb0d347718b9aeff5b61dfff344993a275b79717cd815b6ad4c0beb568c4ac9c36ff1c315ec1119a1993c4b61e6eaa0375e0aaf738ac691abd3263bf937e3"
            )
            val error1 = FailurePacket.wrap(error2, decrypted1.sharedSecret)
            assertEquals(
                Hex.encode(error1),
                "aac3200c4968f56b21f53e5e374e3a2383ad2b1b6501bbcc45abc31e59b26881b7dfadbb56ec8dae8857add94e6702fb4c3a4de22e2e669e1ed926b04447fc73034bb730f4932acd62727b75348a648a1128744657ca6a4e713b9b646c3ca66cac02cdab44dd3439890ef3aaf61708714f7375349b8da541b2548d452d84de7084bb95b3ac2345201d624d31f4d52078aa0fa05a88b4e20202bd2b86ac5b52919ea305a8949de95e935eed0319cf3cf19ebea61d76ba92532497fcdc9411d06bcd4275094d0a4a3c5d3a945e43305a5a9256e333e1f64dbca5fcd4e03a39b9012d197506e06f29339dfee3331995b21615337ae060233d39befea925cc262873e0530408e6990f1cbd233a150ef7b004ff6166c70c68d9f8c853c1abca640b8660db2921"
            )
            val error0 = FailurePacket.wrap(error1, decrypted0.sharedSecret)
            assertEquals(
                Hex.encode(error0),
                "9c5add3963fc7f6ed7f148623c84134b5647e1306419dbe2174e523fa9e2fbed3a06a19f899145610741c83ad40b7712aefaddec8c6baf7325d92ea4ca4d1df8bce517f7e54554608bf2bd8071a4f52a7a2f7ffbb1413edad81eeea5785aa9d990f2865dc23b4bc3c301a94eec4eabebca66be5cf638f693ec256aec514620cc28ee4a94bd9565bc4d4962b9d3641d4278fb319ed2b84de5b665f307a2db0f7fbb757366067d88c50f7e829138fde4f78d39b5b5802f1b92a8a820865af5cc79f9f30bc3f461c66af95d13e5e1f0381c184572a91dee1c849048a647a1158cf884064deddbf1b0b88dfe2f791428d0ba0f6fb2f04e14081f69165ae66d9297c118f0907705c9c4954a199bae0bb96fad763d690e7daa6cfda59ba7f2c8d11448b604d12d"
            )
            // origin parses error packet and can see that it comes from node #4
            val decrypted = FailurePacket.decrypt(error0, packetAndSecrets.sharedSecrets)
            assertEquals(DecryptedFailurePacket(publicKeys[4], TemporaryNodeFailure), decrypted.get())
        }
    }

    @Test
    fun `intermediate node replies with a failure message (reference test vector)`() {
        val testCases = listOf(
            Pair(OnionRoutingPacket.PaymentPacketLength, referenceFixedSizePayloads),
            Pair(OnionRoutingPacket.PaymentPacketLength, referenceVariableSizePayloads),
            Pair(OnionRoutingPacket.PaymentPacketLength, variableSizePayloadsFull),
            Pair(OnionRoutingPacket.TrampolinePacketLength, trampolinePayloads),
        )
        testCases.forEach {
            // route: origin -> node #0 -> node #1 -> node #2 -> node #3 -> node #4
            // origin builds the onion packet
            val packetLength = it.first
            val packetAndSecrets = Sphinx.create(sessionKey, publicKeys, it.second.map { p -> p.toByteArray() }, associatedData, packetLength)

            // each node parses and forwards the packet
            // node #0
            val decrypted0 = Sphinx.peel(privKeys[0], associatedData, packetAndSecrets.packet, packetLength).right!!
            // node #1
            val decrypted1 = Sphinx.peel(privKeys[1], associatedData, decrypted0.nextPacket, packetLength).right!!
            // node #2
            val decrypted2 = Sphinx.peel(privKeys[2], associatedData, decrypted1.nextPacket, packetLength).right!!

            // node #2 want to reply with an error message
            val error2 = FailurePacket.create(decrypted2.sharedSecret, InvalidRealm)
            // error sent back to 1 and 0
            val error1 = FailurePacket.wrap(error2, decrypted1.sharedSecret)
            val error0 = FailurePacket.wrap(error1, decrypted0.sharedSecret)

            // origin parses error packet and can see that it comes from node #2
            val decrypted = FailurePacket.decrypt(error0, packetAndSecrets.sharedSecrets)
            assertEquals(DecryptedFailurePacket(publicKeys[2], InvalidRealm), decrypted.get())
        }
    }
}