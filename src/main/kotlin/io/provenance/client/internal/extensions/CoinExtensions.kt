package io.provenance.client.internal.extensions

import cosmos.base.v1beta1.CoinOuterClass

internal fun Number.toCoin(denom: String): CoinOuterClass.Coin {
	return CoinOuterClass.Coin.newBuilder()
		.setAmount(toString())
		.setDenom(denom)
		.build()
}

internal fun String.toCoin(): CoinOuterClass.Coin {
	val split = indexOfFirst { it.isLetter() }
	require(split != 0) { "invalid amount for coin:$this" }
	require(split > 0) { "invalid denom for coin:$this" }

	return CoinOuterClass.Coin.newBuilder()
		.setAmount(substring(0, split))
		.setDenom(substring(split, length))
		.build()
}

internal operator fun List<CoinOuterClass.Coin>.times(other: Double): List<CoinOuterClass.Coin> = map { it * other }

internal operator fun CoinOuterClass.Coin.times(other: Double): CoinOuterClass.Coin {
	return CoinOuterClass.Coin
		.newBuilder()
		.mergeFrom(this)
		.setAmount((amount.toDouble() * other).toBigDecimal().toPlainString())
		.build()
}