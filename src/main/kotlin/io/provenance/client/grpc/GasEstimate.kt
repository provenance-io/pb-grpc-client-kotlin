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
        const val DEFAULT_GAS_PRICE = 1905.00
    }
}

/**
 * Creates a [GasEstimate] when [GasEstimationMethod.COSMOS_SIMULATION] is used.
 *
 * @param estimate The estimated gas limit.
 * @param adjustment An adjustment to apply to the gas estimate.
 */
fun fromSimulation(estimate: Long, adjustment: Double): GasEstimate {
    val limit = ceil(estimate * adjustment).toLong()
    val feeAmount = ceil(limit * GasEstimate.DEFAULT_GAS_PRICE).toLong()
    return listOf(
        CoinOuterClass.Coin.newBuilder()
            .setAmount(feeAmount.toString())
            .setDenom("nhash")
            .build()
    ).let { fees -> GasEstimate(limit, fees) }
}

/**
 * [GasEstimator] is an alias to standardize how gas estimations are made.
 * @param tx The transaction to estimate for.
 * @param adj The gas adjustment being applied.
 * @return Gas estimates.
 */
typealias GasEstimator = (tx: TxOuterClass.Tx, adj: Double) -> GasEstimate

/**
 * Cosmos simulation gas estimation. Must be used when interacting with pbc 1.7 or lower.
 */
private val CosmosSimulationGasEstimator : PbGasEstimator = {
    { tx, adj ->
        val sim = cosmosService.simulate(
            ServiceOuterClass.SimulateRequest.newBuilder()
                .setTxBytes(tx.toByteString())
                .build()
        )
        fromSimulation(sim.gasInfo.gasUsed, adj)
    }
}

/**
 * Message fee endpoint gas estimation. Only compatible and should be used with pbc 1.8 or greater.
 */
private val MsgFeeCalculationGasEstimator : PbGasEstimator = {
    { tx, adj ->
        val estimate = msgFeeClient.calculateTxFees(
            CalculateTxFeesRequest.newBuilder()
                .setTxBytes(tx.toByteString())
                .setGasAdjustment(adj.toFloat())
                .build()
        )
        GasEstimate(estimate.estimatedGas, estimate.totalFeesList)
    }
}

private fun CustomGasPriceGasEstimator(delegate: PbGasEstimator, gasPrice: CoinOuterClass.Coin): PbGasEstimator = {
    { tx, adj ->
        val estimate = delegate(this)(tx, adj)
        val newFees = estimate.feesCalculated.map { c ->
            c.toBuilder().setAmount((c.amount.toInt() * gasPrice.amount.toInt()).toString()).build()
        }
        estimate.copy(feesCalculated = newFees)
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
    val MSG_FEE_CALCULATION: PbGasEstimator = MsgFeeCalculationGasEstimator

    /**
     *
     */
    fun CUSTOM_GAS_PRICE_GAS_ESTIMATE(delegate: PbGasEstimator, getGasPrice: () -> CoinOuterClass.Coin): PbGasEstimator =
        CustomGasPriceGasEstimator(delegate, getGasPrice())
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
