package com.mobileuse.galaxy;
public final class ActionPolicy {
 private ActionPolicy(){}
 public static boolean sensitive(String s){if(s==null)return true;String t=s.toLowerCase(java.util.Locale.ROOT);return t.contains("비밀번호")||t.contains("password")||t.contains("인증번호")||t.contains("otp")||t.contains("주민등록")||t.contains("계좌번호")||t.contains("카드번호")||t.contains("결제")||t.contains("송금")||t.contains("이체")||t.contains("로그인")||t.contains("security code")||t.contains("payment")||t.contains("transfer");}
 public static boolean safeTask(String s){return s!=null&&!s.trim().isEmpty()&&s.length()<=500&&!sensitive(s);}
 public static boolean valid(String action,double x,double y){return ("tap".equals(action)||"scroll_up".equals(action)||"scroll_down".equals(action)||"done".equals(action)||"wait".equals(action))&&Double.isFinite(x)&&Double.isFinite(y)&&x>=0&&x<=1&&y>=0&&y<=1;}
}
