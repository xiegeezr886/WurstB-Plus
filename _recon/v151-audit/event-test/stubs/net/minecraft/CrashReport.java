package net.minecraft;
public class CrashReport {
  public static CrashReport forThrowable(Throwable t, String message) { return new CrashReport(); }
  public CrashReportCategory addCategory(String name) { return new CrashReportCategory(); }
}
