package io.provenance.client

import cosmos.auth.v1beta1.QueryOuterClass
import io.provenance.client.coroutines.PbCoroutinesClient
import kotlinx.coroutines.test.runTest
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertTrue

class PbClientTest {
    val pbClient = PbCoroutinesClient(
        chainId = "localnet-main",
        channelUri = URI("http://localhost:9090"),
        gasEstimationMethod = io.provenance.client.coroutines.GasEstimationMethod.MSG_FEE_CALCULATION // GasEstimationMethod.COSMOS_SIMULATION used only if pbc version is 1.7 or lower
    )

    @Test
    fun testClientQuery() {
        runTest {
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
}
