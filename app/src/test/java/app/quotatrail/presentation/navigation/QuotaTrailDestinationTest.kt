package app.quotatrail.presentation.navigation

import app.quotatrail.R
import app.quotatrail.presentation.auth.AddAccountEntryMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class QuotaTrailDestinationTest {
    @Test
    fun `bottom tabs are home account settings in order`() {
        assertEquals(
            listOf(QuotaTrailDestination.Home, QuotaTrailDestination.Account, QuotaTrailDestination.Settings),
            QuotaTrailDestination.bottomTabs,
        )
        assertEquals(
            listOf("home", "account", "settings"),
            QuotaTrailDestination.bottomTabs.map { it.route },
        )
    }

    @Test
    fun `bottom tabs declare label content description and icon resources`() {
        QuotaTrailDestination.bottomTabs.forEach { route ->
            assertNotEquals("label resource must be set for ${route.route}", 0, route.labelResId)
            assertNotEquals(
                "content description resource must be set for ${route.route}",
                0,
                route.contentDescriptionResId,
            )
            assertNotEquals("icon resource must be set for ${route.route}", 0, route.iconResId)
        }

        assertEquals(R.string.tab_home, QuotaTrailDestination.Home.labelResId)
        assertEquals(R.string.tab_home_content_description, QuotaTrailDestination.Home.contentDescriptionResId)
        assertEquals(R.drawable.ic_tab_home, QuotaTrailDestination.Home.iconResId)

        assertEquals(R.string.tab_account, QuotaTrailDestination.Account.labelResId)
        assertEquals(R.string.tab_account_content_description, QuotaTrailDestination.Account.contentDescriptionResId)
        assertEquals(R.drawable.ic_tab_account, QuotaTrailDestination.Account.iconResId)

        assertEquals(R.string.tab_settings, QuotaTrailDestination.Settings.labelResId)
        assertEquals(R.string.tab_settings_content_description, QuotaTrailDestination.Settings.contentDescriptionResId)
        assertEquals(R.drawable.ic_tab_settings, QuotaTrailDestination.Settings.iconResId)
    }

    @Test
    fun `widget launch destination resolves add account route`() {
        assertEquals(
            QuotaTrailDestination.Home.route,
            QuotaTrailDestination.startRouteForLaunchDestination(null),
        )
        assertEquals(
            QuotaTrailDestination.Home.route,
            QuotaTrailDestination.startRouteForLaunchDestination(QuotaTrailLaunchDestination.Home.value),
        )
        assertEquals(
            QuotaTrailDestination.AddAccount.route,
            QuotaTrailDestination.startRouteForLaunchDestination(QuotaTrailLaunchDestination.AddAccount.value),
        )
    }

    @Test
    fun `add account routes only preserve device code login entry mode`() {
        assertEquals(
            "add_account/login",
            QuotaTrailDestination.AddAccount.routeFor(AddAccountEntryMode.LoginToCodex),
        )
        assertEquals(
            AddAccountEntryMode.LoginToCodex,
            AddAccountEntryMode.fromRouteValue("login"),
        )
        assertEquals(
            AddAccountEntryMode.Choose,
            AddAccountEntryMode.fromRouteValue("import"),
        )
        assertEquals(
            AddAccountEntryMode.Choose,
            AddAccountEntryMode.fromRouteValue("unexpected"),
        )
    }
}
