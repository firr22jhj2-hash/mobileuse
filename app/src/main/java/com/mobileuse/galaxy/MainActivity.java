package com.mobileuse.galaxy;
import android.app.*;import android.content.*;import android.media.projection.*;import android.os.*;import android.provider.Settings;import android.graphics.Color;import android.widget.*;
public final class MainActivity extends Activity {
 private static final int CAPTURE_REQUEST=140;private final Handler statusHandler=new Handler(Looper.getMainLooper());private final Runnable statusTicker=new Runnable(){public void run(){if(status!=null)refresh();statusHandler.postDelayed(this,800);}};private TextView status;private EditText task,key;private CheckBox sendImages;private android.content.SharedPreferences prefs;
 @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("local_progress",MODE_PRIVATE);ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(28,30,28,28);scroll.addView(root);
 TextView title=new TextView(this);title.setText("Mobile Use · Z Fold8 v0.4");title.setTextSize(23);root.addView(title);status=new TextView(this);root.addView(status);
 button(root,"1. 접근성 서비스 켜기",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
 button(root,"2. 화면 캡처 승인",()->{MediaProjectionManager mgr=getSystemService(MediaProjectionManager.class);startActivityForResult(mgr.createScreenCaptureIntent(),CAPTURE_REQUEST);});
 task=new EditText(this);task.setHint("작업 목표 (로그인·결제·송금 금지)");task.setText(prefs.getString("task",""));root.addView(task);
 key=new EditText(this);key.setHint("OpenAI API 키 (메모리에만 유지)");key.setInputType(129);root.addView(key);
 sendImages=new CheckBox(this);sendImages.setText("화면 이미지를 OpenAI로 전송 (개인정보 포함 가능, 명시적 동의)");sendImages.setChecked(false);root.addView(sendImages);
 button(root,"3. AI 작업 시작 (최대 30단계)",()->{String t=task.getText().toString();if(!ActionPolicy.safeTask(t)){status.setText("민감하거나 유효하지 않은 작업");return;}prefs.edit().putString("task",t).putInt("step",0).remove("last_error").apply();if(key.getText().toString().trim().isEmpty()){status.setText("OpenAI API 키가 비어 있습니다");return;}boolean ok=TaskRunner.start(this,key.getText().toString(),t,sendImages.isChecked());status.setText(ok?"실행 중. 알림의 긴급 정지로 언제든 중단 가능":"접근성 및 캡처 권한 확인 필요");});
 button(root,"현재 화면 접근성 텍스트 확인",()->{String v=ControlService.inspect();status.setText(v.isEmpty()?"현재 화면에서 읽을 수 있는 텍스트가 없습니다":v);});
 Button stop=new Button(this);stop.setText("긴급 정지");stop.setTextColor(Color.WHITE);stop.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.RED));stop.setOnClickListener(v->{TaskRunner.stop();startService(new Intent(this,CaptureService.class).setAction(CaptureService.ACTION_STOP_ALL));refresh();});root.addView(stop);
 TextView note=new TextView(this);note.setText("주의: 접근성 텍스트에도 개인정보가 포함될 수 있습니다. 화면 이미지 전송은 기본 꺼짐이며, 키는 저장하지 않습니다. 화면 분석 중 금융·로그인·인증 화면이 감지되면 중단합니다. 긴급 정지는 캡처 알림에서도 실행할 수 있습니다. v0.4는 실기기 미검증 개발 빌드입니다.");root.addView(note);setContentView(scroll);refresh();statusHandler.postDelayed(statusTicker,800);}
 private void button(LinearLayout root,String label,Runnable r){Button b=new Button(this);b.setText(label);b.setOnClickListener(v->r.run());root.addView(b);}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==CAPTURE_REQUEST&&result==RESULT_OK&&data!=null){Intent i=new Intent(this,CaptureService.class).setAction(CaptureService.START).putExtra("resultCode",result).putExtra("data",data);startForegroundService(i);status.setText("화면 캡처 승인됨");}}
 private void refresh(){status.setText("접근성: "+(ControlService.active==null?"꺼짐":"켜짐")+" | 캡처: "+(CaptureService.instance==null?"꺼짐":"서비스 실행")+" | AI: "+(TaskRunner.isRunning()?"실행 중":"대기")+" | 단계: "+prefs.getInt("step",0)+" | 마지막 행동: "+prefs.getString("last_action","없음")+" | 오류: "+prefs.getString("last_error","없음"));}
 @Override protected void onDestroy(){statusHandler.removeCallbacks(statusTicker);super.onDestroy();}
 @Override protected void onResume(){super.onResume();if(status!=null)refresh();}
}
