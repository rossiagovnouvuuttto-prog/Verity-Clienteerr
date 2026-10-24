package dev.cfvision;
import android.accessibilityservice.*;
import android.graphics.Path;
import android.os.Handler;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.graphics.PointF;

public class AimAccessibility extends AccessibilityService {
  static volatile AimAccessibility active;
  private long last = 0;
  @Override public void onServiceConnected(){super.onServiceConnected();active=this;}
  @Override public void onAccessibilityEvent(AccessibilityEvent e){}
  @Override public void onInterrupt(){}
  @Override public void onDestroy(){if(active==this)active=null;super.onDestroy();}
  public synchronized void aim(float dx,float dy,int sw,int sh){
     long now=SystemClock.uptimeMillis();
     if(now-last<260 || Math.abs(dx)+Math.abs(dy)<5)return;
     last=now;
     float sx=sw*.78f,sy=sh*.68f;
     float stepX=Math.max(-90,Math.min(90,dx*.25f));
     float stepY=Math.max(-60,Math.min(60,dy*.25f));
     Path p=new Path();p.moveTo(sx,sy);p.lineTo(sx+stepX,sy+stepY);
     GestureDescription.StrokeDescription stroke=new GestureDescription.StrokeDescription(p,0,115);
     dispatchGesture(new GestureDescription.Builder().addStroke(stroke).build(),null,null);
  }
}