package com.mobileuse.galaxy;
import org.json.*;import java.net.*;import java.io.*;import java.nio.charset.StandardCharsets;import android.util.Base64;
public final class OpenAiClient {
 public static final class Decision {public final String action;public final double x,y;public final String note;Decision(String a,double x,double y,String n){action=a;this.x=x;this.y=y;note=n;}}
 public static Decision decide(String apiKey,String task,String screen,byte[] jpeg,boolean imageAllowed)throws Exception{
  if(!ActionPolicy.safeTask(task)||ActionPolicy.sensitive(screen))throw new SecurityException("민감한 화면/작업이 감지되어 중단했습니다");
  if(apiKey==null||apiKey.isBlank())throw new IllegalArgumentException("API 키 필요");
  JSONObject body=new JSONObject().put("model","gpt-4.1-mini").put("max_output_tokens",180);
  JSONArray content=new JSONArray().put(new JSONObject().put("type","input_text").put("text","You are a mobile UI navigation assistant. Return ONLY JSON {\"action\":\"tap|scroll_up|scroll_down|wait|done\",\"x\":0.5,\"y\":0.5,\"note\":\"short\"}. Coordinates normalized 0..1. Never handle credentials, payments, transfers, purchases, private messages, destructive actions or permission prompts. If uncertain return wait. Task: "+task+"\nVisible accessibility text (may be incomplete):\n"+screen));
  if(imageAllowed&&jpeg!=null)content.put(new JSONObject().put("type","input_image").put("image_url","data:image/jpeg;base64,"+Base64.encodeToString(jpeg,Base64.NO_WRAP)));
  body.put("input",new JSONArray().put(new JSONObject().put("role","user").put("content",content)));
  HttpURLConnection c=(HttpURLConnection)new URL("https://api.openai.com/v1/responses").openConnection();c.setRequestMethod("POST");c.setConnectTimeout(12000);c.setReadTimeout(25000);c.setDoOutput(true);c.setRequestProperty("Authorization","Bearer "+apiKey);c.setRequestProperty("Content-Type","application/json");
  try{byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream out=c.getOutputStream()){out.write(bytes);}int status=c.getResponseCode();if(status!=200)throw new IOException("API HTTP "+status);String response;try(InputStream in=c.getInputStream()){response=new String(in.readAllBytes(),StandardCharsets.UTF_8);}JSONObject obj=new JSONObject(response);String text="";JSONArray outputs=obj.optJSONArray("output");if(outputs!=null)for(int i=0;i<outputs.length();i++){JSONArray parts=outputs.getJSONObject(i).optJSONArray("content");if(parts!=null)for(int j=0;j<parts.length();j++)if("output_text".equals(parts.getJSONObject(j).optString("type")))text+=parts.getJSONObject(j).optString("text");}
   int a=text.indexOf('{'),b=text.lastIndexOf('}');if(a<0||b<=a)throw new IOException("모델 응답 형식 오류");JSONObject d=new JSONObject(text.substring(a,b+1));String act=d.optString("action","wait");double x=d.optDouble("x",.5),y=d.optDouble("y",.5);if(!ActionPolicy.valid(act,x,y))throw new SecurityException("허용되지 않은 동작");return new Decision(act,x,y,d.optString("note",""));
  }finally{c.disconnect();}
 }
}
