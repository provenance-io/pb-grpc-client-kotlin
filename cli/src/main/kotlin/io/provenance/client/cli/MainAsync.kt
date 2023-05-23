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
import kotlinx.coroutines.*
import java.net.URI
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun main() = runBlocking {
    // GasEstimationMethod.COSMOS_SIMULATION used only if pbc version is 1.7 or lower
    val priceGetter = urlGasPrices("https://test.figure.tech/figure-gas-price")
        .cached()
        .withFallbackPrice(GasEstimate.DEFAULT_GAS_PRICE.toCoin("nhash"))

    val estimator = floatingGasPrices(GasEstimationMethod.MSG_FEE_CALCULATION, priceGetter)
    val pbClient = PbCoroutinesClient(
        chainId = "localnet-main",
        channelUri = URI("grpc://0.0.0.0:9090"),
        gasEstimationMethod = estimator
    )

    pbClient.testClientTxn()
    pbClient.close()
    println("done!")
}

/**
 * Example of how to submit a transaction to chain.
 *
 * Example only... real values are needed to run this example
 */
suspend fun PbCoroutinesClient.testClientTxn(coroutineContext: CoroutineContext = EmptyCoroutineContext) {
    val mnemonic = "fly fly comfort" // todo use your own mnemonic
    val walletSigner = fromMnemonic(networkType, mnemonic)

    val addrs = withContext(coroutineContext) {
        listOf(
            nameClient.resolve(QueryResolveRequest.newBuilder().setName("pb").build()),
            nameClient.resolve(QueryResolveRequest.newBuilder().setName("provenance").build()),
        )
    }
    coroutineContext.cancel()

    val addrObject = addrs.map { addr ->
        println("addr: ${addr.address}")
        println("walletAddr:${walletSigner.address()}")
    }

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
