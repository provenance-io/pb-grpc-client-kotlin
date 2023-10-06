package io.provenance.client.exceptions

import io.grpc.Status
import io.grpc.StatusRuntimeException

class TransactionTimeoutException(message: String): StatusRuntimeException(Status.DEADLINE_EXCEEDED.withDescription(message))