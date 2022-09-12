package io.provenance.client.gas.prices

import com.google.gson.Gson
import cosmos.base.v1beta1.CoinOuterClass
import io.provenance.client.common.gas.GasPrice
import io.provenance.client.common.gas.prices.UrlGasPrices
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import java.io.InputStream
import java.io.InputStreamReader

fun urlGasPrices(
    uri: String,
    fetch: (uri: String) -> InputStream = defaultHttpFetch,
    marshal: (body: InputStream) -> CoinOuterClass.Coin = defaultGasPriceMarshal,
) = UrlGasPrices(uri, fetch, marshal)

private val httpClient = HttpClientBuilder.create().build()
private val gson = Gson().newBuilder().create()

private val defaultHttpFetch: (uri: String) -> InputStream = {
    val result = httpClient.execute(HttpGet(it))
    require(result.statusLine.statusCode in 200..299) {
        "failed to get uri:$it status:${result.statusLine.statusCode}: ${result.statusLine.reasonPhrase}"
    }
    result.entity.content
}

private inline fun <reified T : Any> defaultMarshal(it: InputStream): T = it.use {
    InputStreamReader(it).use { isr ->
        gson.fromJson(isr, T::class.java)
    }
}

private val defaultGasPriceMarshal: (InputStream) -> CoinOuterClass.Coin = {
    defaultMarshal<GasPrice>(it).toCoin()
}
