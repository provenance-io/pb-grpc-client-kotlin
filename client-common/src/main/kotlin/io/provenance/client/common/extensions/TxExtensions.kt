package io.provenance.client.common.extensions

import cosmos.tx.v1beta1.TxOuterClass
import tech.figure.hdwallet.common.hashing.sha256
import tech.figure.hdwallet.encoding.base16.Base16

fun TxOuterClass.TxRaw.txHash(): String = toByteArray().sha256().toHexString()
fun ByteArray.toHexString(): String = Base16.encode(this).uppercase()