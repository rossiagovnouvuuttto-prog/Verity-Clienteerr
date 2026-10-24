package dev.cfvision;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.*;
import android.view.*;
import android.widget.*;
import org.tensorflow.lite.Interpreter;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.util.*;

public class VisionService extends Service {
  public static final String STOP="dev.cfvision.stop";
  private WindowManager wm;
  private Guide guide;
  private LinearLayout panel;
  private MediaProjection projection;
  private VirtualDisplay mirror;
  private ImageReader reader;
  private HandlerThread thread;
  private Handler queue;
  private Interpreter net;
  private volatile ArrayList<RectF> people=new ArrayList<>();
  private volatile String status="Загрузка модели...";
  private boolean showEsp=true, aim=false;
  private int screenW,screenH;
  private long lastFrame=0;
  int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  @Override public android.os.IBinder onBind(Intent i){return null;}
  @Override public int onStartCommand(Intent i,int flags,int id){
    if(i!=null&&STOP.equals(i.getAction())){stopSelf();return START_NOT_STICKY;}
    if(projection!=null)return START_NOT_STICKY;
    Intent capture=i==null?null:(Intent)i.getParcelableExtra("capture");
    if(capture==null){stopSelf();return START_NOT_STICKY;}
    NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    nm.createNotificationChannel(new NotificationChannel("cfvision","CF Vision",NotificationManager.IMPORTANCE_LOW));
    startForeground(17,new Notification.Builder(this,"cfvision").setSmallIcon(android.R.drawable.ic_menu_view)
      .setContentTitle("CF Vision").setContentText("Анализ экрана активен; останови из меню").build(),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
    wm=(WindowManager)getSystemService(WINDOW_SERVICE);
    android.util.DisplayMetrics metrics=new android.util.DisplayMetrics();
    wm.getDefaultDisplay().getRealMetrics(metrics);
    screenW=metrics.widthPixels;screenH=metrics.heightPixels;
    guide=new Guide(this);
    WindowManager.LayoutParams op=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
    op.gravity=Gravity.TOP|Gravity.LEFT;wm.addView(guide,op);
    panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(3),dp(3),dp(3),dp(3));panel.setBackgroundColor(0xbb071b28);
    Button esp=make("ESP ВИДИМЫХ: ON");esp.setOnClickListener(v->{showEsp=!showEsp;esp.setText("ESP ВИДИМЫХ: "+(showEsp?"ON":"OFF"));guide.invalidate();});
    Button a=make("AIM: OFF");a.setOnClickListener(v->{
       if(AimAccessibility.active==null){Toast.makeText(this,"Включи CF Vision в специальных возможностях",Toast.LENGTH_LONG).show();return;}
       aim=!aim;a.setText("AIM: "+(aim?"ON":"OFF"));
    });
    Button stop=make("СТОП");stop.setOnClickListener(v->stopSelf());
    panel.addView(esp);panel.addView(a);panel.addView(stop);
    WindowManager.LayoutParams pp=new WindowManager.LayoutParams(dp(146),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
    pp.gravity=Gravity.TOP|Gravity.LEFT;pp.x=dp(10);pp.y=dp(26);wm.addView(panel,pp);
    thread=new HandlerThread("cf-vision-ml");thread.start();queue=new Handler(thread.getLooper());
    try{
      net=new Interpreter(loadModel(),new Interpreter.Options().setNumThreads(3));
      final int[] shape=net.getInputTensor(0).shape();
      if(shape.length!=4||shape[1]!=640||shape[2]!=640||shape[3]!=3)throw new IOException("Неверная форма входа модели");
      status="Обнаружение: активно";
      MediaProjectionManager pm=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
      projection=pm.getMediaProjection(i.getIntExtra("rc",0),capture);
      projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){stopSelf();}},queue);
      int width=Math.max(2,screenW/2),height=Math.max(2,screenH/2);
      reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,3);
      reader.setOnImageAvailableListener(r->{
         Image frame=null;
         try{
           frame=r.acquireLatestImage();
           if(frame==null||net==null)return;
           if(SystemClock.uptimeMillis()-lastFrame<160)return;
           lastFrame=SystemClock.uptimeMillis();
           analyse(frame);
         }catch(Throwable ex){status="Ошибка распознавания: "+ex.getClass().getSimpleName();}
         finally{if(frame!=null)frame.close();}
      },queue);
      mirror=projection.createVirtualDisplay("CF Vision",width,height,metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,queue);
    }catch(Exception ex){status="Ошибка: "+ex.getMessage();Toast.makeText(this,status,Toast.LENGTH_LONG).show();stopSelf();}
    return START_NOT_STICKY;
  }
  private Button make(String text){Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextSize(11);b.setMinHeight(0);b.setMinimumHeight(0);return b;}
  private java.nio.MappedByteBuffer loadModel() throws IOException{
    File target=new File(getFilesDir(),"yolov8n.tflite");
    if(!target.isFile()||target.length()<1000000){
      try(InputStream src=getAssets().open("yolov8n.tflite");FileOutputStream dst=new FileOutputStream(target)){
        byte[] buf=new byte[16384];int n;while((n=src.read(buf))>0)dst.write(buf,0,n);
      }
    }
    try(FileInputStream in=new FileInputStream(target)){
      return in.getChannel().map(FileChannel.MapMode.READ_ONLY,0,target.length());
    }
  }
  private void analyse(Image frame){
    Image.Plane pl=frame.getPlanes()[0];
    int rowPixels=pl.getRowStride()/pl.getPixelStride();
    Bitmap full=Bitmap.createBitmap(rowPixels,frame.getHeight(),Bitmap.Config.ARGB_8888);
    ByteBuffer original=pl.getBuffer();original.rewind();
    full.copyPixelsFromBuffer(original);
    Bitmap cropped=Bitmap.createBitmap(full,0,0,frame.getWidth(),frame.getHeight());
    Bitmap input=Bitmap.createScaledBitmap(cropped,640,640,true);
    full.recycle();cropped.recycle();
    int[] pix=new int[640*640];input.getPixels(pix,0,640,0,0,640,640);input.recycle();
    ByteBuffer pixels=ByteBuffer.allocateDirect(640*640*3*4).order(ByteOrder.nativeOrder());
    for(int c:pix){pixels.putFloat(Color.red(c)/255f);pixels.putFloat(Color.green(c)/255f);pixels.putFloat(Color.blue(c)/255f);}
    pixels.rewind();
    int[] size=net.getOutputTensor(0).shape();
    if(size.length!=3){status="Неизвестный формат выходной модели";return;}
    float[][][] out=new float[size[0]][size[1]][size[2]];
    net.run(pixels,out);
    boolean transposed=size[1]>size[2]; // [1,8400,84] vs [1,84,8400]
    int count=transposed?size[1]:size[2];
    int channels=transposed?size[2]:size[1];
    if(channels<5){status="Неверный выход модели";return;}
    ArrayList<Detected> candidates=new ArrayList<>();
    for(int k=0;k<count;k++){
      float conf=transposed?out[0][k][4]:out[0][4][k];
      if(conf<.48f)continue;
      float cx=transposed?out[0][k][0]:out[0][0][k];
      float cy=transposed?out[0][k][1]:out[0][1][k];
      float w=transposed?out[0][k][2]:out[0][2][k];
      float h=transposed?out[0][k][3]:out[0][3][k];
      if(Math.max(cx,cy)<2){cx*=640;cy*=640;w*=640;h*=640;}
      if(w<12||h<18||w>550||h>625)continue;
      RectF rect=new RectF((cx-w*.5f)/640f*screenW,(cy-h*.5f)/640f*screenH,
          (cx+w*.5f)/640f*screenW,(cy+h*.5f)/640f*screenH);
      if(rect.width()<12||rect.height()<16)continue;
      candidates.add(new Detected(rect,conf));
    }
    candidates.sort((x,y)->Float.compare(y.conf,x.conf));
    ArrayList<RectF> chosen=new ArrayList<>();
    for(Detected item:candidates){
      boolean overlap=false;
      for(RectF b:chosen)if(iou(b,item.box)>.47f){overlap=true;break;}
      if(!overlap){chosen.add(item.box);if(chosen.size()>=8)break;}
    }
    people=chosen;
    status="Объектов: "+people.size();
    if(guide!=null)guide.postInvalidate();
    if(aim&&AimAccessibility.active!=null&&!chosen.isEmpty()){
      RectF closest=null;float best=Float.MAX_VALUE;
      for(RectF rect:chosen){
        float x=rect.centerX(),y=rect.top+rect.height()*.24f;
        float dx=x-screenW*.5f,dy=y-screenH*.5f;
        float dist=dx*dx+dy*dy;
        if(dist<best){best=dist;closest=rect;}
      }
      if(closest!=null){float dx=closest.centerX()-screenW*.5f;
          float dy=closest.top+closest.height()*.24f-screenH*.5f;
          AimAccessibility.active.aim(dx,dy,screenW,screenH);}
    }
  }
  static class Detected {RectF box;float conf;Detected(RectF b,float s){box=b;conf=s;}}
  static float iou(RectF a,RectF b){
    float left=Math.max(a.left,b.left),top=Math.max(a.top,b.top),right=Math.min(a.right,b.right),bottom=Math.min(a.bottom,b.bottom);
    float area=Math.max(0,right-left)*Math.max(0,bottom-top);
    return area/(a.width()*a.height()+b.width()*b.height()-area+1);
  }
  @Override public void onDestroy(){
    aim=false;
    if(panel!=null){wm.removeView(panel);panel=null;}
    if(guide!=null){wm.removeView(guide);guide=null;}
    if(reader!=null){reader.close();reader=null;}
    if(mirror!=null){mirror.release();mirror=null;}
    if(projection!=null){MediaProjection p=projection;projection=null;p.stop();}
    if(net!=null){net.close();net=null;}
    if(thread!=null){thread.quitSafely();thread=null;}
    super.onDestroy();
  }
  class Guide extends View {
    Paint brush=new Paint(3);
    Guide(Context c){super(c);}
    @Override protected void onDraw(Canvas canvas){
      float x=getWidth()*.5f,y=getHeight()*.5f;
      brush.setStrokeWidth(dp(2));brush.setColor(0xff57edb6);brush.setStyle(Paint.Style.STROKE);
      canvas.drawLine(x-dp(12),y,x-dp(4),y,brush);canvas.drawLine(x+dp(4),y,x+dp(12),y,brush);
      canvas.drawLine(x,y-dp(12),x,y-dp(4),brush);canvas.drawLine(x,y+dp(4),x,y+dp(12),brush);
      if(showEsp){
        brush.setColor(0xffffd955);
        for(RectF r:people){canvas.drawRect(r,brush);canvas.drawCircle(r.centerX(),r.top+r.height()*.24f,dp(4),brush);}
      }
      brush.setStyle(Paint.Style.FILL);brush.setTextSize(dp(12));brush.setColor(0xffffffff);
      canvas.drawText(status,dp(15),getHeight()-dp(18),brush);
    }
  }
}