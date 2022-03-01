package io.provenance.client.grpc

import com.google.gson.Gson
import cosmos.base.v1beta1.CoinOuterClass
import cosmos.tx.v1beta1.ServiceOuterClass
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.msgfees.v1.CalculateTxFeesRequest
import java.io.InputStreamReader
import kotlin.math.ceil
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder

/**
 * The gas estimate implementation
 *
 * @param limit The estimated gas limit.
 * @param feesCalculated A list of [CoinOuterClass.Coin].
 */
data class GasEstimate(val limit: Long, val feesCalculated: List<CoinOuterClass.Coin> = emptyList()) {
    companion object {
        const val DEFAULT_FEE_ADJUSTMENT = 1.25

        // TODO - Remove once mainnet.version > 1.8
        const val DEFAULT_GAS_PRICE = 1905.00
    }
}

/**
 * Creates a [GasEstimate] when [GasEstimationMethod.COSMOS_SIMULATION] is used.
 *
 * @param estimate The estimated gas limit.
 * @param adjustment An adjustment to apply to the gas estimate.
 *
 * TODO - Remove once mainnet.version > 1.8
 */
fun fromSimulation(estimate: Long, adjustment: Double, gasPrice: Double): GasEstimate {
    val limit = ceil(estimate * adjustment).toLong()
    val feeAmount = ceil(limit * gasPrice).toLong()
    return GasEstimate(limit, listOf(feeAmount.toCoin("nhash")))
}

/**
 * [GasEstimator] is an alias to standardize how gas estimations are made.
 * @param tx The transaction to estimate for.
 * @param adj The gas adjustment being applied.
 * @return Gas estimates.
 */
typealias GasEstimator = (tx: TxOuterClass.Tx, adjustment: Double) -> GasEstimate

/**
 * Cosmos simulation gas estimation. Must be used when interacting with pbc 1.7 or lower.
 * TODO - Remove once mainnet.version > 1.8
 */
private val CosmosSimulationGasEstimator : PbGasEstimator = {
    { tx, adjustment ->
        val sim = cosmosService.simulate(
            ServiceOuterClass.SimulateRequest.newBuilder()
                .setTxBytes(tx.toByteString())
                .build()
        )
        fromSimulation(sim.gasInfo.gasUsed, adjustment, GasEstimate.DEFAULT_GAS_PRICE)
    }
}

/**
 * Message fee endpoint gas estimation. Only compatible and should be used with pbc 1.8 or greater.
 */
@TestnetOnly
private val MsgFeeCalculationGasEstimator : PbGasEstimator = {
    { tx, adjustment ->
        val estimate = msgFeeClient.calculateTxFees(
            CalculateTxFeesRequest.newBuilder()
                .setTxBytes(tx.toByteString())
                .setGasAdjustment(adjustment.toFloat())
                .build()
        )
        GasEstimate(estimate.estimatedGas, estimate.totalFeesList)
    }
}

@TestnetOnly
private fun FloatingGasPriceGasEstimator(delegate: PbGasEstimator, floatingGasPrice: CoinOuterClass.Coin): PbGasEstimator = {
    { tx, adjustment ->
        // Original estimate
        val estimate = delegate(this)(tx, adjustment)
        // Adjust up or down based on floating factor.
        val factor = floatingGasPrice.amount.toDouble() / nodeGasPrice.value
        // Updated values
        estimate.copy(feesCalculated = estimate.feesCalculated * factor)
    }
}

typealias PbGasEstimator = PbClient.() -> GasEstimator

/**
 * A set of flags used to specify how gas should be estimated
 *
 * - `COSMOS_SIMULATION` - A flag to specify cosmos simulation gas estimation. Must be used when interacting with pbc 1.7 or lower.
 * - `MSG_FEE_CALCULATION` - A flag to specify message fee endpoint gas estimation. Only compatible and should be used with pbc 1.8 or greater.
 */
object GasEstimationMethod {
    /**
     * A flag for cosmos simulation gas estimation. Must be used when interacting with pbc 1.7 or lower.
     */
    val COSMOS_SIMULATION: PbGasEstimator = CosmosSimulationGasEstimator

    /**
     * A flag for message fee endpoint gas estimation. Only compatible and should be used with pbc 1.8 or greater.
     */
    @TestnetOnly
    val MSG_FEE_CALCULATION: PbGasEstimator = MsgFeeCalculationGasEstimator

    /**
     * Add a floating adjustment based on current market rates to estimation.
     */
    @TestnetOnly
    fun floatingGasPrice(delegate: PbGasEstimator, getGasPrice: () -> CoinOuterClass.Coin): PbGasEstimator =
        FloatingGasPriceGasEstimator(delegate, getGasPrice())
}

/**
 * When provided with a url, fetches an object of shape '{"gasPrice":nnn,"gasPriceDenom":"denom"}'
 */
object UrlGasFetcher {
    private val client = HttpClientBuilder.create().build()
    private val gson = Gson().newBuilder().create()

    private data class GasPrice(val gasPrice: Int, val gasPriceDenom: String) {
        fun toCoin(): CoinOuterClass.Coin = CoinOuterClass.Coin.newBuilder()
            .setAmount(gasPrice.toString())
            .setDenom(gasPriceDenom)
            .build()
    }

    fun fetcher(uri: String): () -> CoinOuterClass.Coin = {
        val result = client.execute(HttpGet(uri))
        require(result.statusLine.statusCode in 200..299) {
            "failed to get uri:$uri status:${result.statusLine.statusCode}: ${result.statusLine.reasonPhrase}"
        }

        result.entity.content.use { i ->
            InputStreamReader(i).use {
                gson.fromJson(it, GasPrice::class.java).toCoin()
            }
        }
    }
}

private fun Long.toCoin(denom: String): CoinOuterClass.Coin = (toString() + denom).toCoin()

private fun String.toCoin(): CoinOuterClass.Coin {
    val split = indexOfFirst { it.isLetter() }
    require(split != 0) { "invalid amount for coin:$this" }
    require(split > 0) { "invalid denom for coin:$this" }

    return CoinOuterClass.Coin.newBuilder()
        .setAmount(substring(0, split))
        .setDenom(substring(split, length))
        .build()
}

private operator fun List<CoinOuterClass.Coin>.times(other: Double): List<CoinOuterClass.Coin> = map {
    CoinOuterClass.Coin
        .newBuilder()
        .mergeFrom(it)
        .setAmount((it.amount.toDouble() * other).toBigDecimal().toPlainString())
        .build()
}