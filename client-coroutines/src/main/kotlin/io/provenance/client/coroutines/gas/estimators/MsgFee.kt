package io.provenance.client.coroutines.gas.estimators

import io.provenance.client.coroutines.PbGasEstimator
import io.provenance.client.grpc.GasEstimate
import io.provenance.msgfees.v1.CalculateTxFeesRequest

/**
 * Message fee endpoint gas estimation. Only compatible and should be used with pbc 1.8 or greater.
 */
// @TestnetFeaturePreview
internal val MsgFeeCalculationGasEstimator: PbGasEstimator = {
    { tx, adjustment ->
        val estimate = msgFeeClient.calculateTxFees(
            CalculateTxFeesRequest.newBuilder()
                .setTxBytes(tx.toByteString())
                .setGasAdjustment(adjustment.toFloat())
                .build()
        )
        GasEstimate(estimate.estimatedGas, estimate.totalFeesList, estimate.additionalFeesList)
    }
}
