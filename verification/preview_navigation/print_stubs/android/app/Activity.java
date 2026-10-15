package android.app;
public class Activity { public boolean finishing,destroyed; public Object service; public boolean isFinishing(){return finishing;} public boolean isDestroyed(){return destroyed;} public Object getSystemService(String name){return service;} protected void onDestroy(){destroyed=true;} }

