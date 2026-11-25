package com.mobileuse.galaxy;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.WindowManager;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.graphics.Color;
import android.widget.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.concurrent.atomic.AtomicBoolean;
public final class ControlService extends AccessibilityService {
 static volatile ControlService active;
 private WindowManager wm;private LinearLayout floating;private TextView floatingStatus;private TextView floatingLog;private final Handler overlayHandler=new Handler(Looper.getMainLooper());
 private final Runnable overlayTicker=new Runnable(){public void run(){if(floatingStatus!=null){android.content.SharedPreferences p=getSharedPreferences("local_progress",MODE_PRIVATE);floatingStatus.setText((TaskRunner.isRunning()?"AI 실행 중":"AI 대기")+" | 단계 "+p.getInt("step",0));if(floatingLog!=null)floatingLog.setText(p.getString("live_log","기록 없음"));}overlayHandler.postDelayed(this,1000);}};
 static final AtomicBoolean stopped=new AtomicBoolean(true);
 static final AtomicBoolean busy=new AtomicBoolean(false);
 static volatile long lastAction=0;
 @Override protected void onServiceConnected(){active=this;stop();showFloating();}
 @Override public void onAccessibilityEvent(AccessibilityEvent e){}
 @Override public void onInterrupt(){stop();}
 @Override public void onDestroy(){stop();overlayHandler.removeCallbacks(overlayTicker);if(wm!=null&&floating!=null){wm.removeView(floating);floating=null;}active=null;super.onDestroy();}
 private void showFloating(){
  if(floating!=null)return;
  wm=(WindowManager)getSystemService(WINDOW_SERVICE);
  floating=new LinearLayout(this);floating.setOrientation(LinearLayout.VERTICAL);floating.setPadding(12,8,12,8);floating.setBackgroundColor(0xDD202735);
  floatingStatus=new TextView(this);floatingStatus.setTextColor(Color.WHITE);floatingStatus.setTextSize(11);floatingStatus.setText("Mobile Use 대기 · 여기를 드래그");floating.addView(floatingStatus);
  floatingLog=new TextView(this);floatingLog.setTextColor(0xFFE0E6EF);floatingLog.setTextSize(10);floatingLog.setMaxLines(6);floatingLog.setText("기록 없음");floating.addView(floatingLog);
  LinearLayout buttons=new LinearLayout(this);floating.addView(buttons);
  Button start=new Button(this);start.setText("작업 실행");buttons.addView(start);
  start.setOnClickListener(v->{if(TaskRunner.isRunning())return;boolean ok=TaskRunner.startPrepared(this);if(!ok)floatingStatus.setText("앱에서 작업/키 입력 및 화면 캡처 승인이 필요합니다");});
  Button stopButton=new Button(this);stopButton.setText("정지");buttons.addView(stopButton);
  stopButton.setOnClickListener(v->{TaskRunner.stop();floatingStatus.setText("긴급 정지됨");});
  WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,android.graphics.PixelFormat.TRANSLUCENT);
  lp.gravity=Gravity.TOP|Gravity.START;android.content.SharedPreferences placement=getSharedPreferences("overlay_position",MODE_PRIVATE);lp.x=placement.getInt("x",100);lp.y=placement.getInt("y",160);
  floatingStatus.setOnTouchListener(new View.OnTouchListener(){float downX,downY;int originX,originY;public boolean onTouch(View v,MotionEvent event){switch(event.getActionMasked()){case MotionEvent.ACTION_DOWN:downX=event.getRawX();downY=event.getRawY();originX=lp.x;originY=lp.y;return true;case MotionEvent.ACTION_MOVE:lp.x=Math.max(0,originX+Math.round(event.getRawX()-downX));lp.y=Math.max(0,originY+Math.round(event.getRawY()-downY));try{wm.updateViewLayout(floating,lp);}catch(Exception ignored){}return true;case MotionEvent.ACTION_UP:placement.edit().putInt("x",lp.x).putInt("y",lp.y).apply();return true;default:return false;}}});
  try{wm.addView(floating,lp);overlayHandler.post(overlayTicker);}catch(Exception e){android.util.Log.e("MobileUse","Floating control failed",e);}
 }
 static void stop(){stopped.set(true);busy.set(false);}
 static void arm(){stopped.set(false);}
 static boolean isOwnAppForeground(){ControlService s=active;if(s==null)return false;AccessibilityNodeInfo root=s.getRootInActiveWindow();return root!=null&&root.getPackageName()!=null&&s.getPackageName().contentEquals(root.getPackageName());}
 static boolean isPublicTestScreen(){
  ControlService s=active;if(s==null)return false;
  AccessibilityNodeInfo root=s.getRootInActiveWindow();
  if(root==null||root.getPackageName()==null)return false;
  return s.getPackageName().contentEquals(root.getPackageName()) && SandboxActivity.visible;
 }
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
