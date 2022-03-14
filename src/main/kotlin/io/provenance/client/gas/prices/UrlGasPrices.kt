package io.provenance.client.gas.prices

import com.google.gson.Gson
import cosmos.base.v1beta1.CoinOuterClass
import io.provenance.client.internal.extensions.toCoin
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import java.io.InputStreamReader

/**
 * When provided with a url, fetches an object of shape '{"gasPrice":nnn,"gasPriceDenom":"denom"}'
 */
open class UrlGasPrices(private val uri: String) : GasPrices {
    private val client = HttpClientBuilder.create().build()
    private val gson = Gson().newBuilder().create()

    private data class GasPrice(val gasPrice: Long, val gasPriceDenom: String) {
        fun toCoin(): CoinOuterClass.Coin = "$gasPrice$gasPriceDenom".toCoin()
    }

    override fun invoke(): CoinOuterClass.Coin {
        val result = client.execute(HttpGet(uri))
        require(result.statusLine.statusCode in 200..299) {
            "failed to get uri:$uri status:${result.statusLine.statusCode}: ${result.statusLine.reasonPhrase}"
        }

        return result.entity.content.use { i ->
            InputStreamReader(i).use {
                gson.fromJson(it, GasPrice::class.java).toCoin()
            }
        }
    }
}
