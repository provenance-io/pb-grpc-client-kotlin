package io.provenance.client.gas.estimators

import io.provenance.client.grpc.GasEstimate
import io.provenance.client.grpc.PbGasEstimator
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
