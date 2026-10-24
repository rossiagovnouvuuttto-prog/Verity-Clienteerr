package dev.cfaimguide;
import android.app.*;import android.os.*;import android.view.*;import android.graphics.*;import android.content.*;
public class TrainingActivity extends Activity {
  @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(new Arena(this));}
  class Arena extends View {
    Paint p=new Paint(3);long start=System.currentTimeMillis();
    Arena(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    @Override public void onDraw(Canvas c){
      c.drawColor(0xff0c1626);
      p.setColor(0xff375166);
      for(int i=1;i<10;i++){c.drawLine(getWidth()*i/10f,0,getWidth()*i/10f,getHeight(),p);c.drawLine(0,getHeight()*i/10f,getWidth(),getHeight()*i/10f,p);}
      float t=(System.currentTimeMillis()-start)*.001f;
      float x=getWidth()*.5f+(float)Math.sin(t*.9f)*getWidth()*.35f;
      float y=getHeight()*.45f+(float)Math.cos(t*.67f)*getHeight()*.25f;
      p.setColor(0xffff212c);c.drawCircle(x,y,Math.max(24,getWidth()/18),p);
      p.setColor(Color.WHITE);p.setTextSize(38);c.drawText("ТРЕНИРОВКА",25,65,p);
      p.setTextSize(20);c.drawText("Красная цель двигается",25,100,p);
      postInvalidateDelayed(30);
    }
  }
}
