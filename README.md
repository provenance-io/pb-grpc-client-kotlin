# GRPC client for the Provenance Blockchain

**Tip**: Refer to the [Cosmos Proto Docs](https://docs.cosmos.network/master/core/proto-docs.html) and
[Provenance Blockchain Proto Docs](https://github.com/provenance-io/provenance/blob/main/docs/proto-docs.md) for
client interface definitions.


### Maven

```xml
<dependency>
  <groupId>io.provenance.client</groupId>
  <artifactId>pb-grpc-client-kotlin</artifactId>
  <version>${version}</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.provenance.client:pb-grpc-client-kotlin:${version}'
```

## Setup

Setup the client by supplying the chain id (e.g. `pio-testnet-1`) and URI of the node to which you are connecting. The normal GRPC port is `9090`.

Example: for a locally running testnet instance:
```kotlin
val pbClient = PbClient("chain-local", URI("http://localhost:9090"))
```

Optionally configure GRPC by also passing `ChannelOpts` or a `NettyChannelBuilder`.

Example: Set client idle timeout to 1 minute
```kotlin
val pbClient = PbClient(
    chainId = "chain-local",
    channelUri = URI("http://localhost:9090"),
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

Example: creating a `Marker`

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