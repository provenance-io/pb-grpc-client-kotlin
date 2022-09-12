package io.provenance.client.gas.estimators

import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.client.common.gas.GasEstimate
import io.provenance.client.grpc.GasEstimator
import io.provenance.msgfees.v1.CalculateTxFeesRequest

/**
 * Message fee endpoint gas estimation. Only compatible and should be used with pbc 1.8 or greater.
 */
internal val MsgFeeCalculationGasEstimator: GasEstimator =
    { tx: TxOuterClass.Tx, adjustment: Double ->
        val estimate = msgFeeClient.calculateTxFees(
            CalculateTxFeesRequest.newBuilder()
                .setTxBytes(tx.toByteString())
                .setGasAdjustment(adjustment.toFloat())
                .build()
        )
        GasEstimate(estimate.estimatedGas, estimate.totalFeesList, estimate.additionalFeesList)
    }
