package dev.akuzoi.ai.time;

import dev.akuzoi.ai.config.PluginSettings;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.ZoneId;
import java.util.Locale;

public final class TimeContext {
    private final PluginSettings settings;
    private final ZoneId zoneId;
    private final DateTimeFormatter formatter;

    public TimeContext(PluginSettings settings) {
        this.settings = settings;
        this.zoneId = resolveZone(settings.timeZone());
        this.formatter = resolveFormatter(settings.timeFormat());
    }

    public String promptLine() {
        if (!settings.timeEnabled()) {
            return "";
        }
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        return "当前现实时间：" + formattedNow()
                + "，" + dayOfWeek.getDisplayName(TextStyle.FULL, Locale.CHINA)
                + "，时区=" + zoneId.getId() + "。";
    }

    public String formattedNow() {
        return formatter.format(ZonedDateTime.now(zoneId));
    }

    private ZoneId resolveZone(String zone) {
        try {
            if (zone == null || zone.isBlank()) {
                return ZoneId.of("Asia/Shanghai");
            }
            return ZoneId.of(zone.trim());
        } catch (DateTimeException exception) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    private DateTimeFormatter resolveFormatter(String pattern) {
        try {
            if (pattern == null || pattern.isBlank()) {
                return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.CHINA);
            }
            return DateTimeFormatter.ofPattern(pattern, Locale.CHINA);
        } catch (IllegalArgumentException exception) {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.CHINA);
        }
    }
}
