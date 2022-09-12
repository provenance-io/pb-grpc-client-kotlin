package io.provenance.client.common.gas.prices

import cosmos.base.v1beta1.CoinOuterClass
import java.io.InputStream

/**
 * When provided with a url, fetches an object of shape '{"gasPrice":nnn,"gasPriceDenom":"denom"}'
 */
open class UrlGasPrices(
    uri: String,
    fetch: (uri: String) -> InputStream,
    marshal: (body: InputStream) -> CoinOuterClass.Coin,
) : GasPrices {
    private val gasPrices = gasPrices { fetch(uri).let(marshal) }
    override fun invoke(): CoinOuterClass.Coin = gasPrices()
}