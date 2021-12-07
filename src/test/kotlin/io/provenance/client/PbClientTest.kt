package io.provenance.client

import cosmos.auth.v1beta1.QueryOuterClass
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertTrue

class PbClientTest {

    @Test
    fun testClient() {
        val pbClient = PbClient(
            chainId = "chain-local",
            channelUri = URI("http://localhost:9090"),
        )

        pbClient.authClient.accounts(
            QueryOuterClass.QueryAccountsRequest.getDefaultInstance()
        ).also { response ->
            println(response)
            assertTrue(
                response.accountsCount > 0,
                "Found zero accounts on blockchain or could not properly connect to localhost chain."
            )
        }

    }
}