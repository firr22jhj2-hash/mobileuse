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
  if(!LocalEngine.isReady()||!ActionPolicy.safeTask(task)||ControlService.active==null||!running.compareAndSet(false,true))return false;
  final int token=generation.incrementAndGet();
  final int limit=continuous?MAX_STEPS:1;
  final Context app=context.getApplicationContext();
  final android.content.SharedPreferences prefs=app.getSharedPreferences("local_progress",Context.MODE_PRIVATE);
  prefs.edit().remove("last_error").putInt("step",0).putString("last_action","준비 중").putString("live_log","시작: 작업 준비").apply();
  watchdog.postDelayed(()->{if(generation.get()==token&&running.get()){prefs.edit().putString("last_error","전체 실행 시간 120초 초과로 자동 중지").apply();log(prefs,"시간 제한으로 중지");stop();}},MAX_DURATION_MS);
  ControlService.arm();
  job=pool.submit(()->{
   try{
    Thread.sleep(500);
    java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("예제\\s*기업\\s*(\\d+)\\s*번?").matcher(task);
    String target=matcher.find()?"예제 기업 "+matcher.group(1):"";
    int failures=0;boolean verified=false;
    for(int step=0;step<limit&&running.get()&&generation.get()==token;step++){
     if(!ControlService.isPublicTestScreen())throw new SecurityException("내장 테스트 또는 허용된 설정 화면을 열어주세요");
     ControlService.NodeSnapshot snap=ControlService.snapshot();String screen=snap.text.toString();
     if((screen.isEmpty()&&SandboxActivity.visible)||ActionPolicy.sensitive(screen))throw new SecurityException("유효한 노드 없음");
     if(!target.isEmpty()&&snap.labels.contains(target+" 선택됨")){verified=true;break;}
     log(prefs,"노드 "+snap.nodes.size()+"개 · 로컬 판단 요청");
     LocalEngine.NodeDecision d;
     try{d=LocalEngine.decide(app,task,screen.isEmpty()?ControlService.inspect():screen);}catch(Exception e){if(++failures>=2||!continuous)throw e;log(prefs,"응답 오류 재시도");continue;}
     if(!running.get()||generation.get()!=token)break;
     prefs.edit().putInt("step",step+1).putString("last_action",d.getAction()).apply();
     String action=d.getAction();int id=d.getNodeId();
     if("done".equals(action)){log(prefs,"모델 종료 요청");break;}
     if(!ControlService.isPublicTestScreen()||!screen.equals(ControlService.snapshot().text.toString())){if(++failures>=2)throw new IllegalStateException("화면 변경 반복");continue;}
     boolean ok=false;
     if("tap".equals(action)){
      if(!target.isEmpty()){int exact=snap.labels.indexOf(target);if(exact>=0){id=exact+1;}else{id=0;}}
      if(id>0&&id<=snap.labels.size()&&!snap.labels.get(id-1).endsWith(" 선택됨")){
       String label=snap.labels.get(id-1);
       ok=ControlService.clickNode(snap,id);
       Thread.sleep(500);
       if(!running.get()||generation.get()!=token)break;
       ok=ok&&ControlService.snapshot().labels.contains(label+" 선택됨");
       if(ok){log(prefs,"선택 검증: "+label);if(!target.isEmpty()&&label.equals(target)){verified=true;break;}}
      }
     }else if("scroll_down".equals(action)||"scroll_up".equals(action)){
      String prior=ControlService.inspect();ok=ControlService.scrollNode("scroll_down".equals(action));Thread.sleep(500);
      ok=ok&&!prior.equals(ControlService.inspect());
     }
     if(!ok){log(prefs,"행동 미검증 · 재판단");if(++failures>=2||!continuous)break;}else failures=0;
    }
    if(generation.get()==token&&running.get()){
     prefs.edit().putString("last_action",verified?"목표 검증 성공":"종료 · 목표 미검증").apply();
     log(prefs,verified?"목표 달성 확인":"목표 달성 미확인");
    }
   }catch(InterruptedException e){Thread.currentThread().interrupt();}
    catch(Exception e){if(generation.get()==token){String msg=e.getMessage()==null?"실행 오류":e.getMessage();prefs.edit().putString("last_error",msg).apply();log(prefs,"오류: "+msg);}}
    finally{if(generation.get()==token){running.set(false);ControlService.stop();log(prefs,"실행 종료");}}
  });
  return true;
 }
}
