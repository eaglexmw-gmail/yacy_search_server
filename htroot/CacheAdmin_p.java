// CacheAdmin_p.java 
// -----------------------
// part of the AnomicHTTPD caching proxy
// (C) by Michael Peter Christen; mc@yacy.net
// first published on http://www.anomic.de
// Frankfurt, Germany, 2004
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; either version 2 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

// You must compile this file with
// javac -classpath .:../classes CacheAdmin_p.java
// if the shell's current path is HTROOT

/* changes by [FB], 19.12.2006:
 * - removed HTML code from .java file in favour of the corresponding .html
 */

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeSet;

import de.anomic.htmlFilter.htmlFilterContentScraper;
import de.anomic.htmlFilter.htmlFilterImageEntry;
import de.anomic.htmlFilter.htmlFilterWriter;
import de.anomic.http.httpHeader;
import de.anomic.plasma.plasmaHTCache;
import de.anomic.plasma.plasmaParserDocument;
import de.anomic.plasma.plasmaSwitchboard;
import de.anomic.plasma.plasmaSwitchboardConstants;
import de.anomic.plasma.cache.IResourceInfo;
import de.anomic.plasma.cache.UnsupportedProtocolException;
import de.anomic.server.serverFileUtils;
import de.anomic.server.serverObjects;
import de.anomic.server.serverSwitch;
import de.anomic.yacy.yacyURL;

public class CacheAdmin_p {
	
	public static final String thisHtmlFile = "CacheAdmin_p.html";
	
	private static final int TypeDIR = 1;
	private static final int TypeFILE = 0;
	
	private static final int HtmlFile = 0;
	private static final int NotCached = 1;
	private static final int Image = 2;
    private static final int ProtocolError = 3;
    private static final int SecurityError = 4;
    
    public static final class Filter implements FilenameFilter {
        private static final String EXCLUDE_NAME = plasmaHTCache.DB_NAME;
        private final File EXCLUDE_DIR;
        public Filter(final File path) { this.EXCLUDE_DIR = path; }
        public boolean accept(final File dir, final String name) {
            return !dir.equals(EXCLUDE_DIR) && !name.equals(EXCLUDE_NAME);
        }
    }

    public static serverObjects respond(final httpHeader header, final serverObjects post, final serverSwitch<?> env) {
        final plasmaSwitchboard switchboard = (plasmaSwitchboard) env;
        final serverObjects prop = new serverObjects();

        final String action = ((post == null) ? "info" : post.get("action", "info"));
        String pathString = ((post == null) ? "" : post.get("path", "/"));
//      String pathString = ((post == null) ? "" : post.get("path", "/").replaceAll("//", "/")); // where is the BUG ?

        // don't leave the htCachePath
        File file = new File(switchboard.htCachePath, pathString);
        try {
            if (!file.getCanonicalPath().startsWith(switchboard.htCachePath.getCanonicalPath())) {
                pathString = "/";
                file = new File(switchboard.htCachePath, pathString);
            }
        } catch (final IOException e) {
            pathString = "/";
            file = new File(switchboard.htCachePath, pathString);
        }

        final StringBuffer path = new StringBuffer(256);
        final StringBuffer tree = new StringBuffer();
        final StringBuffer info = new StringBuffer();

        final yacyURL  url  = plasmaHTCache.getURL(file);
        
        String urlstr = "";
        
        if (action.equals("info") && !file.isDirectory() && url != null) {					// normal file
            prop.put("info", TypeFILE);
            // path.append((pathString.length() == 0) ? linkPathString("/", true) : linkPathString(pathString, false));
            linkPathString(prop, ((pathString.length() == 0) ? ("/") : (pathString)), true);

            urlstr = url.toNormalform(true, true);
            prop.put("info_url", urlstr);

            info.ensureCapacity(10000);
            try {
                final IResourceInfo resInfo = plasmaHTCache.loadResourceInfo(url);
                if (resInfo == null) {
                    prop.put("info_type", NotCached);
                } else {
                    formatHeader(prop, resInfo.getMap());
                    
                    final String ff = file.toString();
                    final int dotpos = ff.lastIndexOf('.');
                    final String ext = (dotpos >= 0) ? ff.substring(dotpos + 1).toLowerCase() : "";
                    if (ext.equals("gif") || ext.equals("jpg") ||
                        ext.equals("png") || ext.equals("jpeg") ||
                        ext.equals("ico") || ext.equals("bmp")) {
                    	prop.put("info_type", Image);
                        prop.put("info_type_src", pathString);
                    } else {
                    	prop.put("info_type", HtmlFile);
                    	// fill the htmlFilerContentScraper object with the contents of the cached file
                    	// to retrieve all needed information
                        final htmlFilterContentScraper scraper = new htmlFilterContentScraper(url);
                        //final OutputStream os = new htmlFilterOutputStream(null, scraper, null, false);
                        final Writer writer = new htmlFilterWriter(null,null,scraper,null,false);                    
                        String sourceCharset = resInfo.getCharacterEncoding();
                        if (sourceCharset == null) sourceCharset = "UTF-8";
                        final String mimeType = resInfo.getMimeType();                    
                        serverFileUtils.copy(file, Charset.forName(sourceCharset), writer);
                        writer.close();
                        
                        final plasmaParserDocument document = switchboard.parser.transformScraper(url, mimeType, sourceCharset, scraper);
                        
                        prop.putHTML("info_type_title", scraper.getTitle());
                        
                        int i;
                        final String[] t = document.getSectionTitles();
                        prop.put("info_type_headlines", t.length);
                        for (i = 0; i < t.length; i++)
                        	prop.putHTML("info_type_headlines_" + i + "_headline",
                        			t[i].replaceAll("\n", "").trim());
                        
                        formatAnchor(prop, document.getHyperlinks(), "links");
                        formatImageAnchor(prop, document.getImages());
                        formatAnchor(prop, document.getAudiolinks(), "audio");
                        formatAnchor(prop, document.getVideolinks(), "video");
                        formatAnchor(prop, document.getApplinks(), "apps");
                        formatEmail(prop, document.getEmaillinks(), "email");
                        
                        prop.putHTML("info_type_text", new String(scraper.getText()));
                        
                        i = 0;
                        final Iterator<StringBuffer> sentences = document.getSentences(false);
                        if (sentences != null)
                        	while (sentences.hasNext()) {
                        		prop.putHTML("info_type_lines_" + i + "_line",
                        				new String(sentences.next()).replaceAll("\n", "").trim());
    	                        i++;
    	                    }
                        prop.put("info_type_lines", i);
                        if (document != null) document.close();
                    }
                }
            } catch (final IOException e) {
            	prop.put("info_type", NotCached);
            } catch (final UnsupportedProtocolException e) {
                prop.put("info_type", ProtocolError);
            } catch (final IllegalAccessException e) {
                prop.put("info_type", SecurityError);
            }
        } else {
            prop.put("info", TypeDIR);

            File dir;
            if (file.isDirectory()) {
                dir = file;
            } else {
                dir = file.getParentFile();
                pathString = (new File(pathString)).getParent().replace('\\','/');
            }

            // generate sorted dir/file listing
            final String[] list = dir.list(new Filter(switchboard.getConfigPath(plasmaSwitchboardConstants.HTCACHE_PATH, plasmaSwitchboardConstants.HTCACHE_PATH_DEFAULT)));
            tree.ensureCapacity((list == null) ? 70 : (list.length + 1) * 256);
            linkPathString(prop, ((pathString.length() == 0) ? ("/") : (pathString)), true); 
            if (list == null) {
                prop.put("info_empty", "1");
            } else {
            	prop.put("info_empty", "0");
                final TreeSet<String> dList = new TreeSet<String>();
                final TreeSet<String> fList = new TreeSet<String>();
                final int size = list.length - 1;
                int i;
                for (i = size; i >= 0 ; i--) { // Rueckwaerts ist schneller
                    if (new File(dir, list[i]).isDirectory())
                        dList.add(list[i]);
                    else
                        fList.add(list[i]);
                }
                
                Iterator<String> iter = dList.iterator();
                i = 0;
                prop.put("info_treeFolders", dList.size());
                while (iter.hasNext()) {
                    prop.put("info_treeFolders_" + i + "_path", pathString);
                    prop.put("info_treeFolders_" + i + "_name", iter.next());
                    i++;
                } 
                
                i = 0;
                iter = fList.iterator();
                prop.put("info_treeFiles", fList.size());
                while (iter.hasNext()) {
                    prop.put("info_treeFiles_" + i + "_path", pathString);
                    prop.put("info_treeFiles_" + i + "_name", iter.next());
                    i++;
                }
            }
        }
        
        prop.putNum("cachesize", plasmaHTCache.curCacheSize/1024);
        prop.putNum("cachemax", plasmaHTCache.maxCacheSize/1024);
        prop.put("path", path.toString());
        prop.putHTML("info_info", info.toString());

        /* prop.put("info_tree", tree.toString()); */
        // return rewrite properties
        return prop;
    }
    
    private static void formatHeader(final serverObjects prop, final Map<String, String> header) {
        if (header == null) {
            prop.put("info_header", "0");
        } else {
        	prop.put("info_header", "1");
        	int i = 0;
            final Iterator<Map.Entry<String, String>> iter = header.entrySet().iterator();
            Map.Entry<String, String> entry;
            while (iter.hasNext()) {
            	entry = iter.next();
            	prop.put("info_header_line_" + i + "_property", entry.getKey());
            	prop.put("info_header_line_" + i + "_value", entry.getValue());
            	i++;
            }
            prop.put("info_header_line", i);
        }
    }

    private static void formatAnchor(final serverObjects prop, final Map<yacyURL, String> anchor, final String extension) {
        final Iterator<Map.Entry<yacyURL, String>> iter = anchor.entrySet().iterator();
        String descr;
        Map.Entry<yacyURL, String> entry;
        prop.put("info_type_use." + extension + "_" + extension, anchor.size());
        int i = 0;
        while (iter.hasNext()) {
            entry = iter.next();
            descr = entry.getValue().trim();
            if (descr.length() == 0) { descr = "-"; }
            prop.put("info_type_use." + extension + "_" + extension + "_" + i + "_name",
            		de.anomic.data.htmlTools.encodeUnicode2html(descr.replaceAll("\n", "").trim(), true));
            prop.put("info_type_use." + extension + "_" + extension + "_" + i + "_link",
            		de.anomic.data.htmlTools.encodeUnicode2html(entry.getKey().toString(), true));
            i++;
        }
        prop.put("info_type_use." + extension, (i == 0) ? 0 : 1);
    }
    
    private static void formatEmail(final serverObjects prop, final Map<String, String> anchor, final String extension) {
        final Iterator<Map.Entry<String, String>> iter = anchor.entrySet().iterator();
        String descr;
        Map.Entry<String, String> entry;
        prop.put("info_type_use." + extension + "_" + extension, anchor.size());
        int i = 0;
        while (iter.hasNext()) {
            entry = iter.next();
            descr = entry.getValue().trim();
            if (descr.length() == 0) { descr = "-"; }
            prop.put("info_type_use." + extension + "_" + extension + "_" + i + "_name",
                    de.anomic.data.htmlTools.encodeUnicode2html(descr.replaceAll("\n", "").trim(), true));
            prop.put("info_type_use." + extension + "_" + extension + "_" + i + "_link",
                    de.anomic.data.htmlTools.encodeUnicode2html(entry.getKey(), true));
            i++;
        }
        prop.put("info_type_use." + extension, (i == 0) ? 0 : 1);
    }

    private static void formatImageAnchor(final serverObjects prop, final HashMap<String, htmlFilterImageEntry> anchor) {
        final Iterator<htmlFilterImageEntry> iter = anchor.values().iterator();
        htmlFilterImageEntry ie;
        prop.put("info_type_use.images_images", anchor.size());
        int i = 0;
        while (iter.hasNext()) {
            ie = iter.next();
            prop.putHTML("info_type_use.images_images_" + i + "_name", ie.alt().replaceAll("\n", "").trim());
            prop.putHTML("info_type_use.images_images_" + i + "_link",
            		de.anomic.data.htmlTools.encodeUnicode2html(ie.url().toNormalform(false, true), false));
            i++;
        }
        prop.put("info_type_use.images", (i == 0) ? "0" : "1");
    }

    private static void linkPathString(final serverObjects prop, final String path, final boolean dir) {
        final String[] elements = path.split("/");
        String dirs = "";
        int i, e, count = 0;
        if (dir) { e = elements.length; } else { e = elements.length - 1; }
        for(i = 0; i < e; i++) {
            if (elements[i].length() == 0) continue;
        	prop.putHTML("paths_" + count + "_path", dirs);
        	prop.putHTML("paths_" + count + "_name", elements[i]);
        	dirs += "/" + elements[i];
            count++;
        }
        prop.put("paths", count);
        return;
    }
}
