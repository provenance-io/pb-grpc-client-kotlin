package io.provenance.client.gas.prices

import cosmos.base.v1beta1.CoinOuterClass

class ConstantGasPrice(private val gasPrice: CoinOuterClass.Coin) : GasPrices {
    override fun invoke(): CoinOuterClass.Coin = gasPrice
}
