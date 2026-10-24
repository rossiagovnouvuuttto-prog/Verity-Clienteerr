package dev.cfaimguide;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.media.projection.*;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.view.*;
import android.widget.*;
import java.nio.ByteBuffer;

public class OverlayService extends Service {
    public static final String STOP="dev.cfaimguide.STOP";
    private WindowManager wm;private AimOverlay overlay;private LinearLayout panel;
    private MediaProjection proj;private ImageReader reader;private VirtualDisplay display;
    private Handler handler;private HandlerThread worker;private int sw,sh;
    private boolean showAim=true, showTarget=true;private volatile float targetX=-1,targetY=-1;
    private volatile boolean busy=false;private long lastScan=0;
    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public int onStartCommand(Intent i,int f,int start) {
        if(i!=null&&STOP.equals(i.getAction())){stopSelf();return START_NOT_STICKY;}
        if(proj!=null)return START_STICKY;
        int res=i==null?0:i.getIntExtra("result",0);Intent data=i==null?null:(Intent)i.getParcelableExtra("data");
        if(data==null){stopSelf();return START_NOT_STICKY;}
        String channel="cf_aimguide";NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(channel,"CF AimGuide",NotificationManager.IMPORTANCE_LOW));
        Notification n=new Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("CF AimGuide").setContentText("Нажми остановить в меню").build();
        if(Build.VERSION.SDK_INT>=29)startForeground(4,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);else startForeground(4,n);
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        android.util.DisplayMetrics m=new android.util.DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(m);
        sw=m.widthPixels;sh=m.heightPixels;
        overlay=new AimOverlay(this);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
              WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
              PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.START;wm.addView(overlay,p);
        panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(6),dp(6),dp(6),dp(6));panel.setBackgroundColor(0xdd14202c);
        Button aim=new Button(this);aim.setText("Прицел: ВКЛ");aim.setTextSize(11);aim.setAllCaps(false);
        aim.setOnClickListener(v->{showAim=!showAim;aim.setText("Прицел: "+(showAim?"ВКЛ":"ВЫКЛ"));overlay.invalidate();});panel.addView(aim);
        Button mark=new Button(this);mark.setText("Метка: ВКЛ");mark.setTextSize(11);mark.setAllCaps(false);
        mark.setOnClickListener(v->{showTarget=!showTarget;mark.setText("Метка: "+(showTarget?"ВКЛ":"ВЫКЛ"));overlay.invalidate();});panel.addView(mark);
        Button stop=new Button(this);stop.setText("× СТОП");stop.setTextSize(11);stop.setAllCaps(false);stop.setOnClickListener(v->stopSelf());panel.addView(stop);
        WindowManager.LayoutParams q=new WindowManager.LayoutParams(dp(122),-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
              WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        q.gravity=Gravity.TOP|Gravity.START;q.x=0;q.y=dp(70);wm.addView(panel,q);
        worker=new HandlerThread("cf-screen");worker.start();handler=new Handler(worker.getLooper());
        try {
          MediaProjectionManager mp=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
          proj=mp.getMediaProjection(res,data);
          proj.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){stopSelf();}},handler);
          int w=Math.max(2,sw/2),h=Math.max(2,sh/2);
          reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);
          reader.setOnImageAvailableListener(r->{
            Image im=null;
            try {
              im=r.acquireLatestImage();
              if(im==null)return;
              if(SystemClock.uptimeMillis()-lastScan<90)return;
              lastScan=SystemClock.uptimeMillis();
              Image.Plane pl=im.getPlanes()[0];ByteBuffer bb=pl.getBuffer();
              int stride=pl.getRowStride(),pixel=pl.getPixelStride(), iw=im.getWidth(),ih=im.getHeight();
              int bestScore=Integer.MAX_VALUE, tx=-1, ty=-1;
              int midx=iw/2,midy=ih/2;
              for(int y=10;y<ih-10;y+=7)for(int x=10;x<iw-10;x+=7){
                int pos=y*stride+x*pixel;
                if(pos+2>=bb.limit())continue;
                int r0=bb.get(pos)&255,g=bb.get(pos+1)&255,b=bb.get(pos+2)&255;
                if(r0>185 && r0>g*1.7 && r0>b*1.6 && g<145) {
                   int score=(x-midx)*(x-midx)+(y-midy)*(y-midy);
                   if(score<bestScore){bestScore=score;tx=x;ty=y;}
                }
              }
              if(tx>=0){targetX=tx*(sw/(float)iw);targetY=ty*(sh/(float)ih);} else {targetX=-1;targetY=-1;}
              if(overlay!=null)overlay.postInvalidate();
            }catch(Exception ignored){}finally{if(im!=null)im.close();}
          },handler);
          display=proj.createVirtualDisplay("cf-screen",w,h,m.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);
        }catch(Exception e){Toast.makeText(this,"Ошибка захвата: "+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show();stopSelf();}
        return START_NOT_STICKY;
    }
    @Override public void onDestroy(){
        if(panel!=null&&wm!=null){wm.removeView(panel);panel=null;}
        if(overlay!=null&&wm!=null){wm.removeView(overlay);overlay=null;}
        if(display!=null){display.release();display=null;}
        if(reader!=null){reader.close();reader=null;}
        if(proj!=null){MediaProjection x=proj;proj=null;x.stop();}
        if(worker!=null){worker.quitSafely();worker=null;}
        super.onDestroy();
    }
    final class AimOverlay extends View{
        Paint p=new Paint(3);AimOverlay(Context c){super(c);}
        @Override public void onDraw(Canvas c){
            float cx=getWidth()/2f,cy=getHeight()/2f;
            if(showAim){p.setColor(0xff59ffb7);p.setStrokeWidth(dp(2));c.drawCircle(cx,cy,dp(12),p);c.drawLine(cx-dp(20),cy,cx-dp(7),cy,p);c.drawLine(cx+dp(7),cy,cx+dp(20),cy,p);c.drawLine(cx,cy-dp(20),cx,cy-dp(7),p);c.drawLine(cx,cy+dp(7),cx,cy+dp(20),p);}
            if(showTarget&&targetX>=0){p.setColor(0xffffff54);p.setStrokeWidth(dp(2));p.setStyle(Paint.Style.STROKE);
              c.drawCircle(targetX,targetY,dp(25),p);p.setStyle(Paint.Style.FILL);
              c.drawLine(cx,cy,targetX,targetY,p);}
        }
    }
}
