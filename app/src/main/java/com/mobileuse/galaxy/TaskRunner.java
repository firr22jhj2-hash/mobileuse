package com.mobileuse.galaxy;
import android.content.*;import android.os.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
/** Bounded execution with a wall-clock watchdog and duplicate-action detection. */
public final class TaskRunner {
 private static final ExecutorService pool=Executors.newSingleThreadExecutor();
 private static final AtomicBoolean running=new AtomicBoolean(false);
 private static final AtomicInteger generation=new AtomicInteger();
 private static final Handler watchdog=new Handler(Looper.getMainLooper());
 private static volatile Future<?> job;
 private static final int MAX_STEPS=20;
 private static volatile boolean continuous=false;
 public static void setContinuous(boolean value){continuous=value;}
 private static volatile String preparedKey,preparedTask;
 private static volatile boolean preparedImages;
 public static void prepare(String key,String task,boolean images){preparedKey=key;preparedTask=task;preparedImages=images;}
 public static boolean startPrepared(Context c){return start(c,preparedKey,preparedTask,preparedImages);}
 private static final long MAX_DURATION_MS=120000;
 private static void log(android.content.SharedPreferences p,String message){String old=p.getString("live_log","");String next=(old.isEmpty()?"":old+"\n")+message;if(next.length()>900)next=next.substring(next.length()-900);p.edit().putString("live_log",next).apply();}
 public static boolean isRunning(){return running.get();}
 public static void stop(){generation.incrementAndGet();running.set(false);Future<?> j=job;if(j!=null)j.cancel(true);ControlService.stop();}
 public static boolean start(Context context,String key,String task,boolean sendImages){
  if(key==null||key.trim().isEmpty()||!ActionPolicy.safeTask(task)||ControlService.active==null||CaptureService.instance==null||!running.compareAndSet(false,true))return false;
  final int token=generation.incrementAndGet();
  final int limit=continuous?MAX_STEPS:1;
  final Context app=context.getApplicationContext();
  final android.content.SharedPreferences prefs=app.getSharedPreferences("local_progress",Context.MODE_PRIVATE);
  prefs.edit().remove("last_error").putInt("step",0).putString("last_action","준비 중").putString("live_log","시작: 작업 준비").apply();
  watchdog.postDelayed(()->{if(generation.get()==token&&running.get()){prefs.edit().putString("last_error","전체 실행 시간 120초 초과로 자동 중지").apply();log(prefs,"시간 제한으로 중지");stop();}},MAX_DURATION_MS);
  ControlService.arm();
  job=pool.submit(()->{
   String previous="";int repeats=0;int waits=0;
   try{
    Thread.sleep(500);
    for(int step=0;step<limit&&running.get()&&generation.get()==token;step++){
     if(ControlService.isOwnAppForeground())throw new IllegalStateException("Mobile Use 앱 대신 개인정보 없는 테스트 앱을 열어주세요");
     String screen=ControlService.inspect();if(screen.trim().isEmpty()||!ControlService.isPublicTestScreen())throw new SecurityException("허용된 공개 테스트 화면이 아닙니다");log(prefs,"화면 텍스트 확인: "+screen.length()+"자");
     if(ActionPolicy.sensitive(screen))throw new SecurityException("민감한 화면 감지");
     prefs.edit().putString("last_action","API 응답 대기 ("+(step+1)+"/"+limit+")").apply();log(prefs,"AI 판단 요청 중");
     CaptureService capture=CaptureService.instance;
     if(capture==null)throw new IllegalStateException("화면 캡처가 종료되었습니다");
     byte[] frame=null;
     OpenAiClient.Decision d=OpenAiClient.decide(key,task,screen,frame,false);
     if(!running.get()||generation.get()!=token)break;
     if(!ControlService.isPublicTestScreen()||!screen.equals(ControlService.inspect()))throw new SecurityException("응답 대기 중 화면 변경 감지");
     prefs.edit().putInt("step",step+1).putString("last_action",d.action).apply();log(prefs,"AI 판단: "+d.action);
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
      if(!ControlService.gesture(x,startY,x,endY,"tap".equals(d.action)?100:420))throw new IllegalStateException("제어 불가");log(prefs,"제스처 요청 수락");
     }
     Thread.sleep(1300);
    if(generation.get()==token){boolean changed=!screen.equals(ControlService.inspect());log(prefs,changed?"화면 텍스트 변화 감지":"화면 텍스트 변화 없음");if(!changed&&!"wait".equals(d.action))break;}
    }
    if(running.get()&&generation.get()==token&&prefs.getInt("step",0)>=limit) prefs.edit().putString("last_action",prefs.getString("last_action","")+" (실행 한도 도달)").apply();
   }catch(InterruptedException e){Thread.currentThread().interrupt();}
    catch(Exception e){if(generation.get()==token){String msg=e.getMessage()==null?"실행 오류":e.getMessage();prefs.edit().putString("last_error",msg).apply();log(prefs,"오류: "+msg);}}
    finally{if(generation.get()==token){running.set(false);ControlService.stop();log(prefs,"실행 종료");}}
  });
  return true;
 }
}
