package io.provenance.client.grpc.extensions

import com.google.protobuf.Any
import com.google.protobuf.Message
import cosmos.tx.v1beta1.TxOuterClass

fun Message.toAny(typeUrlPrefix: String = ""): Any = Any.pack(this, typeUrlPrefix)

fun Iterable<Any>.toTxBody(memo: String? = null, timeoutHeight: Long? = null): TxOuterClass.TxBody =
    TxOuterClass.TxBody.newBuilder()
        .addAllMessages(this)
        .also { builder ->
            memo?.run { builder.memo = this }
            timeoutHeight?.run { builder.timeoutHeight = this }
        }
        .build()

fun Any.toTxBody(memo: String? = null, timeoutHeight: Long? = null): TxOuterClass.TxBody =
    listOf(this).toTxBody(memo, timeoutHeight)