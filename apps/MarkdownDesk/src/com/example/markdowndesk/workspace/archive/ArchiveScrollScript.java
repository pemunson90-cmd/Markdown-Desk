package com.example.markdowndesk.workspace.archive;

/** Static, data-free JS for the archive's bounded WebView reading window.
 * The database supplies HTML sections separately; text is never interpolated
 * into a script or used as executable source.
 */
public final class ArchiveScrollScript {
    private ArchiveScrollScript() {}

    public static String boot() {
        return "<script>" +
            "(function(){\n" +
            "  var stream=document.getElementById('archive-stream'),\n" +
            "      before=document.getElementById('archive-before'),\n" +
            "      after=document.getElementById('archive-after');\n" +
            "  if(!stream||!before||!after)return;\n" +
            "  var busy=false,chunkBusy=false,lastY=window.scrollY;\n" +
            "  function ask(direction){\n" +
            "    if(busy)return;\n" +
            "    var node=direction<0?before:after;\n" +
            "    if(node.getAttribute('data-more')!=='1')return;\n" +
            "    busy=true;window.location.href='mdarchive://adjacent?direction='+direction;\n" +
            "  }\n" +
            "  function partList(content){return content.querySelectorAll(':scope > .archive-part');}\n" +
            "  function heldSelection(node){\n" +
            "    var selection=window.getSelection();if(!selection||!selection.rangeCount)return false;\n" +
            "    return node.contains(selection.anchorNode)||node.contains(selection.focusNode);\n" +
            "  }\n" +
            "  function updateEarlier(section,content){\n" +
            "    var list=partList(content),first=list[0];\n" +
            "    var index=first?Number(first.getAttribute('data-start-part')):0;\n" +
            "    var link=section.querySelector('.archive-earlier');\n" +
            "    if(index>0){\n" +
            "      if(!link){\n" +
            "        link=document.createElement('a');\n" +
            "        link.className='archive-more archive-earlier';\n" +
            "        link.textContent='Load earlier message text';\n" +
            "        section.insertBefore(link,content);\n" +
            "      }\n" +
            "      var previous=Math.max(0,index-20);\n" +
            "      link.href='mdarchive://chunk?direction=-1&ordinal='+section.getAttribute('data-ordinal')+'&part='+previous+'&until='+index;\n" +
            "    }else if(link)link.remove();\n" +
            "  }\n" +
            "  function trimParts(section,content,direction){\n" +
            "    var parts=partList(content);\n" +
            "    while(parts.length>6){\n" +
            "      var victim=direction>0?parts[0]:parts[parts.length-1];\n" +
            "      if(heldSelection(victim))break;\n" +
            "      var box=victim.getBoundingClientRect();\n" +
            "      if(direction>0 && box.bottom > -300)break;\n" +
            "      if(direction<0 && box.top < window.innerHeight+300)break;\n" +
            "      var anchor=direction>0?parts[1]:parts[parts.length-2];\n" +
            "      var previous=anchor.getBoundingClientRect().top;\n" +
            "      victim.remove();\n" +
            "      if(document.contains(anchor)){\n" +
            "        window.scrollBy(0,anchor.getBoundingClientRect().top-previous);\n" +
            "        lastY=window.scrollY;\n" +
            "      }\n" +
            "      parts=partList(content);\n" +
            "    }\n" +
            "    updateEarlier(section,content);\n" +
            "  }\n" +
            "  function sectionAt(ordinal){\n" +
            "    return stream.querySelector('section[data-ordinal=\"'+ordinal+'\"]');\n" +
            "  }\n" +
            "  window.mdArchiveRelease=function(){busy=false;};\n" +
            "  window.mdArchiveChunkRelease=function(){chunkBusy=false;};\n" +
            "  window.mdArchiveAppendChunk=function(ordinal,html,startPart,nextPart,more){\n" +
            "    var section=sectionAt(ordinal);if(!section)return false;\n" +
            "    var content=section.querySelector('.archive-content');if(!content)return false;\n" +
            "    var parts=partList(content),last=parts[parts.length-1];\n" +
            "    if(last && Number(last.getAttribute('data-part-end'))!==startPart)return false;\n" +
            "    var part=document.createElement('div');part.className='archive-part';\n" +
            "    part.setAttribute('data-start-part',String(startPart));part.setAttribute('data-part-end',String(nextPart));part.innerHTML=html;\n" +
            "    content.appendChild(part);\n" +
            "    var link=section.querySelector('.archive-more[data-autoload]');\n" +
            "    if(link){\n" +
            "      if(more){link.href='mdarchive://chunk?direction=1&ordinal='+ordinal+'&part='+nextPart;\n" +
            "        link.setAttribute('data-part',String(nextPart));}\n" +
            "      else link.remove();\n" +
            "    }\n" +
            "    trimParts(section,content,1);\n" +
            "    chunkBusy=false;\n" +
            "    if(window.mdArchiveRefreshHighlights)window.mdArchiveRefreshHighlights();\n" +
            "    return true;\n" +
            "  };\n" +
            "  window.mdArchivePrependChunk=function(ordinal,html,startPart,untilPart){\n" +
            "    var section=sectionAt(ordinal);if(!section)return false;\n" +
            "    var content=section.querySelector('.archive-content');if(!content)return false;\n" +
            "    var parts=partList(content),first=parts[0];if(!first)return false;\n" +
            "    if(Number(first.getAttribute('data-start-part'))!==untilPart)return false;\n" +
            "    var previous=first.getBoundingClientRect().top;\n" +
            "    var part=document.createElement('div');part.className='archive-part';\n" +
            "    part.setAttribute('data-start-part',String(startPart));part.setAttribute('data-part-end',String(untilPart));part.innerHTML=html;\n" +
            "    content.insertBefore(part,first);\n" +
            "    window.scrollBy(0,first.getBoundingClientRect().top-previous);\n" +
            "    lastY=window.scrollY;\n" +
            "    trimParts(section,content,-1);\n" +
            "    chunkBusy=false;\n" +
            "    if(window.mdArchiveRefreshHighlights)window.mdArchiveRefreshHighlights();\n" +
            "    return true;\n" +
            "  };\n" +
            "  window.mdArchiveInsert=function(html,direction,prune,hasBefore,hasAfter){\n" +
            "    var anchor=null,oldTop=0,items=stream.children;\n" +
            "    if(items.length){\n" +
            "      var keep=direction>0?Math.min(prune,items.length-1):0;\n" +
            "      anchor=items[keep];oldTop=anchor.getBoundingClientRect().top;\n" +
            "    }\n" +
            "    var template=document.createElement('template');template.innerHTML=html;\n" +
            "    if(direction>0)stream.appendChild(template.content);\n" +
            "    else stream.insertBefore(template.content,stream.firstChild);\n" +
            "    for(var i=0;i<prune;i++){\n" +
            "      var victim=direction>0?stream.firstElementChild:stream.lastElementChild;\n" +
            "      if(victim)victim.remove();\n" +
            "    }\n" +
            "    before.setAttribute('data-more',hasBefore?'1':'0');\n" +
            "    after.setAttribute('data-more',hasAfter?'1':'0');\n" +
            "    before.hidden=!hasBefore;after.hidden=!hasAfter;\n" +
            "    if(anchor&&document.contains(anchor))window.scrollBy(0,anchor.getBoundingClientRect().top-oldTop);\n" +
            "    busy=false;lastY=window.scrollY;\n" +
            "    if(window.mdArchiveRefreshHighlights)window.mdArchiveRefreshHighlights();\n" +
            "    return true;\n" +
            "  };\n" +
            "  function nearViewport(el,margin){\n" +
            "    var b=el.getBoundingClientRect();\n" +
            "    return b.top<window.innerHeight+margin&&b.bottom>-margin;\n" +
            "  }\n" +
            "  window.addEventListener('scroll',function(){\n" +
            "    var y=window.scrollY,delta=y-lastY;lastY=y;\n" +
            "    if(delta>3 && !chunkBusy){\n" +
            "      var links=stream.querySelectorAll('.archive-more[data-autoload]');\n" +
            "      for(var i=0;i<links.length;i++)if(nearViewport(links[i],420)){\n" +
            "        var url=links[i].getAttribute('href');\n" +
            "        if(url){chunkBusy=true;window.location.href=url;break;}\n" +
            "      }\n" +
            "    }else if(delta< -3 && !chunkBusy){\n" +
            "      var earlier=stream.querySelectorAll('.archive-earlier');\n" +
            "      for(var k=0;k<earlier.length;k++)if(nearViewport(earlier[k],420)){\n" +
            "        var earlierUrl=earlier[k].getAttribute('href');\n" +
            "        if(earlierUrl){chunkBusy=true;window.location.href=earlierUrl;break;}\n" +
            "      }\n" +
            "    }\n" +
            "    if(delta>3 && document.documentElement.scrollHeight-y-window.innerHeight<650)ask(1);\n" +
            "    else if(delta< -3 && y<650)ask(-1);\n" +
            "  },{passive:true});\n" +
            "  before.addEventListener('click',function(event){event.preventDefault();ask(-1);});\n" +
            "  after.addEventListener('click',function(event){event.preventDefault();ask(1);});\n" +
            "})();\n" +
            "</script>";
    }

    /** Small logical reading position, independent of trimmed DOM pixel height. */
    public static String position(){
        return "(function(){var parts=document.querySelectorAll('#archive-stream .archive-part'),chosen=null;"+
            "for(var i=0;i<parts.length;i++){var r=parts[i].getBoundingClientRect();"+
            "if(r.bottom>0){chosen=parts[i];break;}}if(!chosen)return '';"+
            "var section=chosen.closest('[data-ordinal]');if(!section)return '';"+
            "return JSON.stringify({ordinal:Number(section.getAttribute('data-ordinal')),"+
            "part:Number(chosen.getAttribute('data-start-part')),y:Math.round(-chosen.getBoundingClientRect().top)});})()";
    }
    public static String restorePosition(int ordinal,int part,int offset){
        if(ordinal<0||part<0)throw new IllegalArgumentException("Invalid reading anchor");
        return "(function(){var e=document.querySelector('[data-ordinal=\""+ordinal+
            "\"] .archive-part[data-start-part=\""+part+"\"]');if(!e)return false;"+
            "e.scrollIntoView();window.scrollBy(0,"+offset+");return true;})()";
    }

    public static String insert(String html,int direction,int remove,boolean hasBefore,boolean hasAfter){
        if(direction!=1 && direction!=-1)throw new IllegalArgumentException("Direction");
        if(remove<0)throw new IllegalArgumentException("Removal count");
        return "window.mdArchiveInsert("+quote(html)+","+direction+","+remove+","+
                hasBefore+","+hasAfter+");";
    }
    public static String appendChunk(int ordinal,String html,int startPart,int nextPart,boolean hasMore){
        if(ordinal<0||startPart<0||nextPart<=startPart)throw new IllegalArgumentException("Invalid chunk range");
        return "window.mdArchiveAppendChunk("+ordinal+","+quote(html)+","+startPart+","+
                nextPart+","+hasMore+");";
    }
    public static String prependChunk(int ordinal,String html,int startPart,int untilPart){
        if(ordinal<0||startPart<0||untilPart<=startPart||untilPart-startPart>20)
            throw new IllegalArgumentException("Invalid earlier chunk range");
        return "window.mdArchivePrependChunk("+ordinal+","+quote(html)+","+startPart+","+untilPart+");";
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