package com.mobileuse.galaxy;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.concurrent.atomic.AtomicBoolean;
public final class ControlService extends AccessibilityService {
 static volatile ControlService active;
 static final AtomicBoolean stopped=new AtomicBoolean(true);
 static final AtomicBoolean busy=new AtomicBoolean(false);
 static volatile long lastAction=0;
 @Override protected void onServiceConnected(){active=this;stop();}
 @Override public void onAccessibilityEvent(AccessibilityEvent e){}
 @Override public void onInterrupt(){stop();}
 @Override public void onDestroy(){stop();active=null;super.onDestroy();}
 static void stop(){stopped.set(true);busy.set(false);}
 static void arm(){stopped.set(false);}
 static String inspect(){
  ControlService s=active;
  if(s==null)return "접근성 서비스를 활성화하세요";
  AccessibilityNodeInfo root=s.getRootInActiveWindow();
  if(root==null)return "현재 화면을 읽을 수 없습니다";
  StringBuilder out=new StringBuilder();
  walk(root,out,0);return out.toString();
 }
 private static void walk(AccessibilityNodeInfo n,StringBuilder out,int depth){
  if(n==null||depth>12||out.length()>5000)return;
  if(n.isPassword())return;
  CharSequence txt=n.getText(),desc=n.getContentDescription();
  String t=txt==null?"":txt.toString(), d=desc==null?"":desc.toString();
  if(!t.isEmpty()||!d.isEmpty()){
   android.graphics.Rect r=new android.graphics.Rect();n.getBoundsInScreen(r);
   out.append("[").append(r.centerX()).append(",").append(r.centerY()).append("] ")
     .append(PrivacyFilter.clean(t+" "+d)).append("\n");
  }
  for(int i=0;i<n.getChildCount();i++)walk(n.getChild(i),out,depth+1);
 }
 static boolean gesture(float x,float y,float ex,float ey,long duration){
  ControlService s=active;
  if(s==null||stopped.get()||!busy.compareAndSet(false,true)||duration<50||duration>1200)return false;
  long now=android.os.SystemClock.elapsedRealtime();
  if(now-lastAction<650){busy.set(false);return false;}
  lastAction=now;
  Path path=new Path();path.moveTo(x,y);if(x!=ex||y!=ey)path.lineTo(ex,ey);
  GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,duration)).build();
  boolean accepted=s.dispatchGesture(g,new GestureResultCallback(){
   @Override public void onCompleted(GestureDescription d){busy.set(false);}
   @Override public void onCancelled(GestureDescription d){busy.set(false);}
  },new Handler(Looper.getMainLooper()));
  if(!accepted)busy.set(false);
  return accepted;
 }
}
