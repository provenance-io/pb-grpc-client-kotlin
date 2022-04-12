package io.provenance.client.coroutines.gas.prices

import com.google.gson.Gson
import cosmos.base.v1beta1.CoinOuterClass
import io.provenance.client.common.gas.GasPrice
import kotlinx.coroutines.CoroutineScope
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import java.io.InputStream
import java.io.InputStreamReader

/**
 *
 */
fun CoroutineScope.urlGasPrices(
    uri: String,
    fetch: suspend (uri: String) -> InputStream = defaultHttpFetch,
    marshal: suspend (body: InputStream) -> CoinOuterClass.Coin = defaultCoinMarshal,
): GasPrices = gasPrices { marshal(fetch(uri)) }

/**
 *
 */
private val gson = Gson().newBuilder().create()

/**
 *
 */
private val httpClient = HttpClientBuilder.create().build()

/**
 *
 */
private val defaultHttpFetch: suspend (uri: String) -> InputStream = {
    val result = httpClient.execute(HttpGet(it))
    require(result.statusLine.statusCode in 200..299) {
        "failed to get uri:$it status:${result.statusLine.statusCode}: ${result.statusLine.reasonPhrase}"
    }
    result.entity.content
}

/**
 *
 */
private val defaultCoinMarshal: suspend (body: InputStream) -> CoinOuterClass.Coin = {
    it.use { i ->
        InputStreamReader(i).use { isr ->
            gson.fromJson(isr, GasPrice::class.java).toCoin()
        }
    }
}
