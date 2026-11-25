package com.mobileuse.galaxy;
import java.util.regex.Pattern;
public final class PrivacyFilter {
 private PrivacyFilter(){}
 private static final Pattern EMAIL=Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
 private static final Pattern PHONE=Pattern.compile("(?<!\\d)01[016789][- ]?\\d{3,4}[- ]?\\d{4}(?!\\d)");
 private static final Pattern ID=Pattern.compile("(?<!\\d)\\d{6}[- ]?[1-8]\\d{6}(?!\\d)");
 private static final Pattern CARD=Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");
 public static String clean(String s){if(s==null)return "";String v=EMAIL.matcher(s).replaceAll("[EMAIL]");v=PHONE.matcher(v).replaceAll("[PHONE]");v=ID.matcher(v).replaceAll("[ID]");return CARD.matcher(v).replaceAll("[NUMBER]");}
}
