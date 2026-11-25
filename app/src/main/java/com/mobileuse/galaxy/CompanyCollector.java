package com.mobileuse.galaxy;
import android.app.Activity;
import android.content.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CompanyCollector {
 private static final ExecutorService worker=Executors.newSingleThreadExecutor();
 private static final AtomicBoolean running=new AtomicBoolean();
 private static volatile HttpURLConnection connection;
 private static volatile boolean stopping;
 private static volatile String status="대기";
 public static String status(){return status;}
 public static boolean isRunning(){return running.get();}
 public static void stop(){stopping=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
 private static JSONObject request(String key,String endpoint,String query)throws Exception{
  if(stopping||!running.get())throw new InterruptedException();
  HttpURLConnection c=(HttpURLConnection)new URL("https://opendart.fss.or.kr/api/"+endpoint+".json?crtfc_key="+URLEncoder.encode(key,"UTF-8")+"&"+query).openConnection();
  connection=c;c.setConnectTimeout(8000);c.setReadTimeout(12000);c.setInstanceFollowRedirects(false);
  try{if(c.getResponseCode()!=200)throw new IOException("DART HTTP 오류");try(InputStream in=c.getInputStream()){return new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}}finally{connection=null;c.disconnect();}
 }
 private static String field(JSONObject j,String k){return j.optString(k,"").trim();}
 private static String csv(String s){if(s==null)s="";if(s.matches("^[=+@-].*"))s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
 private static String row(String... values){StringJoiner j=new StringJoiner(",");for(String v:values)j.add(csv(v));return j.toString()+"\r\n";}
 private static String employees(JSONObject j){if(!"000".equals(j.optString("status")))return "";JSONArray a=j.optJSONArray("list");if(a==null)return "";long total=0;boolean found=false;for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r==null)continue;String n=field(r,"sm").replace(",","");if(n.matches("[0-9]+")){total+=Long.parseLong(n);found=true;}}return found?Long.toString(total):"";}
 private static String revenue(JSONObject j){if(!"000".equals(j.optString("status")))return "";JSONArray a=j.optJSONArray("list");if(a==null)return "";for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r==null)continue;String id=field(r,"account_id"),name=field(r,"account_nm"),sj=field(r,"sj_div");if(("IS".equals(sj)||"CIS".equals(sj))&&("ifrs-full_Revenue".equals(id)||"매출액".equals(name)||"수익(매출액)".equals(name))){String v=field(r,"thstrm_amount").replace(",","");if(v.matches("-?[0-9]+"))return v;}}return "";}
 public static synchronized boolean start(Context context,String key,String codes,String year){
  if(running.get()||key==null||!key.matches("[a-fA-F0-9]{40}")||!year.matches("20[0-9]{2}"))return false;
  LinkedHashSet<String> set=new LinkedHashSet<>();for(String c:codes.split("[,\\s]+")){if(!c.matches("[0-9]{8}"))return false;set.add(c);}if(set.isEmpty()||set.size()>30)return false;
  stopping=false;running.set(true);Context app=context.getApplicationContext();status="수집 시작";
  worker.submit(()->{File file=new File(app.getFilesDir(),"companies.csv");android.content.SharedPreferences checkpoint=app.getSharedPreferences("company_checkpoint",Context.MODE_PRIVATE);String batch=year+":"+String.join(",",set);boolean resume=batch.equals(checkpoint.getString("batch",""))&&file.exists();int saved=resume?checkpoint.getInt("count",0):0;if(!resume)checkpoint.edit().putString("batch",batch).putInt("count",0).commit();try(Writer out=new OutputStreamWriter(new FileOutputStream(file,resume),StandardCharsets.UTF_8)){
   if(!resume){out.write("\uFEFF");out.write(row("기업코드","기업명","본점주소","업종코드","종업원수","매출액","통화","기준연도","재무구분","출처","수집상태"));out.flush();}int count=saved;int index=0;
   for(String code:set){if(index++<saved)continue;if(stopping||!running.get())break;status="수집 중 "+(count+1)+"/"+set.size();String name="",addr="",industry="",emp="",sales="",basis="",currency="",result="";try{
    String q="corp_code="+code;JSONObject co=request(key,"company",q);if(!"000".equals(co.optString("status")))throw new IOException("기업조회 상태 "+co.optString("status"));name=field(co,"corp_name");addr=field(co,"adres");industry=field(co,"induty_code");
    String annual=q+"&bsns_year="+year+"&reprt_code=11011";emp=employees(request(key,"empSttus",annual));
    JSONObject fin=request(key,"fnlttSinglAcntAll",annual+"&fs_div=CFS");sales=revenue(fin);if(!sales.isEmpty()){basis="CFS";}else{fin=request(key,"fnlttSinglAcntAll",annual+"&fs_div=OFS");sales=revenue(fin);if(!sales.isEmpty())basis="OFS";}
    if(!sales.isEmpty()){JSONArray list=fin.optJSONArray("list");if(list!=null&&list.length()>0)currency=field(list.getJSONObject(0),"currency");}
    result=name.isEmpty()||addr.isEmpty()||industry.isEmpty()||emp.isEmpty()||sales.isEmpty()?"일부 누락":"완료";
   }catch(Exception e){result="조회 실패 또는 중지";}if(stopping||!running.get())break;
   out.write(row(code,name,addr,industry,emp,sales,currency,year,basis,"https://opendart.fss.or.kr/",result));out.flush();count++;checkpoint.edit().putInt("count",count).commit();Thread.sleep(350);}
   status=(!stopping?"완료 ":"중지 ")+count+"건 저장";
  }catch(Exception e){status="수집 오류";}finally{running.set(false);}});return true;
 }
 public static void export(Activity a,android.net.Uri uri)throws IOException{try(InputStream in=new FileInputStream(new File(a.getFilesDir(),"companies.csv"));OutputStream out=a.getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException("저장 실패");byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}
}
