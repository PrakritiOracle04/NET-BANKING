package com.oracle.banking.scheduler.service;

import com.oracle.banking.scheduler.entity.BankingSchedule;
import com.oracle.banking.scheduler.entity.ScheduleType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

@Component
public class RecurrenceCalculator {
    public Instant firstExecution(Instant startAt, String timezone) {
        ZoneId.of(timezone);
        return startAt;
    }

    public Instant next(BankingSchedule schedule, Instant fromScheduledTime) {
        if (schedule.getScheduleType() == ScheduleType.ONE_TIME) return null;
        ZoneId zone = ZoneId.of(schedule.getTimezone());
        ZonedDateTime current = fromScheduledTime.atZone(zone);
        ZonedDateTime candidate = switch (schedule.getScheduleType()) {
            case DAILY -> current.plusDays(1);
            case WEEKLY -> current.plusWeeks(1);
            case MONTHLY -> nextMonthly(current, schedule.getRequestedDayOfMonth());
            case ONE_TIME -> current;
        };
        Instant next = candidate.toInstant();
        return schedule.getEndAt() != null && next.isAfter(schedule.getEndAt()) ? null : next;
    }

    public Integer requestedDay(Instant startAt, String timezone, ScheduleType scheduleType) {
        if (scheduleType != ScheduleType.MONTHLY) return null;
        return startAt.atZone(ZoneId.of(timezone)).getDayOfMonth();
    }

    public Instant dailyAt(ZoneId zone, String localTime) {
        String[] parts = localTime.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return LocalDate.now(zone).atTime(hour, minute).atZone(zone).toInstant();
    }

    private ZonedDateTime nextMonthly(ZonedDateTime current, Integer requestedDay) {
        ZonedDateTime nextMonth = current.plusMonths(1);
        int targetDay = requestedDay == null ? current.getDayOfMonth() : requestedDay;
        int monthLength = nextMonth.toLocalDate().lengthOfMonth();
        return nextMonth.withDayOfMonth(Math.min(targetDay, monthLength));
    }

    public Instant parseStart(OffsetDateTime startAt) {
        return startAt.toInstant();
    }
}
