package com.example.markdowndesk.workspace.archive;

/** Static, data-free JS for the archive's bounded WebView reading window.
 * The database supplies HTML sections separately; text is never interpolated
 * into a script or used as executable source.
 */
public final class ArchiveScrollScript {
    private ArchiveScrollScript() {}

    public static String boot() {
        return "<script>(function(){\n" +
            "var stream=document.getElementById('archive-stream')," +
            "before=document.getElementById('archive-before')," +
            "after=document.getElementById('archive-after');\n" +
            "if(!stream||!before||!after)return;\n" +
            "var busy=false,chunkBusy=false,lastY=window.scrollY;\n" +
            "function ask(direction){\n" +
            " if(busy)return;var node=direction<0?before:after;\n" +
            " if(node.getAttribute('data-more')!=='1')return;\n" +
            " busy=true;window.location.href='mdarchive://adjacent?direction='+direction;\n" +
            "}\n" +
            "window.mdArchiveRelease=function(){busy=false;};\n" +
            "window.mdArchiveChunkRelease=function(){chunkBusy=false;};\n" +
            "window.mdArchiveAppendChunk=function(ordinal,html,nextPart,more){\n" +
            " var selector='section[data-ordinal=\"'+ordinal+'\"]';\n" +
            " var section=stream.querySelector(selector);if(!section)return false;\n" +
            " var content=section.querySelector('.archive-content');if(!content)return false;\n" +
            " var part=document.createElement('div');part.className='archive-part';part.innerHTML=html;\n" +
            " content.appendChild(part);\n" +
            " var older=content.querySelectorAll('.archive-part');\n" +
            " while(older.length>6){older[0].remove();older=content.querySelectorAll('.archive-part');\n" +
            "  if(!section.querySelector('.archive-earlier')){\n" +
            "   var earlier=document.createElement('a');earlier.className='archive-more archive-earlier';\n" +
            "   earlier.textContent='Earlier section of this message';\n" +
            "   earlier.href='mdarchive://continue?ordinal='+ordinal+'&part=0';\n" +
            "   section.insertBefore(earlier,content);\n" +
            "  }\n" +
            " }\n" +
            " var link=section.querySelector('.archive-more[data-autoload]');\n" +
            " if(link){if(more){link.href='mdarchive://chunk?ordinal='+ordinal+'&part='+nextPart;\n" +
            "  link.setAttribute('data-part',String(nextPart));}else link.remove();}\n" +
            " chunkBusy=false;if(window.mdArchiveRefreshHighlights)window.mdArchiveRefreshHighlights();return true;\n" +
            "};\n" +
            "window.mdArchiveInsert=function(html,direction,prune,hasBefore,hasAfter){\n" +
            " var anchor=null,oldTop=0;\n" +
            " var items=stream.children;\n" +
            " if(items.length){\n" +
            "  var keep=direction>0?Math.min(prune,items.length-1):0;\n" +
            "  anchor=items[keep];oldTop=anchor.getBoundingClientRect().top;\n" +
            " }\n" +
            " var template=document.createElement('template');template.innerHTML=html;\n" +
            " if(direction>0)stream.appendChild(template.content);\n" +
            " else stream.insertBefore(template.content,stream.firstChild);\n" +
            " for(var i=0;i<prune;i++){\n" +
            "  var victim=direction>0?stream.firstElementChild:stream.lastElementChild;\n" +
            "  if(victim)victim.remove();\n" +
            " }\n" +
            " before.setAttribute('data-more',hasBefore?'1':'0');\n" +
            " after.setAttribute('data-more',hasAfter?'1':'0');\n" +
            " before.hidden=!hasBefore;after.hidden=!hasAfter;\n" +
            " if(anchor&&document.contains(anchor))window.scrollBy(0,anchor.getBoundingClientRect().top-oldTop);\n" +
            " busy=false;lastY=window.scrollY;if(window.mdArchiveRefreshHighlights)window.mdArchiveRefreshHighlights();return true;\n" +
            "};\n" +
            "window.addEventListener('scroll',function(){\n" +
            " var y=window.scrollY,delta=y-lastY;lastY=y;\n" +
            " if(delta>3 && !chunkBusy){\n" +
            "  var links=stream.querySelectorAll('.archive-more[data-autoload]');\n" +
            "  for(var i=0;i<links.length;i++){var b=links[i].getBoundingClientRect();\n" +
            "   if(b.top<window.innerHeight+420&&b.bottom>0){\n" +
            "    var url=links[i].getAttribute('href');\n" +
            "    if(url){chunkBusy=true;window.location.href=url;break;}\n" +
            "   }\n" +
            "  }\n" +
            " }\n" +
            " if(delta>3 && document.documentElement.scrollHeight-y-window.innerHeight<650)ask(1);\n" +
            " else if(delta< -3 && y<650)ask(-1);\n" +
            "},{passive:true});\n" +
            "before.addEventListener('click',function(event){event.preventDefault();ask(-1);});\n" +
            "after.addEventListener('click',function(event){event.preventDefault();ask(1);});\n" +
            "})();</script>";
    }

    public static String insert(String html,int direction,int remove,boolean hasBefore,boolean hasAfter){
        if(direction!=1 && direction!=-1)throw new IllegalArgumentException("Direction");
        if(remove<0)throw new IllegalArgumentException("Removal count");
        return "window.mdArchiveInsert("+quote(html)+","+direction+","+remove+","+
                hasBefore+","+hasAfter+");";
    }
    public static String appendChunk(int ordinal,String html,int nextPart,boolean hasMore){
        if(ordinal<0||nextPart<0)throw new IllegalArgumentException("Negative chunk position");
        return "window.mdArchiveAppendChunk("+ordinal+","+quote(html)+","+nextPart+","+hasMore+");";
    }
    /** JSON-safe JS literal. '<' is escaped so even text containing a closing
     * script tag remains plain text when used in an HTML script container.
     */
    static String quote(String text){
        StringBuilder out=new StringBuilder("\"");
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            switch(c){
                case '"':out.append("\\\"");break;
                case '\\':out.append("\\\\");break;
                case '\n':out.append("\\n");break;
                case '\r':out.append("\\r");break;
                case '\t':out.append("\\t");break;
                default:
                    if(c<' '||c=='<'||c=='>'||c=='\u2028'||c=='\u2029'){
                        String hex=Integer.toHexString(c);
                        out.append("\\u");
                        for(int p=hex.length();p<4;p++)out.append('0');
                        out.append(hex);
                    }else out.append(c);
            }
        }
        return out.append('"').toString();
    }
}