package com.mobileuse.galaxy;
import android.app.Activity;import android.os.Bundle;import android.widget.*;
public final class SandboxActivity extends Activity {
 static volatile boolean visible;
 @Override public void onCreate(Bundle b){super.onCreate(b);ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);scroll.addView(root);TextView title=new TextView(this);title.setText("공개 예제 기업 목록 (가상 데이터)");root.addView(title);for(int i=1;i<=30;i++){final int n=i;Button row=new Button(this);row.setText("예제 기업 "+i);row.setOnClickListener(v->row.setText("예제 기업 "+n+" 선택됨"));root.addView(row);}setContentView(scroll);}
 @Override protected void onResume(){super.onResume();visible=true;}
 @Override protected void onPause(){visible=false;TaskRunner.stop();super.onPause();}
}
