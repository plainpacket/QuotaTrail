package app.quotatrail.presentation.home

import app.quotatrail.domain.currency.CurrencyPreferenceReader
import app.quotatrail.domain.currency.CurrencyPreferences
import app.quotatrail.domain.currency.ExchangeRates
import app.quotatrail.storage.currency.ExchangeRateReader
import app.quotatrail.domain.model.QuotaWindowId
import app.quotatrail.domain.quota.QuotaWindow
import app.quotatrail.domain.quota.QuotaWindowAvailability
import app.quotatrail.domain.quota.QuotaWindowDisplayKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeBalanceStatusTest {
    private fun vm(rates: ExchangeRates? = null) = HomeViewModel(
        currencyPreferenceReader = object : CurrencyPreferenceReader {
            override suspend fun currencyPreferences() = CurrencyPreferences(targetCurrency = "USD")
        },
        exchangeRateReader = object : ExchangeRateReader {
            override suspend fun currentRates() = rates
        },
    )

    private fun balanceWindow(amount: String, currency: String = "CNY") = QuotaWindow(
        windowId = QuotaWindowId("balance"),
        titleKey = "balance",
        usedPercent = null,
        resetAt = null,
        limitWindowSeconds = null,
        isPrimaryCandidate = true,
        availability = QuotaWindowAvailability.Available,
        displayKind = QuotaWindowDisplayKind.Balance,
        balanceAmount = amount,
        balanceCurrency = currency,
    )

    @Test
    fun `balance above caution is normal`() {
        assertEquals(HomeQuotaStatus.Normal, vm().testQuotaStatusFor(balanceWindow("100.0")))
    }

    @Test
    fun `balance below warning is warning`() {
        assertEquals(HomeQuotaStatus.Warning, vm().testQuotaStatusFor(balanceWindow("0.5")))
    }

    @Test
    fun `balance between thresholds is caution`() {
        assertEquals(HomeQuotaStatus.Caution, vm().testQuotaStatusFor(balanceWindow("3.0")))
    }

    @Test
    fun `conversion is applied via injected readers`() = runTest {
        val rates = ExchangeRates("USD", mapOf("USD" to 1.0, "CNY" to 7.2), java.time.Instant.EPOCH)
        val viewModel = vm(rates = rates)
        viewModel.loadPreferencesForTest()
        // 7.2 CNY = 1.0 USD <= warning(1.0) => Warning (without conversion 7.2 > caution(5.0) => Normal)
        assertEquals(HomeQuotaStatus.Warning, viewModel.testQuotaStatusFor(balanceWindow("7.2", "CNY")))
    }

    @Test
    fun `no conversion leaves native amount`() = runTest {
        val viewModel = vm(rates = null)
        viewModel.loadPreferencesForTest()
        // No rates -> no conversion -> native 7.2 CNY > caution(5.0) => Normal
        assertEquals(HomeQuotaStatus.Normal, viewModel.testQuotaStatusFor(balanceWindow("7.2", "CNY")))
    }
}
