package com.example.markdowndesk.workspace.archive;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Value object for archive-wide and conversation-local search. SQL remains
 * parameterized in ArchiveDatabase; UI cannot interpolate filter expressions.
 */
public final class ArchiveSearchSpec {
    public static final int MAX_PHRASE_UNITS=1024;
    public final String term,speaker,fromDate,toDate;
    public ArchiveSearchSpec(String phrase,String speaker,String from,String to) {
        term=phrase==null?"":phrase.trim();
        this.speaker=speaker==null?"":speaker.trim();
        fromDate=checkDate(from);
        toDate=checkDate(to);
        if(term.isEmpty())throw new IllegalArgumentException("Enter words or an exact phrase to search");
        if(!fromDate.isEmpty()&&!toDate.isEmpty()&&fromDate.compareTo(toDate)>0)
            throw new IllegalArgumentException("End date cannot precede start date");
        if(term.length()>MAX_PHRASE_UNITS)throw new IllegalArgumentException("Search phrase is too long");
        if(this.speaker.length()>64)throw new IllegalArgumentException("Speaker name is too long");
    }
    public boolean filtersMessagesOnly(){return !speaker.isEmpty()||!fromDate.isEmpty()||!toDate.isEmpty();}
    private static String checkDate(String date){
        if(date==null||date.trim().isEmpty())return "";
        String normalized=date.trim();
        if(!normalized.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            throw new IllegalArgumentException("Use dates in YYYY-MM-DD format");
        try{ LocalDate.parse(normalized); }
        catch(DateTimeParseException error){throw new IllegalArgumentException("Invalid calendar date",error);}
        return normalized;
    }
}
