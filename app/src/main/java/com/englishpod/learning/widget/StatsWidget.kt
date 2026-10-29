package com.englishpod.learning.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.englishpod.learning.EnglishPodApp
import com.englishpod.learning.MainActivity
import com.englishpod.learning.core.Fmt

/**
 * Home-screen widget: today's study progress, streak and due-word count with a one-tap resume
 * that opens the app and continues the last course.
 */
class StatsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = EnglishPodApp.store
        val stats = store.stats.value
        val settings = store.settings.value
        val today = stats.today()
        val goalMinutes = settings.dailyGoalMinutes.coerceAtLeast(1)
        val fraction = (today.seconds.toFloat() / (goalMinutes * 60)).coerceIn(0f, 1f)
        val due = store.dueWords().size
        val streak = stats.streak()

        provideContent {
            GlanceTheme {
                Box(
                    modifier = GlanceModifier.fillMaxSize().padding(12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = "English Pod",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = ColorProvider(Color.Gray),
                                ),
                            )
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = "今日 ${Fmt.studyMinutes(today.seconds)} / ${goalMinutes}分",
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GlanceTheme.colors.onSurface,
                                ),
                            )
                            Spacer(modifier = GlanceModifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = fraction,
                                modifier = GlanceModifier.fillMaxWidth().height(6.dp),
                                color = GlanceTheme.colors.primary,
                                backgroundColor = GlanceTheme.colors.secondary,
                            )
                            Spacer(modifier = GlanceModifier.height(6.dp))
                            Text(
                                text = buildString {
                                    append("🔥 $streak 天")
                                    if (due > 0) append(" · 📖 $due 词待复习")
                                },
                                style = TextStyle(fontSize = 12.sp, color = ColorProvider(Color.Gray)),
                            )
                        }
                        Spacer(modifier = GlanceModifier.width(12.dp))
                        Box(
                            modifier = GlanceModifier
                                .background(GlanceTheme.colors.primary)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .clickable(
                                    actionStartActivity<MainActivity>(
                                        parameters = actionParametersOf(
                                            ActionParameters.Key<Boolean>(MainActivity.EXTRA_AUTO_RESUME) to true,
                                        ),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (today.seconds > 0L) "继续" else "开始",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GlanceTheme.colors.onPrimary,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

class StatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StatsWidget()
}
