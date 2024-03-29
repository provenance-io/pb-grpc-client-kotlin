package io.provenance.client.cli

import com.google.protobuf.Any
import com.google.protobuf.Message
import cosmos.bank.v1beta1.Tx
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.client.common.extensions.toCoin
import io.provenance.client.common.gas.GasEstimate
import io.provenance.client.common.gas.prices.cached
import io.provenance.client.common.gas.prices.constGasPrice
import io.provenance.client.common.gas.prices.withFallback
import io.provenance.client.gas.prices.urlGasPrices
import io.provenance.client.grpc.BaseReqSigner
import io.provenance.client.grpc.GasEstimationMethod
import io.provenance.client.grpc.PbClient
import io.provenance.client.grpc.floatingGasPrices
import io.provenance.client.wallet.NetworkType
import io.provenance.client.wallet.fromMnemonic
import io.provenance.name.v1.QueryResolveRequest
import java.net.URI

val networkType = NetworkType("tp", "m/44'/1'/0'/0/0")

fun main() {
    // GasEstimationMethod.COSMOS_SIMULATION used only if pbc version is 1.7 or lower
    val priceGetter = urlGasPrices("https://test.figure.tech/figure-gas-price")
        .cached()
        .withFallback(constGasPrice(GasEstimate.DEFAULT_GAS_PRICE.toCoin("nhash")))

    val estimator = floatingGasPrices(GasEstimationMethod.MSG_FEE_CALCULATION, priceGetter)
    val pbClient = PbClient(
        chainId = "localnet-main",
        channelUri = URI("grpc://0.0.0.0:9090"),
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
            .setFromAddress("tp1x4ay2yxn4e6mnnxd76y0ckdglw45pvnhc08p36")
            .setToAddress("tp1x4ay2yxn4e6mnnxd76y0ckdglw45pvnhc08p36")
            .addAmount("100000nhash".toCoin())
            .build()
    )
    val baseReq = baseRequest(txn.toTxBody(), listOf(BaseReqSigner(walletSigner, 0)), 1.0)
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
