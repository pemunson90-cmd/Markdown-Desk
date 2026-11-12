package com.example.markdowndesk;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.widget.Button;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/** Accessible result labels shared by the archive library and search surfaces. */
final class ArchiveResultRow {
    private ArchiveResultRow(){}

    static String date(String value){
        if(value==null||value.isEmpty())return "";
        try{
            LocalDate day=value.length()==10?LocalDate.parse(value):
                    OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate();
            return day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM));
        }catch(java.time.DateTimeException invalid){return "";}
    }

    static void bind(Button row,String title,String metadata,ArchiveDatabase.Hit hit,UiTheme.Palette palette){
        row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        SpannableStringBuilder label=new SpannableStringBuilder();
        label.append(title==null||title.isEmpty()?"Untitled conversation":title);
        label.setSpan(new StyleSpan(Typeface.BOLD),0,label.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if(metadata!=null&&!metadata.isEmpty()){
            label.append('\n');
            int start=label.length();
            label.append(metadata);
            label.setSpan(new RelativeSizeSpan(.86f),start,label.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if(hit!=null&&hit.excerpt!=null&&!hit.excerpt.isEmpty()){
            label.append('\n');
            int start=label.length();
            label.append(hit.excerpt);
            if(hit.excerptStart>=0&&hit.excerptEnd>hit.excerptStart&&hit.excerptEnd<=hit.excerpt.length()){
                int from=start+hit.excerptStart,to=start+hit.excerptEnd;
                label.setSpan(new BackgroundColorSpan(palette.accentSoft),from,to,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                label.setSpan(new ForegroundColorSpan(UiTheme.readableForeground(palette.text,palette.accentSoft)),
                        from,to,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                label.setSpan(new StyleSpan(Typeface.BOLD),from,to,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        row.setContentDescription(null);
        row.setText(label);
    }
}
