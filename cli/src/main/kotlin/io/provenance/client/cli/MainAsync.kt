package io.provenance.client.cli

import cosmos.bank.v1beta1.Tx
import io.provenance.client.common.extensions.toCoin
import io.provenance.client.common.gas.GasEstimate
import io.provenance.client.coroutines.GasEstimationMethod
import io.provenance.client.coroutines.PbCoroutinesClient
import io.provenance.client.coroutines.floatingGasPrices
import io.provenance.client.coroutines.gas.prices.cached
import io.provenance.client.coroutines.gas.prices.urlGasPrices
import io.provenance.client.coroutines.gas.prices.withFallbackPrice
import io.provenance.client.grpc.BaseReqSigner
import io.provenance.client.wallet.fromMnemonic
import io.provenance.name.v1.QueryResolveRequest
import kotlinx.coroutines.runBlocking
import java.net.URI

fun main() = runBlocking {
    // GasEstimationMethod.COSMOS_SIMULATION used only if pbc version is 1.7 or lower
    val priceGetter = urlGasPrices("https://test.figure.tech/figure-gas-price")
        .cached()
        .withFallbackPrice(GasEstimate.DEFAULT_GAS_PRICE.toCoin("nhash"))

    val estimator = floatingGasPrices(GasEstimationMethod.MSG_FEE_CALCULATION, priceGetter)
    val pbClient = PbCoroutinesClient(
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
suspend fun PbCoroutinesClient.testClientTxn() {
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