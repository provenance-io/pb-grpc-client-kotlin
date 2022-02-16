# GRPC client for the Provenance Blockchain

**Tip**: Refer to the [Cosmos Proto Docs](https://docs.cosmos.network/master/core/proto-docs.html) and
[Provenance Blockchain Proto Docs](https://github.com/provenance-io/provenance/blob/main/docs/proto-docs.md) for
client interface definitions.

## Installation

### Maven

```xml
<dependency>
  <groupId>io.provenance.client</groupId>
  <artifactId>pb-grpc-client-kotlin</artifactId>
  <version>${version}</version>
</dependency>
```

### Gradle

#### Groovy

In `build.gradle`:

```groovy
implementation 'io.provenance.client:pb-grpc-client-kotlin:${version}'
```

#### Kotlin

In `build.gradle.kts`:

```kotlin
implementation("io.provenance.client:pb-grpc-client-kotlin:${version}")
```

## Setup

Setup the client by supplying:
- the chain id (e.g. `pio-testnet-1`)
- the URI of the node to which you are connecting (default port is `9090`)
- the gas estimation method
  - for pbc version 1.8 or higher use `MSG_FEE_CALCULATION`
  - for pbc version 1.7 or lower use `COSMOS_SIMULATION`

Example: for a locally running testnet instance:
```kotlin
// pbc version 1.8 or higher
val pbClient = PbClient(
    "chain-local", 
    URI("http://localhost:9090"),
    GasEstimationMethod.MSG_FEE_CALCULATION
)

// pbc version 1.7 or lower
val pbClient = PbClient(
    "chain-local", 
    URI("http://localhost:9090"),
    GasEstimationMethod.COSMOS_SIMULATION
)
```

Optionally configure GRPC by also passing `ChannelOpts` or a `NettyChannelBuilder`.

Example: Set client idle timeout to 1 minute
```kotlin
val pbClient = PbClient(
    chainId = "chain-local",
    channelUri = URI("http://localhost:9090"),
    gasEstimationMethod = GasEstimationMethod.MSG_FEE_CALCULATION,
    opts = ChannelOpts(idleTimeout = (1L to TimeUnit.MINUTES))
)
```

## Query Usage

`PBClient` contains individual clients for each Cosmos and Provenance Blockchain SDK query service. Each module contains a `query.proto`, which 
defines the query interface.

Example: Querying the `marker` module for the access permissions on a marker:

[Marker module query interface](https://github.com/provenance-io/provenance/blob/main/proto/provenance/marker/v1/query.proto)

```kotlin
pbClient.markerClient.access(QueryAccessRequest.newBuilder().setId("marker address or denom here").build())
```

## Transaction Usage

### Example: creating a `Marker`

```kotlin
val mnemonic = "your 20 word phrase here" // todo use your own mnemonic
val walletSigner = WalletSigner(NetworkType.TESTNET, mnemonic)
val signers = listOf(BaseReqSigner(walletSigner))

val msgAddMarkerRequest: MsgAddMarkerRequest = // Your request here

val txn = TxOuterClass.TxBody.newBuilder()
    .addMessages(Any.pack(message = msgAddMarkerRequest, typeUrlPrefix = ""))
    .build()

pbClient.estimateAndBroadcastTx(
    txBody = txn,
    signers = signers,
    mode = ServiceOuterClass.BroadcastMode.BROADCAST_MODE_BLOCK,
    gasAdjustment = 1.5
)
```

**Note**: In general, `BROADCAST_MODE_BLOCK` is not recommended as your transaction may become successful past the time that 
client blocks while waiting for the response. Instead use `BROADCAST_MODE_SYNC`, and listen for transaction success
in the [Event Stream](https://github.com/provenance-io/event-stream) or query the client with the transaction hash to find the outcome of submission.

### Example: using an existing wallet created with [hdwallet](https://github.com/provenance-io/hdwallet/)

```kotlin
import com.google.protobuf.Any
import com.google.protobuf.ByteString
import com.google.protobuf.GeneratedMessageV3
import com.google.protobuf.Message
import cosmos.bank.v1beta1.Tx
import cosmos.base.v1beta1.CoinOuterClass
import cosmos.crypto.secp256k1.Keys
import cosmos.tx.v1beta1.TxOuterClass
import io.provenance.client.grpc.BaseReqSigner
import io.provenance.client.grpc.Signer
import io.provenance.client.wallet.NetworkType
import io.provenance.hdwallet.bip39.MnemonicWords
import io.provenance.hdwallet.wallet.Account
import io.provenance.hdwallet.wallet.Wallet
import java.net.URI
import java.util.concurrent.TimeUnit

// Some helper extension methods:
fun Message.toAny(typeUrlPrefix: String = ""): Any = Any.pack(this, typeUrlPrefix)

fun Iterable<Any>.toTxBody(memo: String? = null): TxOuterClass.TxBody =
    TxOuterClass.TxBody.newBuilder()
        .addAllMessages(this)
        .also { builder -> memo?.run { builder.memo = this } }
        .build()

fun main(args: Array<String>) {

    // Create a wallet using the hdwallet library:
    val wallet = Wallet.fromMnemonic(
        hrp = NetworkType.TESTNET.prefix,
        passphrase = "",
        mnemonicWords = MnemonicWords.of("fly fly comfort"),
        testnet = true
    )

    // Derive an account from a path:
    val account: Account = wallet[NetworkType.TESTNET.path]
    val address: String = account.address.value

    // Construct the Provenance client:
    val pbClient = PbClient(
        chainId = "chain-local",
        channelUri = URI("http://localhost:9090"),
        gasEstimationMethod = GasEstimationMethod.MSG_FEE_CALCULATION,
        opts = ChannelOpts(idleTimeout = (1L to TimeUnit.MINUTES))
    )

    // Implement the [Signer] interface for signing transactions on Provenance:
    val signer = object : Signer {
        override fun address(): String = address

        override fun pubKey(): Keys.PubKey =
            Keys.PubKey
                .newBuilder()
                .setKey(ByteString.copyFrom(account.keyPair.publicKey.compressed()))
                .build()

        override fun sign(data: ByteArray): ByteArray = account.sign(data)
    }

    // Send some hash from one account to another:
    val senderAddress = address
    val receiverAddress = "tp1pxxgsr8efdxvfylxg5uewpalds6cg6c8eg0l9m"

    println("Sending hash from $senderAddress to $receiverAddress")

    // 1. Construct the coin amount:
    val amount: CoinOuterClass.Coin = CoinOuterClass.Coin
        .newBuilder()
        .setDenom("nhash")
        .setAmount("1")
        .build()

    // 2. Build the send message:
    val sendMessage: GeneratedMessageV3 =
        Tx.MsgSend.newBuilder()
            .setFromAddress(senderAddress)
            .setToAddress(receiverAddress)
            .addAmount(amount)
            .build()

    // 3. Wrap the message in a transaction body:
    val txBody: TxOuterClass.TxBody = listOf(sendMessage.toAny()).toTxBody()

    // 4. Estimate the gas fee for the transaction and broad cast it to the blockchain:
    val response = pbClient.estimateAndBroadcastTx(
        txBody = txBody,
        signers = listOf(BaseReqSigner(signer))
    )

    println(if (response.txResponse.code == 0) "ok" else "error")
    println(response)
}
```