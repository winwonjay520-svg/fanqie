package com.lumifocus.app;
import java.time.LocalDate;
/** Runs the actual native recurrence helper on the host JDK; no Android device data. */
public final class CalendarDatesChecks {
 private static int count;
 private static void check(String date,String repeat,String today,String expected){String actual=CalendarDates.occurrence(date,repeat,LocalDate.parse(today)).toString();if(!expected.equals(actual))throw new AssertionError(date+" on "+today+": "+actual);System.out.println("PASS "+date+" / "+repeat+" / "+today+" -> "+actual);count++;}
 public static void main(String[] args){
  check("1995-11-22","yearly","2026-11-22","2026-11-22");
  check("1995-11-22","yearly","2026-11-23","2027-11-22");
  check("1995-01-01","yearly","2026-12-31","2027-01-01");
  check("2024-02-29","yearly","2025-01-01","2025-02-28");
  check("2024-02-29","yearly","2025-03-01","2026-02-28");
  check("2024-02-29","yearly","2028-01-01","2028-02-29");
  check("2024-01-31","monthly","2026-02-01","2026-02-28");
  check("2024-01-31","monthly","2026-03-01","2026-03-31");
  check("2026-11-13","weekly","2026-11-20","2026-11-20");
  check("2027-05-10","yearly","2026-11-22","2027-05-10");
  check("1995-11-22","none","2026-11-23","1995-11-22");
  boolean rejected=false;try{CalendarDates.occurrence("2025-02-29","yearly",LocalDate.of(2026,11,22));}catch(java.time.DateTimeException expected){rejected=true;}if(!rejected)throw new AssertionError("Invalid date accepted");count++;
  System.out.println("Native calendar checks: "+count+" passed");
 }
}
