package com.lumifocus.app;
import java.time.*;
import java.util.Arrays;

final class CalendarDates {
    static void validateRepeat(String repeat){if(!Arrays.asList("none","yearly","monthly","weekly").contains(repeat))throw new IllegalArgumentException("无效重复周期");}
    static LocalDate occurrence(String date,String repeat,LocalDate today) {
        validateRepeat(repeat);LocalDate original=LocalDate.parse(date);
        if(!original.isBefore(today)||repeat.equals("none"))return original;
        if(repeat.equals("weekly"))return today.plusDays(Math.floorMod(original.toEpochDay()-today.toEpochDay(),7));
        if(repeat.equals("monthly")){YearMonth month=YearMonth.from(today);LocalDate next=month.atDay(Math.min(original.getDayOfMonth(),month.lengthOfMonth()));if(next.isBefore(today)){month=month.plusMonths(1);next=month.atDay(Math.min(original.getDayOfMonth(),month.lengthOfMonth()));}return next;}
        YearMonth month=YearMonth.of(today.getYear(),original.getMonthValue());LocalDate next=month.atDay(Math.min(original.getDayOfMonth(),month.lengthOfMonth()));if(next.isBefore(today)){month=month.plusYears(1);next=month.atDay(Math.min(original.getDayOfMonth(),month.lengthOfMonth()));}return next;
    }
}
