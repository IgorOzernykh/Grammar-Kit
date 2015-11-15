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
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import org.intellij.grammar.KnownAttribute;
import org.intellij.grammar.generator.PrinterGenerator;
import org.intellij.grammar.psi.BnfFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        for (BnfFile file : bnfFiles) {
          PrinterGenerator printerGenerator = new PrinterGenerator(file);
          printerGenerator.generatePrinterFiles(getPrinterPackage(file), getFactoryPackage(file), getFileExtension(file));
        }
      }
    });
  }

  static String getPrinterPackage(BnfFile bnfFile) {
    return bnfFile.findAttributeValue(null, KnownAttribute.PRINTER_PACKAGE, null);
  }

  static String getFactoryPackage(BnfFile bnfFile) {
    return bnfFile.findAttributeValue(null, KnownAttribute.FACTORY_PACKAGE, null);
  }

  static String getFileExtension(BnfFile bnfFile) {
    return bnfFile.findAttributeValue(null, KnownAttribute.FILE_EXTENSION, null);
  }
}
