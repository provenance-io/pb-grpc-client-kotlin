package io.provenance.client.internal.extensions

import org.junit.Assert
import org.junit.Test

class CoinExtensionsTest {
    // Value that triggers scientific notation.
    private val numberString = "1481937600"
    private val number = 1481937600.0
    private val denom = "cc"

    @Test
    fun testNumberToCoinWithDenom() {
        val coin = number.toCoin(denom)

        Assert.assertEquals(numberString, coin.amount)
        Assert.assertEquals(denom, coin.denom)

        val bigNum = Long.MAX_VALUE / 100_000 * 100_000.0
        val bigCoin = bigNum.toCoin(denom)
        Assert.assertNotEquals(bigCoin.amount, bigNum.toString())
        Assert.assertEquals(bigCoin.amount, bigNum.toBigDecimal().toPlainString())
    }

    @Test
    fun testStringToCoin() {
        val coin = "${number.toBigDecimal().toPlainString()}$denom".toCoin()
        Assert.assertEquals(denom, coin.denom)
        Assert.assertEquals(numberString, coin.amount)

        // Missing denom
        try {
            "$number".toCoin()
            Assert.fail()
        } catch (e: Throwable) {
            // Expected
        }

        // Missing value
        try {
            denom.toCoin()
            Assert.fail()
        } catch (e: Throwable) {
            // Expected
        }
    }

    @Test
    fun testCoinTimesDouble() {
        val coin = (3e18.toBigDecimal().toPlainString() + "galaxies").toCoin()
        val factor = 100_000.0.toBigDecimal()
        val newCoin = coin * factor
        Assert.assertNotEquals(3e24.toBigDecimal().toPlainString(), newCoin.amount)
    }

    @Test
    fun testListCoinDiscreteSum() {
        val listDiff = listOf("1000foo", "1000bar", "1000baz").map { it.toCoin() }
        val dsDiff = listDiff.discreteSum()
        val expectDiff = listOf("1000bar", "1000baz", "1000foo").map { it.toCoin() }

        // Different coins should come out the same.
        Assert.assertEquals(expectDiff, dsDiff)

        val listSame = listOf("1000foo", "1000foo", "1000foo").map { it.toCoin() }
        val dsSame = listSame.discreteSum()
        val expectSame = listOf("3000foo".toCoin())

        // Same coins should sum
        Assert.assertEquals(expectSame, dsSame)

        val listMixed = listOf("1000foo", "1000bar", "1000foo", "1000bar").map { it.toCoin() }
        val dsMixed = listMixed.discreteSum()
        val expectMixed = listOf("2000bar", "2000foo").map { it.toCoin() }

        // Mixed coins should sum and be ordered by denom
        Assert.assertEquals(expectMixed, dsMixed)
    }
}
