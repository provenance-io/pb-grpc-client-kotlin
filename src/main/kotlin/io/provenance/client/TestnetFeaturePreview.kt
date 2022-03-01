package io.provenance.client

@RequiresOptIn(message = "This API is experimental and only exists in test.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
annotation class TestnetFeature