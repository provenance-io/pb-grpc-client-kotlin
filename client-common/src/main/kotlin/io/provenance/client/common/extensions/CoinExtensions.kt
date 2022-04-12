package io.provenance.client.common.extensions

import cosmos.base.v1beta1.CoinOuterClass
import java.math.BigDecimal

fun Number.toCoin(denom: String): CoinOuterClass.Coin {
    return CoinOuterClass.Coin.newBuilder()
        .setAmount(BigDecimal(toString()).toPlainString())
        .setDenom(denom)
        .build()
}

fun String.toCoin(): CoinOuterClass.Coin {
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

internal fun List<CoinOuterClass.Coin>.discreteSum(): List<CoinOuterClass.Coin> =
    groupBy { it.denom }
        .toSortedMap()
        .map { it.key to it.value.sumOf { it.amount.toBigInteger() } }
        .map { "${it.second}${it.first}".toCoin() }
