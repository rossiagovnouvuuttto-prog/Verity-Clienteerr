package dev.cfaimguide;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

public class MainActivity extends Activity {
    static final int CAPTURE = 101;
    TextView label;
    int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    void btn(LinearLayout root,String t,View.OnClickListener f){
        Button b=new Button(this);b.setText(t);b.setAllCaps(false);b.setOnClickListener(f);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(56));p.bottomMargin=dp(10);root.addView(b,p);
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sc=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);
        root.setPadding(dp(20),dp(30),dp(20),dp(30));root.setBackgroundColor(0xff0d1925);sc.addView(root);
        TextView title=new TextView(this);title.setText("CF AIMGUIDE 0.3");
        title.setTextColor(0xff55efb6);title.setTextSize(28);root.addView(title);
        label=new TextView(this);label.setText("Прицел и подсветка цветной видимой цели. Не управляет игрой, не распознаёт игроков за стенами.");
        label.setTextColor(Color.WHITE);label.setTextSize(16);label.setPadding(0,dp(16),0,dp(18));root.addView(label);
        btn(root,"1. Разрешить поверх приложений",v->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName()))));
        btn(root,"2. Запустить помощник",v->{
            if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"Разреши показ поверх других приложений",Toast.LENGTH_LONG).show();return;}
            MediaProjectionManager m=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
            startActivityForResult(m.createScreenCaptureIntent(),CAPTURE);
        });
        btn(root,"3. Открыть тренировку",v->startActivity(new Intent(this,TrainingActivity.class)));
        btn(root,"Остановить помощник",v->startService(new Intent(this,OverlayService.class).setAction(OverlayService.STOP)));
        setContentView(sc);
    }
    @Override protected void onActivityResult(int req,int result,Intent data){
        super.onActivityResult(req,result,data);
        if(req==CAPTURE&&result==RESULT_OK&&data!=null){
            Intent svc=new Intent(this,OverlayService.class);
            svc.putExtra("result",result);svc.putExtra("data",data);
            if(Build.VERSION.SDK_INT>=26) startForegroundService(svc); else startService(svc);
            label.setText("Помощник запущен. Перейди в тренировку или другую программу.");
        } else if(req==CAPTURE) label.setText("Разрешение на захват экрана не получено.");
    }
}
