package io.provenance.client.coroutines

import cosmos.auth.v1beta1.QueryOuterClass
import kotlinx.coroutines.test.runTest
import org.junit.Ignore
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertTrue

@Ignore
class PbClientTest {
    val pbClient = PbCoroutinesClient(
        chainId = "chain-local",
        channelUri = URI("http://localhost:9090"),
        gasEstimationMethod = GasEstimationMethod.MSG_FEE_CALCULATION // GasEstimationMethod.COSMOS_SIMULATION used only if pbc version is 1.7 or lower
    )

    @Test
    fun testClientQuery() = runTest {
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
