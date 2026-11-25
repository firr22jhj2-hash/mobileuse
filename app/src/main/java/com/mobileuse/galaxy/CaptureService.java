package com.mobileuse.galaxy;
import android.app.*;import android.content.*;import android.graphics.*;import android.hardware.display.*;import android.media.*;import android.media.projection.*;import android.os.*;import java.io.*;
public final class CaptureService extends Service {
 public static final String START="capture.start", STOP="capture.stop", ACTION_STOP_ALL="capture.stop.all";
 private static final int ID=4021; private static final String CHANNEL="mobile_use_control";
 static volatile CaptureService instance;
 private MediaProjection projection; private VirtualDisplay display; private ImageReader reader;
 private final Object lock=new Object(); private volatile boolean running;
 @Override public void onCreate(){super.onCreate();instance=this;NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Mobile Use controls",NotificationManager.IMPORTANCE_HIGH));}
 private Notification notification(){Intent stop=new Intent(this,CaptureService.class).setAction(ACTION_STOP_ALL);PendingIntent pi=PendingIntent.getService(this,0,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel).setContentTitle("Mobile Use 화면 공유 중").setContentText("중지하려면 여기를 누르세요").setOngoing(true).addAction(new Notification.Action.Builder(null,"긴급 정지",pi).build()).build();}
 @Override public int onStartCommand(Intent intent,int flags,int startId){String action=intent==null?"":intent.getAction();if(ACTION_STOP_ALL.equals(action)||STOP.equals(action)){TaskRunner.stop();ControlService.stop();stopCapture();stopSelf();return START_NOT_STICKY;}
 if(!START.equals(action))return START_NOT_STICKY;
 int code=intent.getIntExtra("resultCode",Activity.RESULT_CANCELED);Intent data=intent.getParcelableExtra("data",Intent.class);if(code!=Activity.RESULT_OK||data==null){stopSelf();return START_NOT_STICKY;}
 startForeground(ID,notification(),android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
 try{MediaProjectionManager mgr=getSystemService(MediaProjectionManager.class);projection=mgr.getMediaProjection(code,data);projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){TaskRunner.stop();ControlService.stop();stopCapture();stopSelf();}},new Handler(Looper.getMainLooper()));
 android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();int w=metrics.widthPixels,h=metrics.heightPixels;reader=ImageReader.newInstance(w,h,android.graphics.PixelFormat.RGBA_8888,2);display=projection.createVirtualDisplay("MobileUse",w,h,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,null);running=true;
 }catch(Exception e){android.util.Log.e("MobileUse","Capture start failed",e);stopCapture();stopSelf();}return START_NOT_STICKY;}
 public byte[] frame(){synchronized(lock){if(!running||reader==null)return null;Image image=reader.acquireLatestImage();if(image==null)return null;try{Image.Plane plane=image.getPlanes()[0];int w=image.getWidth(),h=image.getHeight();int stride=plane.getRowStride(),pixel=plane.getPixelStride();java.nio.ByteBuffer buf=plane.getBuffer();Bitmap full=Bitmap.createBitmap(w+Math.max(0,(stride-w*pixel)/pixel),h,Bitmap.Config.ARGB_8888);full.copyPixelsFromBuffer(buf);Bitmap crop=Bitmap.createBitmap(full,0,0,w,h);full.recycle();int sw=Math.min(w,900),sh=Math.max(1,(int)((long)h*sw/w));Bitmap small=Bitmap.createScaledBitmap(crop,sw,sh,true);crop.recycle();ByteArrayOutputStream out=new ByteArrayOutputStream();small.compress(Bitmap.CompressFormat.JPEG,65,out);small.recycle();return out.toByteArray();}finally{image.close();}}}
 private void stopCapture(){synchronized(lock){running=false;if(display!=null){display.release();display=null;}if(reader!=null){reader.close();reader=null;}if(projection!=null){MediaProjection p=projection;projection=null;p.stop();}}}
 @Override public void onDestroy(){stopCapture();instance=null;super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
