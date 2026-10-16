package android.widget;
public final class EditText extends TextView {
 private final StringBuilder value=new StringBuilder();
 public String error,hint,accessibleLabel; public boolean single;
 public EditText(Object owner){super(owner);}
 public void setSingleLine(boolean value){single=value;}
 public void setHintTextColor(int color){}
 public void setHint(String value){hint=value;}
 public void setContentDescription(String value){accessibleLabel=value;}
 public void setFilters(android.text.InputFilter[] filters){}
 public StringBuilder getText(){return value;}
 public void setError(String value){error=value;}
 @Override public void setText(String text){value.setLength(0);value.append(text);super.setText(text);}
}
