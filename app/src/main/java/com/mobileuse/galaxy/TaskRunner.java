package com.mobileuse.galaxy;
import android.content.*;import android.os.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public final class TaskRunner {
 private static final ExecutorService pool=Executors.newSingleThreadExecutor();private static final AtomicBoolean running=new AtomicBoolean(false);private static volatile Future<?> job;
 public static boolean isRunning(){return running.get();}
 public static void stop(){running.set(false);Future<?> j=job;if(j!=null)j.cancel(true);ControlService.stop();}
 public static boolean start(Context context,String key,String task,boolean sendImages){if(!ActionPolicy.safeTask(task)||ControlService.active==null||CaptureService.instance==null||!running.compareAndSet(false,true))return false;
  Context app=context.getApplicationContext();ControlService.arm();job=pool.submit(()->{android.content.SharedPreferences prefs=app.getSharedPreferences("local_progress",Context.MODE_PRIVATE);try{for(int step=0;step<30&&running.get();step++){
    String screen=ControlService.inspect();if(ControlService.isOwnAppForeground())throw new IllegalStateException("Mobile Use 앱 화면이 아닌 개인정보 없는 테스트 앱을 열고 실행하세요");if(ActionPolicy.sensitive(screen))throw new SecurityException("민감한 화면 감지");byte[] frame=sendImages?CaptureService.instance.frame():null;
    OpenAiClient.Decision d=OpenAiClient.decide(key,task,screen,frame,sendImages);if(!running.get())break;
    prefs.edit().putInt("step",step+1).putString("last_action",d.action).apply();if("done".equals(d.action))break;
    if("tap".equals(d.action)||d.action.startsWith("scroll")){android.util.DisplayMetrics m=app.getResources().getDisplayMetrics();float x=(float)(d.x*m.widthPixels),y=(float)(d.y*m.heightPixels);float endY="scroll_up".equals(d.action)?m.heightPixels*.25f:"scroll_down".equals(d.action)?m.heightPixels*.75f:y;
      float startY="scroll_up".equals(d.action)?m.heightPixels*.75f:"scroll_down".equals(d.action)?m.heightPixels*.25f:y;
      if(!ControlService.gesture(x,startY,x,endY,"tap".equals(d.action)?100:420))throw new IllegalStateException("제어 불가");}
    Thread.sleep(1300);
   }}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception e){prefs.edit().putString("last_error",e.getMessage()==null?"실행 오류":e.getMessage()).apply();}finally{running.set(false);ControlService.stop();}});return true;}
}
