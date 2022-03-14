package io.provenance.client.internal.extensions

import cosmos.base.v1beta1.CoinOuterClass
import java.math.BigDecimal

internal fun Number.toCoin(denom: String): CoinOuterClass.Coin {
    return CoinOuterClass.Coin.newBuilder()
        .setAmount(BigDecimal(toString()).toPlainString())
        .setDenom(denom)
        .build()
}

internal fun String.toCoin(): CoinOuterClass.Coin {
    val split = indexOfFirst { it.isLetter() }
    require(split != 0) { "invalid amount for coin:$this" }
    require(split > 0) { "invalid denom for coin:$this" }

    return CoinOuterClass.Coin.newBuilder()
        .setAmount(BigDecimal(substring(0, split)).toPlainString())
        .setDenom(substring(split, length))
        .build()
}

internal operator fun List<CoinOuterClass.Coin>.times(other: BigDecimal): List<CoinOuterClass.Coin> = map { it * other }

internal operator fun CoinOuterClass.Coin.times(other: BigDecimal): CoinOuterClass.Coin {
    return CoinOuterClass.Coin
        .newBuilder()
        .mergeFrom(this)
        .setAmount(amount.toBigDecimal().times(other).toPlainString())
        .build()
}
