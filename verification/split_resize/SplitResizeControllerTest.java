package com.example.markdowndesk;
import java.lang.reflect.Method;
public final class SplitResizeControllerTest {
 static int checks; static Method method;
 static void near(float input,int direction,float expected) throws Exception {checks++;float actual=(Float)method.invoke(null,input,direction);if(Math.abs(actual-expected)>.00001f)throw new AssertionError("ratio "+input+", direction "+direction+": "+actual+" != "+expected);}
 public static void main(String[] args) throws Exception {
  try {method=Class.forName("com.example.markdowndesk.SplitResizeController").getDeclaredMethod("adjust",float.class,int.class);method.setAccessible(true);} catch(ReflectiveOperationException e){throw new AssertionError("Missing bounded Split adjustment operation",e);}
  near(.5f,1,.55f);near(.5f,-1,.45f);near(.71f,1,.72f);near(.29f,-1,.28f);near(.72f,1,.72f);near(.28f,-1,.28f);near(.72f,0,.5f);near(.28f,0,.5f);
  System.out.println("Split controller: "+checks+" assertions PASS");
 }
}
