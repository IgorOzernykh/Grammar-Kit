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

package org.intellij.grammar.generator;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.dbCommitted.SqliteTables;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import org.intellij.grammar.KnownAttribute;
import org.intellij.grammar.psi.BnfFile;
import org.intellij.grammar.psi.BnfRule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class PrinterGenerator {
  final BnfFile myFile;
  final RuleGraphHelper myGraphHelper;
  final ExpressionHelper myExpressionHelper;
  final Map<String, String> mySimpleTokens;
  final GenOptions myGenOptions;
  final RuleMethodsHelper myRuleMethodsHelper;

  final Map<String, BnfRule> mySignificantRules;
  final BnfRule myGrammarRoot;
  final String languageName;
  final String printerPackage;
  final String factoryPackage;
  final String fileExtension;

  public PrinterGenerator(BnfFile f) {
    myFile = f;
    myGraphHelper = RuleGraphHelper.getCached(myFile);
    myExpressionHelper = new ExpressionHelper(myFile, myGraphHelper, true);
    mySimpleTokens = ContainerUtil.newLinkedHashMap(RuleGraphHelper.getTokenMap(myFile));
    myGenOptions = new GenOptions(myFile);
    myRuleMethodsHelper = new RuleMethodsHelper(myGraphHelper, myExpressionHelper, mySimpleTokens, myGenOptions);

    myGrammarRoot = myFile.getRules().get(0);
    mySignificantRules = new LinkedHashMap<String, BnfRule>();
    createRuleMap();
    languageName = myFile.getName().substring(0, myFile.getName().indexOf('.'));
    printerPackage = myFile.findAttributeValue(null, KnownAttribute.PRINTER_PACKAGE, null);
    factoryPackage = myFile.findAttributeValue(null, KnownAttribute.FACTORY_PACKAGE, null);
    fileExtension = myFile.findAttributeValue(null, KnownAttribute.FILE_EXTENSION, null);
  }

  private Map<String, BnfRule> createRuleMap() {
    Map<String, BnfRule> psiRules = new LinkedHashMap<String, BnfRule>();
    psiRules.put(myGrammarRoot.getName(), myGrammarRoot);
    for (BnfRule rule : myFile.getRules()) {
      if (!RuleGraphHelper.shouldGeneratePsi(rule, true)) continue;
      if (ParserGeneratorUtil.Rule.isLeft(rule)) continue;
      String elementType = ParserGeneratorUtil.getElementType(rule, myGenOptions.generateElementCase);
      if (StringUtil.isEmpty(elementType)) continue;
      psiRules.put(rule.getName(), rule);
    }
    myRuleMethodsHelper.buildMaps(psiRules.values());
    for (BnfRule rule : psiRules.values()) {
      Collection<RuleMethodsHelper.MethodInfo> list = myRuleMethodsHelper.getFor(rule);
      if (list.isEmpty()) continue;
      for (RuleMethodsHelper.MethodInfo methodInfo : list) {
        if (methodInfo.rule != null
            || !StringUtil.isEmpty(methodInfo.name)
            || methodInfo.cardinality == RuleGraphHelper.Cardinality.REQUIRED) {
          mySignificantRules.put(rule.getName(), rule);
          break;
        }
      }
    }
    return mySignificantRules;
  }

  private String getPrinterText() {
    // final String componentPackage = printerPackage + ".components";
    final String componentPackage = myFile.findAttributeValue(null, KnownAttribute.PSI_PACKAGE, null);
    String langPrinterPackage = printerPackage + ".printer";
    String psiElemComponentPackage = printerPackage + ".templateBase";
    final String filePsiClass = languageName + "File";
    final String factoryName = languageName + "ElementFactory";

    String compDeclaration = "";
    String applyTmplt = "";
    String getVariants = "";
    String getSaveTemplate = "";
    String factoryCreate = "";
    String countTemplates = "";
    String getTmplt = "";
    for (BnfRule rule : mySignificantRules.values()) {
      if (myFile.getRules().get(0).equals(rule))
        continue;
      String ruleName = getBeautifulName(rule.getName());
      String psiClassName = "Psi" + ruleName;
      String psiComponentClass = ruleName + "Component";
      String psiComponentName = StringUtil.decapitalize(psiComponentClass);
      compDeclaration += "public val " + psiComponentName + ": " + psiComponentClass + " = " + psiComponentClass + "(this)\n";
      applyTmplt += "is " + psiClassName + " -> applyTmplt(p)\n";
      getVariants += "is " + psiClassName + " -> " + psiComponentName + ".getVariants(p, context)\n";
      getSaveTemplate += "is " + psiClassName + " -> " + psiComponentName + ".getAndSaveTemplate(p)\n";
      factoryCreate += "is " + psiClassName + " -> factory.create" + ruleName + "FromText(text)\n";
      countTemplates += /*"+ " + */psiComponentName + ".getTemplates().size() + \n";
      getTmplt += "is " + psiClassName + " -> " + psiComponentName + ".getTmplt(p)\n";
    }
    countTemplates += " + 0";

    final Map<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@LANG@", languageName);
    replaceMap.put("@LANG_PACKAGE@", printerPackage);
    replaceMap.put("@COMP_PACKAGE@", componentPackage);
    replaceMap.put("@FILE_CLASS@", filePsiClass);
    replaceMap.put("@FACTORY@", factoryName);
    replaceMap.put("@FILE_COMP_PASC@", StringUtil.decapitalize(filePsiClass) + "Component");
    replaceMap.put("@FILE_COMP@", filePsiClass + "Component");
    replaceMap.put("@COMP_DECLARATION@", compDeclaration);
    replaceMap.put("@APPLY_TEMPLATE@", applyTmplt);
    replaceMap.put("@GET_VARIANTS@", getVariants);
    replaceMap.put("@GET_SAVE_TEMPLATE@", getSaveTemplate);
    replaceMap.put("@FACTORY_CREATE@", factoryCreate);
    replaceMap.put("@COUNT_TEMPLATES@", countTemplates);
    replaceMap.put("@GET_TMPLT@", getTmplt);

    File printerTemplate = new File("testData/printer/Printer.txt");
    String templateContent;
    try {
      templateContent = FileUtil.loadFile(printerTemplate);
    }
    catch (IOException e) {
      return ""; // TODO: provide some information
    }


    for (Map.Entry<String, String> entry : replaceMap.entrySet()) {
      templateContent = templateContent.replaceAll(entry.getKey(), entry.getValue());
    }

    return  templateContent;
  }

  private String getPsiElementComponentText() {
    final Map<String, String> replaceMap = ImmutableMap.of(
      "@LANG_PACKAGE@", printerPackage,
      "@LANG@"        , languageName
    );

    String templateContent;
    try {
      // TODO: remove hardcoded path
      templateContent = FileUtil.loadFile(new File("testData/printer/PsiElementComponent.txt"));
    } catch (IOException e) {
      return "";
    }
    for (Map.Entry<String, String> entry : replaceMap.entrySet()) {
      templateContent = templateContent.replaceAll(entry.getKey(), entry.getValue());
    }
    return templateContent;
  }

  public void generatePrinterFiles() {
    // TODO: remove test output
    try {
      FileUtil.writeToFile(new File("testData/printer/templateBase/" + languageName + "PsiElementComponent.kt"),
                           getPsiElementComponentText());
      FileUtil.writeToFile(new File("testData/printer/" + languageName + "Printer.kt"), getPrinterText());
    }
    catch (IOException e) {
      return; // TODO: provide some info
    }
  }

  public static String getBeautifulName(String s) {
    return StringUtil.capitalizeWords(s, "_", true, true).replaceAll("_", "");
  }
}
