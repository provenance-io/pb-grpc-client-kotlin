package io.provenance.client.common.gas.prices

import cosmos.base.v1beta1.CoinOuterClass

/**
 *
 */
fun constGasPrice(gasPrice: CoinOuterClass.Coin) = gasPrices { gasPrice }