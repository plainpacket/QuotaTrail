package app.quotatrail.surfaces.widget

import android.appwidget.AppWidgetProviderInfo
import app.quotatrail.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetKeyguardMetadataTest {
    @Test
    fun `widget advertises home screen and keyguard categories`() {
        val parser = RuntimeEnvironment.getApplication().resources.getXml(R.xml.quotatrail_widget_info)
        while (parser.eventType != XmlPullParser.START_TAG) parser.next()

        val categories = parser.getAttributeIntValue(
            "http://schemas.android.com/apk/res/android",
            "widgetCategory",
            0,
        )

        assertEquals(
            AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN or AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD,
            categories,
        )
    }
}
