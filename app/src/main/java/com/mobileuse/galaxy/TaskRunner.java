package com.mobileuse.galaxy;
import android.content.*;import android.os.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
/** Bounded execution with a wall-clock watchdog and duplicate-action detection. */
public final class TaskRunner {
 private static final ExecutorService pool=Executors.newSingleThreadExecutor();
 private static final AtomicBoolean running=new AtomicBoolean(false);
 private static final AtomicInteger generation=new AtomicInteger();
 private static final Handler watchdog=new Handler(Looper.getMainLooper());
 private static volatile Future<?> job;
 private static final int MAX_STEPS=8;
 private static final long MAX_DURATION_MS=90000;
 public static boolean isRunning(){return running.get();}
 public static void stop(){generation.incrementAndGet();running.set(false);Future<?> j=job;if(j!=null)j.cancel(true);ControlService.stop();}
 public static boolean start(Context context,String key,String task,boolean sendImages){
  if(!ActionPolicy.safeTask(task)||ControlService.active==null||CaptureService.instance==null||!running.compareAndSet(false,true))return false;
  final int token=generation.incrementAndGet();
  final Context app=context.getApplicationContext();
  final android.content.SharedPreferences prefs=app.getSharedPreferences("local_progress",Context.MODE_PRIVATE);
  prefs.edit().remove("last_error").putString("last_action","준비 중").apply();
  watchdog.postDelayed(()->{if(generation.get()==token&&running.get()){prefs.edit().putString("last_error","전체 실행 시간 90초 초과로 자동 중지").apply();stop();}},MAX_DURATION_MS);
  ControlService.arm();
  job=pool.submit(()->{
   String previous="";int repeats=0;int waits=0;
   try{
    Thread.sleep(4000);
    for(int step=0;step<MAX_STEPS&&running.get()&&generation.get()==token;step++){
     if(ControlService.isOwnAppForeground())throw new IllegalStateException("Mobile Use 앱 대신 개인정보 없는 테스트 앱을 열어주세요");
     String screen=ControlService.inspect();
     if(ActionPolicy.sensitive(screen))throw new SecurityException("민감한 화면 감지");
     prefs.edit().putString("last_action","API 응답 대기 ("+(step+1)+"/"+MAX_STEPS+")").apply();
     CaptureService capture=CaptureService.instance;
     if(capture==null)throw new IllegalStateException("화면 캡처가 종료되었습니다");
     byte[] frame=sendImages?capture.frame():null;
     OpenAiClient.Decision d=OpenAiClient.decide(key,task,screen,frame,sendImages);
     if(!running.get()||generation.get()!=token)break;
     prefs.edit().putInt("step",step+1).putString("last_action",d.action).apply();
     if("done".equals(d.action))break;
     if("wait".equals(d.action)){if(++waits>=2)throw new IllegalStateException("AI가 연속 대기를 요청하여 자동 중지");}
     else waits=0;
     String signature=d.action+":"+Math.round(d.x*20)+":"+Math.round(d.y*20);
     repeats=signature.equals(previous)?repeats+1:0;previous=signature;
     if(repeats>=2)throw new IllegalStateException("동일 행동 3회 반복 감지로 자동 중지");
     if("tap".equals(d.action)||d.action.startsWith("scroll")){
      android.util.DisplayMetrics m=app.getResources().getDisplayMetrics();
      float x=(float)(d.x*m.widthPixels),y=(float)(d.y*m.heightPixels);
      float startY="scroll_up".equals(d.action)?m.heightPixels*.75f:"scroll_down".equals(d.action)?m.heightPixels*.25f:y;
      float endY="scroll_up".equals(d.action)?m.heightPixels*.25f:"scroll_down".equals(d.action)?m.heightPixels*.75f:y;
      if(!ControlService.gesture(x,startY,x,endY,"tap".equals(d.action)?100:420))throw new IllegalStateException("제어 불가");
     }
     Thread.sleep(1300);
    }
    if(running.get()&&generation.get()==token&&prefs.getInt("step",0)>=MAX_STEPS) prefs.edit().putString("last_error","최대 8단계에 도달하여 종료").apply();
   }catch(InterruptedException e){Thread.currentThread().interrupt();}
    catch(Exception e){if(generation.get()==token)prefs.edit().putString("last_error",e.getMessage()==null?"실행 오류":e.getMessage()).apply();}
    finally{if(generation.get()==token){running.set(false);ControlService.stop();}}
  });
  return true;
 }
}
