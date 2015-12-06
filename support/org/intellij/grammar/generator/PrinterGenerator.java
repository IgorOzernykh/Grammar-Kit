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
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import org.intellij.grammar.KnownAttribute;
import org.intellij.grammar.psi.BnfFile;
import org.intellij.grammar.psi.BnfRule;

import java.io.File;
import java.io.IOException;
import java.util.*;

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
  final String elementFactoryPath;
  final String fileClass;
  final String fileExtension;
  final String psiClassPrefix;
  final String elementFactoryClassName;
  final String componentPackage;
  final Map<BnfRule, List<Subtree>> mySubtreeMap;
  private final String pathToTemplates = "testData/printer/";

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
    elementFactoryPath = myFile.findAttributeValue(null, KnownAttribute.FACTORY_CLASS, null);
    fileClass = myFile.findAttributeValue(null, KnownAttribute.FILE_CLASS, null);
    fileExtension = myFile.findAttributeValue(null, KnownAttribute.FILE_EXTENSION, null);
    psiClassPrefix = myFile.findAttributeValue(null, KnownAttribute.PSI_CLASS_PREFIX, null);
    if (elementFactoryPath != null) {
      elementFactoryClassName = StringUtil.getShortName(elementFactoryPath);
    } else {
      elementFactoryClassName = "";
    }
    componentPackage = myFile.findAttributeValue(null, KnownAttribute.PSI_PACKAGE, null);
    mySubtreeMap = createSubtreeMap();
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
      String psiClassName = psiClassPrefix + ruleName;
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

    String templateContent = readTemplate("Printer.txt");

    return replaceTemplates(templateContent, replaceMap);
  }

  private String getPsiElementComponentText() {
    final Map<String, String> replaceMap = ImmutableMap.of(
      "@LANG_PACKAGE@", printerPackage,
      "@LANG@"        , languageName
    );
    String templateContent = readTemplate("PsiElementComponent.txt");
    return replaceTemplates(templateContent, replaceMap);
  }

  public void generatePrinterFiles() {
    // TODO: remove test output
    try {
      FileUtil.writeToFile(new File("testData/printer/templateBase/" + languageName + "PsiElementComponent.kt"),
                           getPsiElementComponentText());
      FileUtil.writeToFile(new File("testData/printer/" + languageName + "Printer.kt"), getPrinterText());
      String s = getCommonComponentText(myFile.getRule("if_stmt"));
      FileUtil.writeToFile(new File("testData/printer/component.kt"), s);
    }
    catch (IOException e) {
      return; // TODO: provide some info
    }
  }


  private String getCommonComponentText(BnfRule rule) {
    String templateContent = readTemplate("Component.txt");
    Map<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@LANG_PACKAGE@", printerPackage);
    replaceMap.put("@IMPORT_LIST@", getImportListText());
    replaceMap.put("@COMP_PACKAGE@", componentPackage);
    replaceMap.put("@COMP_CLASS@", ParserGeneratorUtil.getRulePsiClassName(rule, psiClassPrefix));
    replaceMap.put("@NAME_CC@", getBeautifulName(rule.getName()));
    replaceMap.put("@LANG@", languageName);
    replaceMap.put("@DECL_TAGS@", getTagsDeclaration(rule));
    replaceMap.put("@GEN_SUBTREES@", getSubtreesMethodsText(rule));
    replaceMap.put("@GET_NEW_ELEM@", getGetNewElementText(rule));
    replaceMap.put("@PREPARE_SUBTREES@", getPrepareSubtreesText(rule));
    replaceMap.put("@UPDATE_SUBTREES@", getUpdateSubtreesText(rule));
    replaceMap.put("@GET_TAGS@", getGetTagsText(rule));
    replaceMap.put("@IS_TEMPL_SUIT@", getIsTemplateSuitableText(rule));
    replaceMap.put("@GET_TEMPLATE@", getGetTemplateText(rule));

    return replaceTemplates(templateContent, replaceMap);
  }

  private class Subtree {
    private Subtree(
      String name,
      String getMethod,
      boolean isRequired,
      boolean isEverywhereSuitable,
      boolean hasSeveralElements,
      boolean isList
    ) {
      this.name = name;
      this.getMethod = getMethod;
      this.isRequired = isRequired;
      this.isEverywhereSuitable = isEverywhereSuitable;
      this.hasSeveralElements = hasSeveralElements;
      this.isList = isList;
    }

    public String name;
    public String getMethod;
    public boolean isRequired;
    public boolean isEverywhereSuitable;
    public boolean hasSeveralElements;
    public boolean isList;
  }

  private String getTagsDeclaration(BnfRule rule) {
    List<Subtree> subtrees = mySubtreeMap.get(rule);

    String declTags = "";
    for (Subtree subtree : subtrees) {
      declTags += "final val " + subtree.name.toUpperCase() + "_TAG: String\n"
        + "  get() = \"" + subtree.name.toLowerCase() + "\"\n";
    }
    return declTags;
  }

  private String getGetNewElementText(BnfRule rule) {
    String templateContent = readTemplate("ComponentGetNewElement.txt");
    final Map<String, String> replaceMap = ImmutableMap.of(
      "@COMP_CLASS@", ParserGeneratorUtil.getRulePsiClassName(rule, psiClassPrefix),
      "@ELEMENT_FACTORY@", elementFactoryClassName,
      "@FROM_TEXT@", getBeautifulName(rule.getName())
    );
    return replaceTemplates(templateContent, replaceMap);
  }

  private String getImportListText() {
    String templateContent = readTemplate("ImportList.txt");
    Map<String, String> replaceMap = ImmutableMap.of(
      "@FACTORY_CLASS@", elementFactoryPath,
      "@LANG_PACKAGE@", printerPackage,
      "@LANG@", languageName
    );
    return replaceTemplates(templateContent, replaceMap);
  }

  private String getIsTemplateSuitableText(BnfRule rule) {
    String templateContent = readTemplate("ComponentIsTemplSuit.txt");
    Map<String, String> replaceMap = ImmutableMap.of(
      "@COMP_CLASS@", ParserGeneratorUtil.getRulePsiClassName(rule, psiClassPrefix),
      "@TEMPL_SUIT@", "return true"
    );
    return replaceTemplates(templateContent, replaceMap);
  }

  private String getSubtreeMethodText(Subtree subtree, BnfRule rule, String fileName) {
    String templateContent = readTemplate(fileName);
    Map<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@NAME_CC@", StringUtil.capitalize(subtree.name));
    replaceMap.put("@COMP_CLASS@", ParserGeneratorUtil.getRulePsiClassName(rule, psiClassPrefix));
    replaceMap.put("@NAME@", subtree.name);
    replaceMap.put("@SUBTREE_GET@", subtree.getMethod);
    replaceMap.put("@NAME_CAP@", subtree.name.toUpperCase());
    String everywhereSuit;
    if (subtree.isEverywhereSuitable) {
      everywhereSuit = "Box.getEverywhereSuitable()\n";
    } else {
      everywhereSuit = subtree.name + "!!.toBox()\n";
    }
    replaceMap.put("@EVERYWHERE_SUIT@", everywhereSuit);
    return replaceTemplates(templateContent, replaceMap);
  }

  private String getSubtreesMethodsText(BnfRule rule) {
    List<Subtree> subtrees = getSubtreesForRule(rule);
    String[] files = { "ComponentAddSubtree.txt", "ComponentGetSubtree.txt", "ComponentPrepareSubtree.txt" };
    String subtreesMethodsText = "";
    for (String file : files) {
      for (Subtree subtree : subtrees) {
        subtreesMethodsText += getSubtreeMethodText(subtree, rule, file);
      }
    }
    return subtreesMethodsText;
  }

  private String getUpdateSubtreesText(BnfRule rule) {
    String templateContent = readTemplate("ComponentUpdateSubtrees.txt");
    String updateSubtreeCode = "return variants";
    Map<String, String> replaceMap = ImmutableMap.of(
      "@COMP_CLASS@", ParserGeneratorUtil.getRulePsiClassName(rule, psiClassPrefix),
      "@UPDATE_SUBTREES@", updateSubtreeCode
    );
    return replaceTemplates(templateContent, replaceMap);
  }


  private String getPrepareSubtreesText(BnfRule rule) {
    String templateContent = readTemplate("ComponentPrepareSubtrees.txt");
    String prepSubtreesCode = "";
    for (Subtree subtree : getSubtreesForRule(rule)) {
      prepSubtreesCode += "prepare" + StringUtil.capitalize(subtree.name) + "Variants(p, variants, context)\n";
    }
    Map<String, String> replaceMap = ImmutableMap.of(
      "@COMP_CLASS@", ParserGeneratorUtil.getRulePsiClassName(rule, psiClassPrefix),
      "@PREP_SUBTREES@", prepSubtreesCode
    );
    return replaceTemplates(templateContent, replaceMap);
  }


  private String getGetTagsText(BnfRule rule) {
    String getTagsCode = "";
    for (Subtree subtree : getSubtreesForRule(rule)) {
      if (!subtree.hasSeveralElements) {
        getTagsCode += "if (p.get" + subtree.getMethod + "() != null) { set.add("
                       + subtree.name.toUpperCase() + "_TAG) }\n";
      } else {
        getTagsCode += "if (p.get" + subtree.getMethod + "() != null && !p.get"
                       + subtree.getMethod + "().isEmpty()) { set.add(" + subtree.name.toUpperCase() + "_TAG) } \n";
      }
    }
    Map<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put("@COMP_CLASS@", ParserGeneratorUtil.getRulePsiClassName(rule, psiClassPrefix));
    replaceMap.put("@GET_TAGS@", getTagsCode);
    String templateContent = readTemplate("ComponentGetTags.txt");
    return replaceTemplates(templateContent, replaceMap);
  }

  private String getGetTemplateText(BnfRule rule) {
    String templateContent = readTemplate("ComponentGetTemplate.txt");
    List<Subtree> subtrees = getSubtreesForRule(rule);
    String subtreesAddCode = "";
    for (Subtree subtree : subtrees) {
      if (subtree.isRequired) {
        subtreesAddCode += "if (!add" + StringUtil.capitalize(subtree.name)
                           + "ToInsertPlaceMap(newP, insertPlaceMap, negShift)) { return null }\n";
      }
      else {
        subtreesAddCode += "add" + StringUtil.capitalize(subtree.name) + "ToInsertPlaceMap(newP, insertPlaceMap, negShift)";
      }
    }

    Map<String, String> replaceMap = ImmutableMap.of(
      "@COMP_CLASS@", ParserGeneratorUtil.getRulePsiClassName(rule, psiClassPrefix),
      "@ADD_SUBTREES@", subtreesAddCode
    );
    return replaceTemplates(templateContent, replaceMap);
  }

  private List<Subtree> getSubtreesForRule(BnfRule rule) {
    return mySubtreeMap.get(rule);
  }

  private List<Subtree> createSubtreesList(BnfRule rule) {
    // TODO: get subtrees
    List<Subtree> subtrees = new ArrayList<Subtree>();
    subtrees.add(new Subtree("condition", "Bexpr",true, true, false, false));
    subtrees.add(new Subtree("thenBranch", "ThenBranch", true, true, false, false));
    subtrees.add(new Subtree("elseBranch", "ElseBranch", false, true, false, false));
    return subtrees;
  }

  private Map<BnfRule, List<Subtree>> createSubtreeMap() {
    Map<BnfRule, List<Subtree>> map = new HashMap<BnfRule, List<Subtree>>();
    for (BnfRule rule : mySignificantRules.values()) {
      map.put(rule, createSubtreesList(rule));
    }
    return map;
  }

  public static String getBeautifulName(String s) {
    return StringUtil.capitalizeWords(s, "_", true, true).replaceAll("_", "");
  }

  private String readTemplate(String name) {
    String templateContent;
    try {
      templateContent = FileUtil.loadFile(new File(pathToTemplates + name));
    } catch (IOException e) {
      return "";
    }
    return templateContent;
  }

  private static String replaceTemplates(String template, Map<String, String> replaceMap) {
    for (Map.Entry<String, String> entry : replaceMap.entrySet()) {
      template = template.replaceAll(entry.getKey(), entry.getValue());
    }
    return template;
  }
}
