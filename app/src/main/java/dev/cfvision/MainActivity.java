package dev.cfvision;
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
  static final int CAPTURE=44;
  TextView state;
  int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
  void button(LinearLayout root,String s,View.OnClickListener click){
    Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(click);
    root.addView(b,new LinearLayout.LayoutParams(-1,dp(55)));
  }
  @Override public void onCreate(Bundle stateBundle){
    super.onCreate(stateBundle);
    ScrollView sc=new ScrollView(this);LinearLayout l=new LinearLayout(this);l.setOrientation(1);
    l.setPadding(dp(25),dp(12),dp(25),dp(12));l.setBackgroundColor(0xff091924);sc.addView(l);
    TextView title=new TextView(this);title.setText("CF VISION 1.0  |  AUTO-AIM + VISUAL ESP");
    title.setTextSize(22);title.setTextColor(0xff5bf8c9);l.addView(title);
    state=new TextView(this);state.setText("Отмечает людей, которых распознаёт модель на экране. Aim отправляет жесты, если включена служба специальных возможностей. Не видит сквозь стены; возможны ложные срабатывания.");
    state.setTextColor(Color.WHITE);state.setTextSize(15);state.setPadding(0,dp(7),0,dp(9));l.addView(state);
    button(l,"1 · Разрешить меню поверх игр",v->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));
    button(l,"2 · Разрешить жесты (Спец. возможности)",v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
    button(l,"3 · Запустить захват экрана и ESP",v->{
      if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"Сначала включи меню поверх приложений",Toast.LENGTH_LONG).show();return;}
      MediaProjectionManager p=(MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
      startActivityForResult(p.createScreenCaptureIntent(),CAPTURE);
    });
    button(l,"ОСТАНОВИТЬ",v->startService(new Intent(this,VisionService.class).setAction(VisionService.STOP)));
    setContentView(sc);
  }
  @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);
    if(request==CAPTURE&&result==RESULT_OK&&data!=null){
      Intent i=new Intent(this,VisionService.class);
      i.putExtra("rc",result);i.putExtra("capture",data);
      startForegroundService(i);state.setText("Активно: открой CounterFlame. Включай автонаведение только в плавающем меню.");
    }else if(request==CAPTURE)state.setText("Для работы ESP нужно подтвердить разрешение на захват экрана.");
  }
}