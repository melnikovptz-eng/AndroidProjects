package ru.aeroclub.informer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** W01 KD PILOT widget. S01 calendar and its event-card flow remain separate. */
class FlightHomeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        FlightRepository.ensureInitialized(context)
        FlightRepository.syncFromDownload(context)
        val now = System.currentTimeMillis()
        val todayIso = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(now))
        val upcoming = FlightRepository.getFlights(context)
            .filterNot { it.isDemo }
            .mapNotNull { f -> FlightDateUtils.toMillis(f.dateIso, f.time).let { t -> if (t > now) f to t else null } }
            .sortedBy { it.second }
        val today = upcoming.firstOrNull { it.first.dateIso == todayIso }?.first
        val flight = today ?: upcoming.firstOrNull()?.first
        appWidgetIds.forEach { render(context, manager, it, flight, today != null) }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        super.onReceive(context, intent)
        val action = intent?.action ?: return
        if (!action.startsWith(ACTION_PREFIX)) return
        if (action == REFRESH) { refresh(context); return }
        val id = intent.getStringExtra(EXTRA_FLIGHT_ID)
        val flight = if (id.isNullOrBlank()) null else FlightRepository.getById(context, id)
        if (flight == null) {
            Toast.makeText(context, "Мероприятие не найдено. Откройте календарь.", Toast.LENGTH_SHORT).show()
            refresh(context)
            return
        }
        when (action) {
            GOING, NOT_GOING -> {
                val going = action == GOING
                val participant = AttendanceRepository.getSelectedParticipantId(context)
                val status = if (going) AttendanceRepository.STATUS_GOING else AttendanceRepository.STATUS_NOT_GOING
                val saved = if (participant.isNullOrBlank())
                    AttendanceRepository.setResponse(context, flight.id, status)
                else AttendanceRepository.setResponse(context, participant, flight.id, status)
                val message = if (!saved) "Не удалось сохранить ответ" else if (going) "Ответ сохранён: Я еду" else "Ответ сохранён: Я не еду"
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                refresh(context)
            }
            ROUTE -> FlightActionLauncher.openRoute(context, flight)
            TELEGRAM -> FlightActionLauncher.shareFlight(context, flight)
        }
    }

    private fun render(c: Context, manager: AppWidgetManager, widgetId: Int, flight: Flight?, hasFlightToday: Boolean) {
        val views = RemoteViews(c.packageName, R.layout.widget_flight_home)
        views.setTextViewText(R.id.widget_logo, "KD PILOT")
        views.setTextViewText(R.id.widget_club_name, ClubSettingsRepository.get(c).clubName.uppercase(Locale.getDefault()).take(22))
        views.setTextViewText(R.id.widget_status, when {
            hasFlightToday -> "СЕГОДНЯ ПОЛЁТЫ"
            flight != null -> "БЛИЖАЙШИЙ ВЫЛЕТ"
            else -> "НЕТ ЗАПЛАНИРОВАННЫХ ПОЛЁТОВ"
        })
        views.setTextViewText(R.id.widget_aerodrome, flight?.place?.takeIf { it.isNotBlank() } ?: "Аэродром не указан")
        views.setTextViewText(R.id.widget_time, flight?.time ?: "--:--")
        // Remote incoming messages are not implemented; keep the badge honest.
        views.setTextViewText(R.id.widget_message_badge, "0")
        val open = Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        views.setOnClickPendingIntent(R.id.widget_header, PendingIntent.getActivity(c, 8100 + widgetId, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        views.setOnClickPendingIntent(R.id.widget_status, PendingIntent.getActivity(c, 8200 + widgetId, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        bind(c, views, widgetId, R.id.widget_going, GOING, flight)
        bind(c, views, widgetId, R.id.widget_not_going, NOT_GOING, flight)
        bind(c, views, widgetId, R.id.widget_route, ROUTE, flight)
        bind(c, views, widgetId, R.id.widget_telegram, TELEGRAM, flight)
        manager.updateAppWidget(widgetId, views)
    }

    private fun bind(c: Context, views: RemoteViews, widgetId: Int, viewId: Int, action: String, flight: Flight?) {
        val intent = Intent(c, FlightHomeWidgetProvider::class.java).setAction(action)
        if (flight != null) intent.putExtra(EXTRA_FLIGHT_ID, flight.id)
        val pi = PendingIntent.getBroadcast(c, (action.hashCode() and 0x00ffffff) + widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(viewId, pi)
    }

    companion object {
        private const val ACTION_PREFIX = "ru.aeroclub.informer.widget."
        private const val EXTRA_FLIGHT_ID = "flight_id"
        const val GOING = ACTION_PREFIX + "GOING"
        const val NOT_GOING = ACTION_PREFIX + "NOT_GOING"
        const val ROUTE = ACTION_PREFIX + "ROUTE"
        const val TELEGRAM = ACTION_PREFIX + "TELEGRAM"
        const val REFRESH = ACTION_PREFIX + "REFRESH"

        @JvmStatic fun refresh(context: Context?) {
            if (context == null) return
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, FlightHomeWidgetProvider::class.java))
            if (ids.isNotEmpty()) FlightHomeWidgetProvider().onUpdate(context, manager, ids)
        }
    }
}
