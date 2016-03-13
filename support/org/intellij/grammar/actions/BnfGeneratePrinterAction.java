/*
 * Copyright 2011-2015 Gregory Shrago
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.grammar.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.PathUIUtils;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.BackgroundFromStartOption;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.grammar.KnownAttribute;
import org.intellij.grammar.generator.ParserGeneratorUtil;
import org.intellij.grammar.generator.PrinterGenerator;
import org.intellij.grammar.psi.BnfFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BnfGeneratePrinterAction extends AnAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    List<BnfFile> bnfFiles = GenerateAction.getFiles(e);
    boolean enabled = !bnfFiles.isEmpty();
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setVisible(enabled);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getEventProject(e);
    final List<BnfFile> bnfFiles = GenerateAction.getFiles(e);
    if (project == null || bnfFiles.isEmpty()) return;
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    FileDocumentManager.getInstance().saveAllDocuments();

    final Map<BnfFile, VirtualFile> rootMap = ContainerUtil.newLinkedHashMap();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        for (BnfFile file : bnfFiles) {
          String parserClass = ParserGeneratorUtil.getRootAttribute(file, KnownAttribute.PARSER_CLASS);
          VirtualFile target = FileGeneratorUtil.getTargetDirectoryFor(project, file.getVirtualFile(),
                                                                       StringUtil.getShortName(parserClass),
                                                                       StringUtil.getPackageName(parserClass), true);
          //PrinterGenerator printerGenerator = new PrinterGenerator(file);
          //printerGenerator.generatePrinterFiles();
          rootMap.put(file, target);
        }
      }
    });

    ProgressManager.getInstance().run(new Task.Backgroundable(project, "Printer Generation", true, new BackgroundFromStartOption()) {
      List<File> files = ContainerUtil.newArrayList();
      Set<VirtualFile> targets = ContainerUtil.newLinkedHashSet();
      long totalWritten = 0;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        long startTime = System.currentTimeMillis();
        indicator.setIndeterminate(true);

        for (final BnfFile file : bnfFiles) {
          final String sourcePath = FileUtil.toSystemDependentName(
            PathUtil.getCanonicalPath(file.getVirtualFile().getParent().getPath()));
          VirtualFile target = rootMap.get(file);
          if (target == null) return;
          targets.add(target);
          final File genDir = new File(VfsUtil.virtualToIoFile(target).getAbsolutePath());
          PrinterGenerator printerGenerator = new PrinterGenerator(file, genDir.getPath());
          printerGenerator.generatePrinterFiles();
        }

      }
    });
  }

  static String getPrinterPackage(BnfFile bnfFile) {
    return bnfFile.findAttributeValue(null, KnownAttribute.PRINTER_PACKAGE, null);
  }

  static String getFactoryPackage(BnfFile bnfFile) {
    return bnfFile.findAttributeValue(null, KnownAttribute.FACTORY_CLASS, null);
  }

  static String getFileExtension(BnfFile bnfFile) {
    return bnfFile.findAttributeValue(null, KnownAttribute.FILE_EXTENSION, null);
  }
}
