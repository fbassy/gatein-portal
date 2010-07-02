/**
 * Copyright (C) 2009 eXo Platform SAS.
 * 
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.exoplatform.web.application.javascript;

import org.exoplatform.commons.utils.Safe;
import org.exoplatform.container.ExoContainerContext;
import org.gatein.common.logging.Logger;
import org.gatein.common.logging.LoggerFactory;
import org.gatein.wci.impl.DefaultServletContainerFactory;
import org.picocontainer.Startable;

import java.io.*;
import java.util.*;

import javax.servlet.ServletContext;

public class JavascriptConfigService implements Startable
{

   /** Our logger. */
   private final Logger log = LoggerFactory.getLogger(JavascriptConfigService.class);

   private Collection<String> availableScripts_;

   private Collection<String> availableScriptsPaths_;

   private List<Javascript> availableScriptsKey_;

   private String mergedJavascript = "";

   private HashMap<String, String> extendedJavascripts;

   private byte[] jsBytes = null;

   /** . */
   private JavascriptDeployer deployer;

   private JavascriptRemoval removal;

   /** Used to clear merged Javascript on undeploying an webapp */
   private Map<String, List<String>> object_view_of_merged_JS;

   public JavascriptConfigService(ExoContainerContext context)
   {
      availableScripts_ = new ArrayList<String>();
      availableScriptsPaths_ = new ArrayList<String>();
      availableScriptsKey_ = new ArrayList<Javascript>();
      extendedJavascripts = new HashMap<String, String>();
      deployer = new JavascriptDeployer(context.getPortalContainerName(), this);
      removal = new JavascriptRemoval(context.getPortalContainerName(), this);
      object_view_of_merged_JS = new HashMap<String, List<String>>();
   }

   /**
    * Return a collection list This method should return the availables scripts
    * in the service
    * 
    * @return
    */
   public Collection<String> getAvailableScripts()
   {
      return availableScripts_;
   }

   /**
    * return a collection list This method should return the availables script paths
    * @return
    */
   public Collection<String> getAvailableScriptsPaths()
   {
      return availableScriptsPaths_;
   }

   /**
    * Add extended JavaScript into available JavaScript
    * @param module
    *           module name
    * @param scriptPath
    *           URI path of JavaScript 
    * @param scontext
    *           the webapp's {@link javax.servlet.ServletContext}
    * @param scriptData
    *            Content of JavaScript that will be added into available JavaScript
    */
   public synchronized void addExtendedJavascript(String module, String scriptPath, ServletContext scontext, String scriptData)
   {
      String servletContextName = scontext.getServletContextName();
      String path = "/" + servletContextName + scriptPath;
      availableScripts_.add(module);
      availableScriptsPaths_.add(path);
      extendedJavascripts.put(path, scriptData);
   }

   /**
    * Clear available JavaScript and add new JavaScripts
    * @param jsKeys
    *          new list of JavaScript will replace current available JavaScript
    */
   @SuppressWarnings("unchecked")
   public synchronized void addJavascripts(List<Javascript> jsKeys)
   {
      availableScriptsKey_.addAll(jsKeys);


      Collections.sort(availableScriptsKey_, new Comparator<Javascript>()
      {
         public int compare(Javascript o1, Javascript o2)
         {
            if (o1.getPriority() == o2.getPriority())
               return o1.getKey().getModule().compareTo(o2.getKey().getModule());
            else if (o1.getPriority() < 0)
               return 1;
            else if (o2.getPriority() < 0)
               return -1;
            else
               return o1.getPriority() - o2.getPriority();
         }
      });

      mergedJavascript = "";
      availableScripts_.clear();
      availableScriptsPaths_.clear();
      object_view_of_merged_JS.clear();

      //
      for (Javascript script : availableScriptsKey_) {
         addJavascript(script);
      }
   }

   /**
    * Add an JavaScript into available JavaScript
    * @param javascript
    *          JavaScript will be added into available JavaScript
    */
   private void addJavascript(Javascript javascript)
   {
      availableScripts_.add(javascript.getKey().getModule());
      availableScriptsPaths_.add(javascript.getPath());

      List<String> mergedJS_list = object_view_of_merged_JS.get(javascript.getKey().getContextPath());
      if (mergedJS_list == null)
      {
         mergedJS_list = new ArrayList<String>();
         object_view_of_merged_JS.put(javascript.getKey().getContextPath(), mergedJS_list);
      }

      StringBuffer sB = new StringBuffer();
      String line = "";
      try
      {
         BufferedReader reader = javascript.getReader();
         try
         {
            while ((line = reader.readLine()) != null)
            {
               line = line + "\n";
               sB.append(line);
               mergedJS_list.add(line);
            }
         }
         catch (Exception ex)
         {
            ex.printStackTrace();
         }
         finally
         {
            Safe.close(reader);
         }
      }
      catch (Exception e)
      {
         e.printStackTrace();
      }
      sB.append("\n");
      mergedJS_list.add("\n");

      mergedJavascript = mergedJavascript.concat(sB.toString());
   }

   /**
    * Remove JavaScripts from availabe JavaScipts by ServletContext
    * @param scontext
    *           the webapp's {@link javax.servlet.ServletContext}
    *          
    */
   public synchronized void remove(ServletContext context)
   {
      // We clone to avoid concurrent modification exception
      for (Javascript script : new ArrayList<Javascript>(availableScriptsKey_))
      {
         if (script.getContext().getContextPath().equals(context.getContextPath())) {
            removeJavascript(script);
         }
      }
   }

   /**
    * Remove JavaScript from available JavaScripts
    * @param script
    *          JavaScript will be removed
    */
   public synchronized void removeJavascript(Javascript script)
   {
      availableScripts_.remove(script.getKey().getModule());
      availableScriptsPaths_.remove(script.getPath());
      object_view_of_merged_JS.remove(script.getKey().getContextPath());

      // Enlist entries to be removed
      for (Iterator<Javascript> i = availableScriptsKey_.iterator();i.hasNext();)
      {
         Javascript _script = i.next();
         if (script.getKey().equals(_script.getKey()))
         {
            i.remove();
         }
      }
   }

   /**
    *  Refresh the mergedJavascript
    */
   public void refreshMergedJavascript()
   {
      mergedJavascript = "";
      StringBuffer buffer = new StringBuffer();
      for (String webApp : object_view_of_merged_JS.keySet())
      {
         for (String jsPath : object_view_of_merged_JS.get(webApp))
         {
            buffer.append(jsPath);
         }
      }
      mergedJavascript = buffer.toString();
   }

   /**
    * Write the merged javascript in a provided output stream.
    *
    * @param out the output stream
    * @throws IOException any io exception
    */
   public void writeMergedJavascript(OutputStream out) throws IOException
   {
      if (jsBytes == null)
      {
         // Generate javascript in a buffer
         StringBuffer allJavascript = new StringBuffer();
         allJavascript.append(mergedJavascript);
         for (String script : extendedJavascripts.values())
         {
            allJavascript.append(script);
         }
         String s = allJavascript.toString();

         // Get bytes
         byte[] bytes;
         try
         {
            bytes = s.getBytes("UTF-8");
         }
         catch (UnsupportedEncodingException e)
         {
            throw new AssertionError("No access to UTF-8, e");
         }

         // Minify
         try
         {
            ByteArrayInputStream input = new ByteArrayInputStream(bytes);
            ByteArrayOutputStream jsStream = new ByteArrayOutputStream();
            JSMin jsMin = new JSMin(input, jsStream);
            jsMin.jsmin();
            jsBytes = jsStream.toByteArray();
         }
         catch (Exception e)
         {
            log.error("Error when generating minified javascript, will use normal javascript instead", e);
            jsBytes = bytes;
         }
      }

      //
      out.write(jsBytes);
   }

   /**
    * Check the existence of module in Available Scripts
    * @param module
    * @return
    */
   public boolean isModuleLoaded(CharSequence module)
   {
      return getAvailableScripts().contains(module);
   }

   /**
    * Remove JavaScript from available JavaScripts and extended JavaScripts
    * @param module
    *          module will be removed
    * @param scriptPath
    *          URI of script that will be removed
    * @param scontext
    *          the webapp's {@link javax.servlet.ServletContext}
    *          
    */
   public void removeExtendedJavascript(String module, String scriptPath, ServletContext scontext)
   {
      String servletContextName = scontext.getServletContextName();
      availableScripts_.remove(module);
      String path = "/" + servletContextName + scriptPath;
      availableScriptsPaths_.remove(path);
      extendedJavascripts.remove(path);
      jsBytes = null;
   }

   /**
    * Start service
    * @see org.picocontainer.Startable#start()
    */
   public void start()
   {
      DefaultServletContainerFactory.getInstance().getServletContainer().addWebAppListener(deployer);
      DefaultServletContainerFactory.getInstance().getServletContainer().addWebAppListener(removal);
   }

   /**
    * Stop service
    * @see org.picocontainer.Startable#stop()
    */
   public void stop()
   {
      DefaultServletContainerFactory.getInstance().getServletContainer().removeWebAppListener(deployer);
      DefaultServletContainerFactory.getInstance().getServletContainer().removeWebAppListener(removal);
   }

}