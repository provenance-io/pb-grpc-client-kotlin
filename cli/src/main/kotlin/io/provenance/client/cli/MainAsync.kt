package io.provenance.client.cli

import com.google.protobuf.Any
import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import cosmos.bank.v1beta1.Tx
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.client.async.GasEstimationMethodAsync
import io.provenance.client.async.PbAsyncClient
import io.provenance.client.async.cached
import io.provenance.client.async.floatingGasPricesAsync
import io.provenance.client.async.urlGasPricesAsync
import io.provenance.client.async.withFallbackPrice
import io.provenance.client.gas.prices.cached
import io.provenance.client.gas.prices.urlGasPrices
import io.provenance.client.gas.prices.withFallbackPrice
import io.provenance.client.grpc.BaseReqSigner
import io.provenance.client.grpc.GasEstimate
import io.provenance.client.grpc.GasEstimationMethod
import io.provenance.client.grpc.PbClient
import io.provenance.client.grpc.floatingGasPrices
import io.provenance.client.internal.extensions.toCoin
import io.provenance.client.wallet.NetworkType
import io.provenance.client.wallet.fromMnemonic
import io.provenance.name.v1.MsgBindNameRequest
import io.provenance.name.v1.NameRecord
import io.provenance.name.v1.QueryResolveRequest
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.util.Base64

val networkType = NetworkType("tp", "m/44'/1'/0'/0'/0'")

fun main1() {
    val coin = "99redballoons".toCoin()
    println(JsonFormat.printer().print(coin))


    val t = "CokBCoYBChwvY29zbW9zLmJhbmsudjFiZXRhMS5Nc2dTZW5kEmYKKXRwMXQ0ZnZuejgzbmdrdWFqOHljeWNhOHlwazVtNHN6Mm1qMDU1cjJhEil0cDE5Zm41bWxudHl4YWZ1Z2V0YzhseXp6cmU2bm55cXNxOTU0NDlndBoOCgVuaGFzaBIFMTAwMDASaApRCkYKHy9jb3Ntb3MuY3J5cHRvLnNlY3AyNTZrMS5QdWJLZXkSIwohAvJMlSkB8nDAAM3YLQDUzq7y4YCYokXGjq6yRWij/QPjEgQKAggBGP0fEhMKDQoFbmhhc2gSBDUwMDAQwJoMGkBEDtGfsCxnvrEC/sabZOdC+Ockkn9Leq6mEQNLObKqpzepYISvfDPtWHt7H1Q5sFG/HNAI0Me3hfUnRPqnHFEP"
    val parser: (ByteArray) -> Message = { TxOuterClass.TxBody.parseFrom(it) }

    val decoded = Base64.getDecoder().decode(t)
    println(decoded.toList().map { it.toInt().toChar() })

    val tx = parser(decoded)
    println(tx)
}

fun main() {
    // GasEstimationMethod.COSMOS_SIMULATION used only if pbc version is 1.7 or lower
    val priceGetter = urlGasPrices("https://test.figure.tech/figure-gas-price")
        .cached()
        .withFallbackPrice(GasEstimate.DEFAULT_GAS_PRICE.toCoin("nhash"))

    val estimator = floatingGasPrices(GasEstimationMethod.MSG_FEE_CALCULATION, priceGetter)
    val pbClient = PbClient(
        chainId = "pio-testnet-1",
        channelUri = URI("grpcs://grpc.test.provenance.io"),
        gasEstimationMethod = estimator
    )

    pbClient.testClientTxn()
}

/**
 * Example of how to submit a transaction to chain.
 *
 * Example only... real values are needed to run this example
 */
fun PbClient.testClientTxn() {
    val mnemonic = "fly fly comfort" // todo use your own mnemonic
    val walletSigner = fromMnemonic(networkType, mnemonic)
    val addr = nameClient.resolve(QueryResolveRequest.newBuilder().setName("pb").build())
    println("addr: ${addr.address}")
    println("walletAddr:${walletSigner.address()}")

    val txn = listOf(
        // MsgBindNameRequest.newBuilder().also {
        //     it.parent = NameRecord.newBuilder().also { it.address = addr.address; it.name = "pb" }.build()
        //     it.record = NameRecord.newBuilder().also { it.name = "tennar"; it.address = walletSigner.address() }.build()
        // }.build(),
        // MsgAddAttributeRequest.newBuilder().also {
        //     it.account = walletSigner.address()
        //     it.owner = walletSigner.address()
        //     it.name = "test.wallet.pb"
        //     it.attributeType = AttributeType.ATTRIBUTE_TYPE_BYTES
        //     it.value = ByteString.copyFrom("test".toByteArray())
        // }.build()
        Tx.MsgSend.newBuilder()
            .setToAddress("tp1s9c2asqtp4f6r5k4jsg97p5yekqc6rhqqv7vky")
            .addAmount("100hash".toCoin())
            .build()
    )
    val baseReq = baseRequest(txn.toTxBody(), listOf(BaseReqSigner(walletSigner, 31)), 1.0)
    val estimate = estimateTx(baseReq)
    println(estimate)
    println("--------------")
    println(baseReq)
    println("--------------")
    val response = broadcastTx(baseReq, estimate)
    println(response)
}

fun Message.toAny(): Any = Any.pack(this, "")

fun List<Message>.toTxBody() = TxOuterClass.TxBody
    .newBuilder()
    .addAllMessages(map { it.toAny() })
    .build()
