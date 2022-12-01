# gRPC client for the Provenance Blockchain

**Tip**: Refer to the [Cosmos Proto Docs](https://docs.cosmos.network/main/tooling/protobuf) and
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
- the chain ID (e.g. `pio-testnet-1`, `chain-local`, etc.)
- the URI of the node to which you are connecting (the default port is `9090`)
- the gas estimation method
  - for pbc version 1.8 or higher, use `GasEstimationMethod.MSG_FEE_CALCULATION`
  - for pbc version 1.7 or lower, use `GasEstimationMethod.COSMOS_SIMULATION`

### Examples

#### Connect to a locally running testnet instance

```kotlin
// pbc version 1.8 or higher:
val pbClient = PbClient(
    chainId = "chain-local",
    channelUri = URI("http://localhost:9090"),
    gasEstimationMethod = GasEstimationMethod.MSG_FEE_CALCULATION
)

// pbc version 1.7 or lower:
val pbClient = PbClient(
    chainId = "chain-local",
    channelUri = URI("http://localhost:9090"),
    gasEstimationMethod = GasEstimationMethod.COSMOS_SIMULATION
)
```

#### Set client idle timeout to 1 minute

```kotlin
// Optionally configure GRPC by also passing `ChannelOpts` or a `NettyChannelBuilder`:
val pbClient = PbClient(
    chainId = "chain-local",
    channelUri = URI("http://localhost:9090"),
    gasEstimationMethod = GasEstimationMethod.MSG_FEE_CALCULATION,
    opts = ChannelOpts(idleTimeout = (1L to TimeUnit.MINUTES))
)
```

## Protobuf Bindings

[Java](https://search.maven.org/artifact/io.provenance/proto-java) and [Kotlin](https://search.maven.org/artifact/io.provenance/proto-kotlin) Protobuf bindings [(source)](https://github.com/provenance-io/provenance/tree/main/protoBindings) for the Provenance Blockchain are available on Maven Central.
These modules provide the type definitions needed to interact with the blockchain in code.

Refer to the [Cosmos Proto Docs](https://docs.cosmos.network/master/core/proto-docs.html) and
[Provenance Blockchain Proto Docs](https://github.com/provenance-io/provenance/blob/main/docs/proto-docs.md) for
an in-depth look at the client interface definitions.

### Extended functionality

Beyond definitions, a number of useful [extension methods](https://github.com/provenance-io/provenance/tree/main/protoBindings/bindings/kotlin/src/main/kotlin/io/provenance/client/protobuf) are provided in the `io.provenance.client.protobuf.extensions` package
that provide higher-level functionality for Kotlin types.

## Query Usage

`PBClient` contains individual clients for each Cosmos and Provenance Blockchain SDK query service. Each module contains a `query.proto`, which 
defines the query interface.

### Examples

#### Querying the `marker` module for the access permissions on a marker

```kotlin
pbClient.markerClient.access(QueryAccessRequest.newBuilder().setId("marker address or denom here").build())
```

See [Marker module query interface](https://github.com/provenance-io/provenance/blob/main/proto/provenance/marker/v1/query.proto).

## Transaction Usage

### Examples

#### Creating a `Marker`

```kotlin
val wallet: Signer = TODO()

val signers: List<BaseReqSigner> = listOf(BaseReqSigner(wallet))

val msgAddMarkerRequest: MsgAddMarkerRequest = MsgAddMarkerRequest
    .newBuilder()
    .setAmount(CoinOuterClass.Coin.newBuilder()
        .setAmount("100000000000")
        .setDenom("nhash")
    )
    .setManager(wallet.address().value)
    .setFromAddress(wallet.address().value)
    .setMarkerType(MarkerType.MARKER_TYPE_COIN)
    .setStatus(MarkerStatus.MARKER_STATUS_PROPOSED)
    .addAllAccessList(
        listOf(
            AccessGrant.newBuilder()
                .setAddress(wallet.address().value)
                .addAllPermissions(
                    listOf(
                        Access.ACCESS_ADMIN,
                        Access.ACCESS_BURN,
                        Access.ACCESS_MINT,
                        Access.ACCESS_DEPOSIT,
                        Access.ACCESS_WITHDRAW,
                        Access.ACCESS_DELETE,
                    )
                )
                .build()
        )
    )
    .build()

val txn = TxOuterClass.TxBody.newBuilder()
    .addMessages(Any.pack(message = msgAddMarkerRequest, typeUrlPrefix = ""))
    .build()

pbClient.estimateAndBroadcastTx(
    txBody = txn,
    signers = signers,
    mode = ServiceOuterClass.BroadcastMode.BROADCAST_MODE_BLOCK,  // DEPRECATED. See note below
    gasAdjustment = 1.5
)
```

**Note**: In general, `BROADCAST_MODE_BLOCK` is not recommended as your transaction may become successful past the time that 
client blocks while waiting for the response. Instead use `BROADCAST_MODE_SYNC`, and listen for transaction success
in the [Event Stream](https://github.com/provenance-io/event-stream) or query the client with the transaction hash to find the outcome of submission.

You can read about the various broadcast modes supported [here](https://docs.cosmos.network/master/run-node/txs.html#broadcasting-a-transaction) and [here](https://github.com/cosmos/cosmos-sdk/blob/master/proto/cosmos/tx/v1beta1/service.proto#L85).

## Code sample

#### Using an existing wallet created with [hdwallet](https://github.com/provenance-io/hdwallet/) to send hash from one address to another

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
