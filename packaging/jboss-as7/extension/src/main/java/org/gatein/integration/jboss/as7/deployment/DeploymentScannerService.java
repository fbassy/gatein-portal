/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
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
package org.gatein.integration.jboss.as7.deployment;

import org.gatein.integration.jboss.as7.GateInConfiguration;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.server.deployment.DeploymentAddHandler;
import org.jboss.as.server.deployment.DeploymentDeployHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

import java.io.File;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.*;

/**
 * @author <a href="mailto:mstrukel@redhat.com">Marko Strukelj</a>
 */
public class DeploymentScannerService implements Service<DeploymentScannerService>
{
   public static final ServiceName NAME = ServiceName.of("org", "gatein", "deployment", "scanner");

   private static final Logger log = Logger.getLogger("org.gatein");

   private static final String GATEIN_EAR = "gatein.ear";

   private String deployRoot = "gatein";
   private String extensionsSub = "extensions";
   private File gateinHome;

   private GateInConfiguration config;

   public DeploymentScannerService(GateInConfiguration config)
   {
      this.config = config;
      String jbossHome = System.getProperty("jboss.home.dir");
      if (jbossHome == null)
      {
         jbossHome = System.getProperty("user.dir");
         log.warn("System property jboss.home.dir not set! Using current working dir: " + jbossHome);
      }
      gateinHome = new File(jbossHome);
   }

   public ModelNode prepareDeploymentModel()
   {
      File deployDir = new File(gateinHome, deployRoot);
      List<File> deployments = new LinkedList<File>();

      File gateinEar = new File(deployDir, GATEIN_EAR);
      if (!gateinEar.exists())
      {
         throw new RuntimeException("GateIn deployment archive not found: " + gateinEar);
      }
      deployments.add(gateinEar);

      File extensionsDir = new File(deployDir, extensionsSub);
      File [] extensions = extensionsDir.listFiles();
      for (File ext: extensions)
      {
         String name = ext.getName();
         if (name.endsWith(".war") || name.endsWith(".ear") || name.endsWith(".jar"))
         {
            deployments.add(ext);
         }
         else
         {
            log.warn("Skipped gatein extension deployment for: " + ext);
         }
      }

      configureExtensions(deployments);
      return composeDeployOperation(deployments);
   }

   private void configureExtensions(List<File> deployments)
   {
      // we are overriding any previously set config
      config.clearDeploymentArchives();

      String gateinEar = GATEIN_EAR;

      for (File archive: deployments)
      {
         config.addDeploymentArchive(archive.getName(), archive.getName().equals(gateinEar));
      }
   }

   private ModelNode composeDeployOperation(Collection<File> deployments)
   {
      final ModelNode deployOperation = Util.getEmptyOperation(COMPOSITE, new ModelNode());
      final ModelNode allSteps = deployOperation.get(STEPS);

      for (File deployment: deployments)
      {
         final ModelNode address = new ModelNode().add(DEPLOYMENT, deployment.getName());

         final ModelNode addOp =
            org.jboss.as.controller.operations.common.Util.getEmptyOperation(DeploymentAddHandler.OPERATION_NAME, address);

         final ModelNode content = new ModelNode();
         final ModelNode contentItem = content.get(0);

         contentItem.get(PATH).set(getRelativePath(deployment));
         contentItem.get(RELATIVE_TO).set("jboss.home.dir");
         contentItem.get(ARCHIVE).set(false);

         addOp.get(CONTENT).set(content);
         addOp.get(PERSISTENT).set(false);

         final ModelNode deployOp =
            Util.getEmptyOperation(DeploymentDeployHandler.OPERATION_NAME, address);

         final ModelNode op = Util.getEmptyOperation(COMPOSITE, new ModelNode());
         final ModelNode steps = op.get(STEPS);
         steps.add(addOp);
         steps.add(deployOp);
         allSteps.add(op);
      }
      return deployOperation;
   }

   private String getRelativePath(File deployment)
   {
      String pathName = deployment.getPath();
      String root = gateinHome.getPath() + File.separatorChar;
      if (!pathName.startsWith(root))
         throw new IllegalStateException("Deployment archive path outside JBOSS_HOME (" + gateinHome + "): " + pathName);

      return pathName.substring(root.length());
   }

   @Override
   public void start(StartContext context) throws StartException
   {

   }

   @Override
   public void stop(StopContext context)
   {
      //
   }

   @Override
   public DeploymentScannerService getValue() throws IllegalStateException, IllegalArgumentException
   {
      return this;
   }
}
