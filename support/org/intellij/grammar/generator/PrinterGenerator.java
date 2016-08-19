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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
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
  public static final Logger LOG = Logger.getInstance("PrinterGenerator");

  final BnfFile myFile;
  final RuleGraphHelper myGraphHelper;
  final ExpressionHelper myExpressionHelper;
  final Map<String, String> mySimpleTokens;
  final GenOptions myGenOptions;
  final RuleMethodsHelper myRuleMethodsHelper;

  // Placeholders
  final private String LANG = "@LANG@";
  final private String NAME_CC = "@NAME_CC";
  final private String LANG_PACKAGE = "@LANG_PACKAGE@";
  final private String COMP_PACKAGE = "@COMP_PACKAGE@";
  final private String FILE_CLASS = "@FILE_CLASS@";
  final private String FILE_CLASS_NAME = "@FILE_CLASS_NAME@";
  final private String FILE_COMP_PASC = "@FILE_COMP_PASC@";
  final private String FILE_COMP = "@FILE_COMP@";
  final private String COMP_DECLARATION = "@COMP_DECLARATION@";
  final private String APPLY_TEMPLATE = "@APPLY_TEMPLATE@";
  final private String GET_VARIANTS = "@GET_VARIANTS@";
  final private String GET_SAVE_TEMPLATE = "@GET_SAVE_TEMPLATE@";
  final private String FACTORY_CREATE = "@FACTORY_CREATE@";
  final private String COUNT_TEMPLATES = "@COUNT_TEMPLATES@";
  final private String GET_TMPLT = "@GET_TMPLT@";
  final private String FACTORY_CLASS = "@FACTORY_CLASS@";
  final private String IMPORT_LIST = "@IMPORT_LIST@";
  final private String COMP_CLASS = "@COMP_CLASS@";
  final private String DECL_TAGS = "@DECL_TAGS@";
  final private String GEN_SUBTREES = "@GEN_SUBTREES@";
  final private String GET_NEW_ELEM = "@GET_NEW_ELEM@";
  final private String PREPARE_SUBTREES = "@PREPARE_SUBTREES@";
  final private String UPDATE_SUBTREES = "@UPDATE_SUBTREES@";
  final private String GET_TAGS = "@GET_TAGS@";
  final private String IS_TEMPL_SUIT = "@IS_TEMPL_SUIT@";
  final private String GET_TEMPLATE = "@GET_TEMPLATE@";
  final private String LIST_SEP = "@LIST_SEP@";
  final private String SUBTREE_GET = "@SUBTREE_GET@";
  final private String GET_SUBTREES = "@GET_SUBTREES@";
  final private String GET_SUBTREES_VARIANTS = "@GET_SUBTREES_VARIANTS@";
  final private String ADD_SUBTREES = "@ADD_SUBTREES@";
  final private String PREP_SUBTREES = "@PREP_SUBTREES@";
  final private String NAME = "@NAME@";
  final private String NAME_CAP = "@NAME_CAP@";
  final private String EVERYWHERE_SUIT = "@EVERYWHERE_SUIT@";
  final private String ELEMENT_FACTORY = "@ELEMENT_FACTORY@";
  final private String FROM_TEXT = "@FROM_TEXT@";
  final private String TEMPLATE = "@TEMPLATE@";
  final private String TEMPL_SUIT = "@TEMPL_SUIT@";


  final Map<String, BnfRule> mySignificantRules;
  final BnfRule myGrammarRoot;
  final String languageName;
  final String printerPackage;
  final String elementFactoryPath;
  final String fileClass;
  final String fileClassName;
  final String fileExtension;
  final String psiClassPrefix;
  final String elementFactoryClassName;
  final String componentPackage;
  final Map<BnfRule, List<Subtree>> mySubtreeMap;
  final String myOutputPath;
  final String fileSubtreesAttr;
  private final String pathToTemplates = "support/resources/printerGenerator/";

  public PrinterGenerator(BnfFile f, String outputPath) {
    myFile = f;
    myOutputPath = outputPath;
    myGraphHelper = RuleGraphHelper.getCached(myFile);
    myExpressionHelper = new ExpressionHelper(myFile, myGraphHelper, true);
    mySimpleTokens = ContainerUtil.newLinkedHashMap(RuleGraphHelper.getTokenMap(myFile));
    myGenOptions = new GenOptions(myFile);
    myRuleMethodsHelper = new RuleMethodsHelper(myGraphHelper, myExpressionHelper, mySimpleTokens, myGenOptions);

    myGrammarRoot = myFile.getRules().get(0);
    mySignificantRules = new LinkedHashMap<String, BnfRule>();
    createRuleMap();
    languageName = StringUtil.capitalize(myFile.getName().substring(0, myFile.getName().indexOf('.')));
    printerPackage = myFile.findAttributeValue(null, KnownAttribute.PRINTER_PACKAGE, null);
    elementFactoryPath = myFile.findAttributeValue(null, KnownAttribute.FACTORY_CLASS, null);
    fileClass = myFile.findAttributeValue(null, KnownAttribute.FILE_CLASS, null);
    fileClassName = StringUtil.getShortName(fileClass);
    fileExtension = myFile.findAttributeValue(null, KnownAttribute.FILE_EXTENSION, null);
    psiClassPrefix = myFile.findAttributeValue(null, KnownAttribute.PSI_CLASS_PREFIX, null);
    if (elementFactoryPath != null) {
      elementFactoryClassName = StringUtil.getShortName(elementFactoryPath);
    } else {
      elementFactoryClassName = "";
    }
    componentPackage = myFile.findAttributeValue(null, KnownAttribute.PSI_PACKAGE, null);
    fileSubtreesAttr = myFile.findAttributeValue(null, KnownAttribute.FILE_SUBTREES, null);
    mySubtreeMap = createSubtreeMap();
  }

  private List<Subtree> getFileSubtrees(String fileSubtreesAttr) {
    if (fileSubtreesAttr == null) return null;
    String[] subtreesStr = fileSubtreesAttr.split(",");
    List<Subtree> subtrees = new ArrayList<Subtree>();
    for (String subtreeStr : subtreesStr) {
      String name = subtreeStr.replaceAll("\\s|\\*|!", "");
      String getMethod = StringUtil.capitalize(name);
      boolean isRequired = subtreeStr.contains("!");
      boolean hasSeveralElements = subtreeStr.contains("*");
      subtrees.add(new Subtree(name, getMethod, isRequired, true, hasSeveralElements));
    }
    return subtrees;
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
    String compDeclaration = "";
    String applyTmplt = "";
    String getVariants = "";
    String getSaveTemplate = "";
    String factoryCreate = "";
    String countTemplates = "";
    String getTmplt = "";
    for (BnfRule rule : mySignificantRules.values()) {
      if (myGrammarRoot.equals(rule))
        continue;
      String ruleName = getBeautifulName(rule.getName());
      String psiClassName = psiClassPrefix + ruleName;
      String psiComponentClass = ruleName + "Component";
      String psiComponentName = StringUtil.decapitalize(psiComponentClass);
      compDeclaration += "val " + psiComponentName + ": " + psiComponentClass + " = " + psiComponentClass + "(this)\n";
      applyTmplt += "is " + psiClassName + " -> applyTmplt(p)\n";
      getVariants += "is " + psiClassName + " -> " + psiComponentName + ".getVariants(p, context)\n";
      getSaveTemplate += "is " + psiClassName + " -> " + psiComponentName + ".getAndSaveTemplate(p)\n";
      factoryCreate += "is " + psiClassName + " -> factory.create" + ruleName + "FromText(text)\n";
      countTemplates += /*"+ " + */psiComponentName + ".getTemplates().size + \n";
      getTmplt += "is " + psiClassName + " -> " + psiComponentName + ".getTmplt(p)\n";
    }
    countTemplates += " + 0";

    final Map<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put(LANG, languageName);
    replaceMap.put(LANG_PACKAGE, printerPackage);
    replaceMap.put(COMP_PACKAGE, componentPackage);
    replaceMap.put(FILE_CLASS, fileClass);
    replaceMap.put(FILE_CLASS_NAME, StringUtil.getShortName(fileClass));
    replaceMap.put(FACTORY_CLASS, elementFactoryPath);
    replaceMap.put(FILE_COMP_PASC, StringUtil.decapitalize(fileClassName) + "Component");
    replaceMap.put(FILE_COMP, fileClassName + "Component");
    replaceMap.put(COMP_DECLARATION, compDeclaration);
    replaceMap.put(APPLY_TEMPLATE, applyTmplt);
    replaceMap.put(GET_VARIANTS, getVariants);
    replaceMap.put(GET_SAVE_TEMPLATE, getSaveTemplate);
    replaceMap.put(FACTORY_CREATE, factoryCreate);
    replaceMap.put(COUNT_TEMPLATES, countTemplates);
    replaceMap.put(GET_TMPLT, getTmplt);

    String templateContent = readTemplate("Printer.txt");

    return replaceTemplates(templateContent, replaceMap);
  }

  private String getPsiElementComponentText() {
    final Map<String, String> replaceMap = ImmutableMap.of(
      LANG_PACKAGE, printerPackage,
      LANG        , languageName
    );
    String templateContent = readTemplate("PsiElementComponent.txt");
    return replaceTemplates(templateContent, replaceMap);
  }

  public void generatePrinterFiles() {
    try {
      String genPath = myOutputPath;
      FileUtil.writeToFile(new File(genPath + "templateBase/" + languageName + "PsiElementComponent.kt")
        , getPsiElementComponentText());
      FileUtil.writeToFile(new File(genPath + "printer/" + languageName + "Printer.kt"), getPrinterText());

      String componentsPath = genPath + "components/";
      for (BnfRule rule : mySignificantRules.values()) {
        String componentName = getBeautifulName(rule.getName());
        String componentFileName = componentsPath + componentName + ".kt";
        if (rule.equals(myGrammarRoot)) {
          FileUtil.writeToFile(new File(componentsPath + languageName + "File.kt"), getFileComponentText());
          continue;
        }
        if (ParserGeneratorUtil.Rule.isList(rule)) {
          FileUtil.writeToFile(new File(componentFileName), getListComponentText(rule));
        } else {
          FileUtil.writeToFile(new File(componentFileName), getCommonComponentText(rule));
        }
      }
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private String getCommonComponentText(BnfRule rule) {
    String templateContent = readTemplate("Component.txt");
    Map<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put(LANG_PACKAGE, printerPackage);
    replaceMap.put(IMPORT_LIST, getImportListText());
    replaceMap.put(COMP_PACKAGE, componentPackage);
    replaceMap.put(COMP_CLASS, ParserGeneratorUtil.getRulePsiClassName(rule, psiClassPrefix));
    replaceMap.put(NAME_CC, getBeautifulName(rule.getName()));
    replaceMap.put(LANG, languageName);
    replaceMap.put(DECL_TAGS, getTagsDeclaration(rule));
    replaceMap.put(GEN_SUBTREES, getSubtreesMethodsText(rule));
    replaceMap.put(GET_NEW_ELEM, getGetNewElementText(rule));
    replaceMap.put(PREPARE_SUBTREES, getPrepareSubtreesText(rule));
    replaceMap.put(UPDATE_SUBTREES, getUpdateSubtreesText(rule));
    replaceMap.put(GET_TAGS, getGetTagsText(rule));
    replaceMap.put(IS_TEMPL_SUIT, getIsTemplateSuitableText(rule));
    replaceMap.put(GET_TEMPLATE, getGetTemplateText(rule));

    return replaceTemplates(templateContent, replaceMap);
  }

  private String getListComponentText(BnfRule rule) {
    String templateContent = readTemplate("ListComponent.txt");
    Map<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put(LANG_PACKAGE, printerPackage);
    //replaceMap.put(FACTORY_CLASS, elementFactoryPath);
    replaceMap.put(LIST_SEP, ParserGeneratorUtil.getAttribute(rule, KnownAttribute.LIST_SEP));
    replaceMap.put(LANG, languageName);
    replaceMap.put(COMP_CLASS, ParserGeneratorUtil.getRulePsiClassName(rule, psiClassPrefix));
    replaceMap.put(COMP_PACKAGE, componentPackage);
    replaceMap.put(NAME_CC, getBeautifulName(rule.getName()));
    replaceMap.put(GET_NEW_ELEM, getGetNewElementText(rule));
    replaceMap.put(IS_TEMPL_SUIT, getIsTemplateSuitableText(rule));
    Subtree subtree = getSubtreesForRule(rule).get(0);
    replaceMap.put(SUBTREE_GET, subtree.getMethod);

    return replaceTemplates(templateContent, replaceMap);
  }

  private String getFileComponentText() {
    Map<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put(LANG_PACKAGE, printerPackage);
    replaceMap.put(FILE_CLASS, fileClass);
    //replaceMap.put(FILE_CLASS_NAME, StringUtil.getShortName(fileClass));
    replaceMap.put(LANG, languageName);
    String componentClass = StringUtil.getShortName(fileClass);
    replaceMap.put(COMP_CLASS, componentClass);
    String componentName = StringUtil.trimStart(componentClass, psiClassPrefix);
    replaceMap.put(NAME_CC, componentName);
    String getSubtreesText = "";
    String getSubtreesVariantsText = "";
    for (Subtree subtree : getSubtreesForRule(myGrammarRoot)) {
      getSubtreesText += getFileGetSubtreesText(subtree);
      getSubtreesVariantsText += getFileGetSubtreeVariantsText(subtree);
    }
    replaceMap.put(GET_SUBTREES, getSubtreesText);
    replaceMap.put(GET_SUBTREES_VARIANTS, getSubtreesVariantsText);

    String templateContent = readTemplate("FileComponent.txt");

    return replaceTemplates(templateContent, replaceMap);
  }

  private class Subtree {
    private Subtree(
      String name,
      String getMethod,
      boolean isRequired,
      boolean isEverywhereSuitable,
      boolean hasSeveralElements
    ) {
      this.name = name;
      this.getMethod = getMethod;
      this.isRequired = isRequired;
      this.isEverywhereSuitable = isEverywhereSuitable;
      this.hasSeveralElements = hasSeveralElements;
      this.ruleName = null;
    }

    private Subtree(
      String name,
      String getMethod,
      boolean isRequired,
      boolean isEverywhereSuitable,
      boolean hasSeveralElements,
      String ruleName
    ) {
      this(name, getMethod, isRequired, isEverywhereSuitable, hasSeveralElements);
      this.ruleName = ruleName;
    }
    public String name;
    public String getMethod;
    public boolean isRequired;
    public boolean isEverywhereSuitable;
    public boolean hasSeveralElements;
    String ruleName;
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
      COMP_CLASS, ParserGeneratorUtil.getRulePsiClassName(rule, psiClassPrefix),
      ELEMENT_FACTORY, elementFactoryClassName,
      FROM_TEXT, getBeautifulName(rule.getName())
    );
    return replaceTemplates(templateContent, replaceMap);
  }

  private String getImportListText() {
    String templateContent = readTemplate("ImportList.txt");
    Map<String, String> replaceMap = ImmutableMap.of(
      //FACTORY_CLASS, elementFactoryPath,
      LANG_PACKAGE, printerPackage,
      LANG, languageName
    );
    return replaceTemplates(templateContent, replaceMap);
  }

  private String getIsTemplateSuitableText(BnfRule rule) {
    String templateContent = readTemplate("ComponentIsTemplSuit.txt");
    String templateInsertPlace;
    if (ParserGeneratorUtil.Rule.isList(rule)) {
      templateInsertPlace = "ListTemplate";
    } else {
      templateInsertPlace = "PsiTemplateGen";
    }
    Map<String, String> replaceMap = ImmutableMap.of(
      COMP_CLASS, ParserGeneratorUtil.getRulePsiClassName(rule, psiClassPrefix),
      TEMPLATE, templateInsertPlace,
      TEMPL_SUIT, "return true"
    );
    return replaceTemplates(templateContent, replaceMap);
  }

  private String getFileGetSubtreeVariantsText(Subtree subtree) {
    String templateContent = readTemplate("FileGetSubtreeVariants.txt");

    Map<String, String> replaceMap = ImmutableMap.of(
      NAME, subtree.name,
      NAME_CC, StringUtil.capitalize(subtree.name)
    );
    return replaceTemplates(templateContent, replaceMap);
  }

  private String getFileGetSubtreesText(Subtree subtree) {
    String templateContent = readTemplate(!subtree.hasSeveralElements? "ComponentGetSubtree.txt" : "SEComponentGetSubtree.txt");
    Map<String, String> replaceMap = ImmutableMap.of(
      NAME_CC, StringUtil.capitalize(subtree.name),
      COMP_CLASS, StringUtil.getShortName(fileClass),
      NAME, subtree.name,
      SUBTREE_GET, subtree.getMethod
    );
    return replaceTemplates(templateContent, replaceMap);
  }

  private String getSubtreeMethodText(Subtree subtree, BnfRule rule, String fileName) {
    String templateContent = readTemplate(fileName);
    Map<String, String> replaceMap = new HashMap<String, String>();
    replaceMap.put(NAME_CC, StringUtil.capitalize(subtree.name));
    replaceMap.put(COMP_CLASS, ParserGeneratorUtil.getRulePsiClassName(rule, psiClassPrefix));
    replaceMap.put(NAME, subtree.name);
    replaceMap.put(SUBTREE_GET, subtree.getMethod);
    replaceMap.put(NAME_CAP, subtree.name.toUpperCase());
    String everywhereSuit;
    if (subtree.isEverywhereSuitable) {
      everywhereSuit = "Box.getEverywhereSuitable()\n";
    } else {
      everywhereSuit = subtree.name + "!!.toBox()\n";
    }
    replaceMap.put(EVERYWHERE_SUIT, everywhereSuit);
    return replaceTemplates(templateContent, replaceMap);
  }

  private String getSubtreesMethodsText(BnfRule rule) {
    List<Subtree> subtrees = getSubtreesForRule(rule);
    final String[] files =
      { "ComponentAddSubtree.txt", "ComponentGetSubtree.txt", "ComponentPrepareSubtree.txt", "SEComponentGetSubtree.txt" };
    String subtreesMethodsText = "";
    for (Subtree subtree : subtrees) {
      subtreesMethodsText +=
        getSubtreeMethodText(subtree, rule, files[0]) + "\n" +
        getSubtreeMethodText(subtree, rule, files[2]) + "\n" +
        getSubtreeMethodText(subtree, rule, !subtree.hasSeveralElements ? files[1] : files[3]) + "\n";
    }
    return subtreesMethodsText;
  }

  private String getUpdateSubtreesText(BnfRule rule) {
    String templateContent = readTemplate("ComponentUpdateSubtrees.txt");
    String updateSubtreeCode = "return variants";
    Map<String, String> replaceMap = ImmutableMap.of(
      COMP_CLASS, ParserGeneratorUtil.getRulePsiClassName(rule, psiClassPrefix),
      UPDATE_SUBTREES, updateSubtreeCode
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
      COMP_CLASS, ParserGeneratorUtil.getRulePsiClassName(rule, psiClassPrefix),
      PREP_SUBTREES, prepSubtreesCode
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
    replaceMap.put(COMP_CLASS, ParserGeneratorUtil.getRulePsiClassName(rule, psiClassPrefix));
    replaceMap.put(GET_TAGS, getTagsCode);
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
        subtreesAddCode += "add" + StringUtil.capitalize(subtree.name) + "ToInsertPlaceMap(newP, insertPlaceMap, negShift)\n";
      }
    }

    Map<String, String> replaceMap = ImmutableMap.of(
      COMP_CLASS, ParserGeneratorUtil.getRulePsiClassName(rule, psiClassPrefix),
      ADD_SUBTREES, subtreesAddCode
    );
    return replaceTemplates(templateContent, replaceMap);
  }

  private List<Subtree> getSubtreesForRule(BnfRule rule) {
    if (rule.equals(myGrammarRoot)) {
      return getFileSubtrees(fileSubtreesAttr);
    }
    return mySubtreeMap.get(rule);
  }

  private List<Subtree> createSubtreesList(BnfRule rule) {
    List<Subtree> subtrees = new ArrayList<Subtree>();
    Collection<RuleMethodsHelper.MethodInfo> methodInfoList = myRuleMethodsHelper.getFor(rule);
    BnfRule topSuperRule = ParserGeneratorUtil.getTopSuperRule(myFile, rule);
    if (topSuperRule != null && ParserGeneratorUtil.Rule.isFake(topSuperRule)) {
      for (RuleMethodsHelper.MethodInfo methodInfo : myRuleMethodsHelper.getFor(topSuperRule)) {
        if (methodInfo.type == 3) {
          methodInfoList.add(methodInfo);
        }
      }
    }
    for (RuleMethodsHelper.MethodInfo methodInfo : methodInfoList) {
      if (methodInfo.name.isEmpty()) continue;
      switch (methodInfo.type) {
        case 1:
        case 2:
          Subtree subtree12 = genType12(methodInfo);
          if (subtree12 != null) {
            subtrees.add(subtree12);
          }
          break;
        case 3:
          Subtree subtree3 = genType3(rule, methodInfo);
          if (subtree3 == null) continue;
          if (subtree3.ruleName != null) {
            int i = 0;
            int j = subtrees.size();
            while (i < j) {
              if (subtree3.ruleName.equals(subtrees.get(i).name)) {
                subtrees.remove(i);
                j--;
              }
              i++;
            }
          }
          subtrees.add(subtree3);
          break;
        default:
          break;
      }
    }
    return subtrees;
  }

  private Subtree genType12(RuleMethodsHelper.MethodInfo methodInfo) {
    RuleGraphHelper.Cardinality cardinality = methodInfo.cardinality;
    if (methodInfo.rule == null && methodInfo.name.isEmpty()) { return null; }  // it is a token
    boolean many = cardinality.many();

    String subtreeName = getBeautifulName(methodInfo.name);
    String getMethod = ParserGeneratorUtil.toIdentifier(methodInfo.name, "");
    if (methodInfo.cardinality.many()) {
      getMethod += "List";
    }
    boolean isRequired = cardinality == RuleGraphHelper.Cardinality.REQUIRED;
    boolean isEverywhereSuitable = true;

    return new Subtree(subtreeName, getMethod, isRequired, isEverywhereSuitable, many);
  }

  private Subtree genType3(BnfRule startRule, RuleMethodsHelper.MethodInfo methodInfo) {
    BnfRule targetRule = startRule;
    RuleGraphHelper.Cardinality cardinality = RuleGraphHelper.Cardinality.REQUIRED;
    String context = "";
    String [] splitPath = methodInfo.path.split("/");
    boolean totalNullable = false;

    for (int i = 0; i < splitPath.length; i++) {
      String pathElement = splitPath[i];
      int indexStart = pathElement.indexOf('[');
      int indexEnd = indexStart > 0 ? pathElement.lastIndexOf(']') : -1;

      String item = indexEnd > -1 ? pathElement.substring(0, indexStart).trim() : pathElement.trim();
      String index = indexEnd > -1 ? pathElement.substring(indexStart + 1, indexEnd).trim() : null;
      if ("first".equals(index)) index = "0";

      if (item.isEmpty()) continue;

      RuleMethodsHelper.MethodInfo targetInfo = myRuleMethodsHelper.getMethodInfo(targetRule, item);
      if (targetInfo == null ||
          index != null && !targetInfo.cardinality.many() ||
          i > 0 && StringUtil.isEmpty(targetInfo.name) && targetInfo.rule == null) {
        return null;
      }
      targetRule = targetInfo.rule;
      cardinality = targetInfo.cardinality;
      totalNullable |= cardinality.optional();
      if (index != null) {
        boolean isLast = index.equals("last");
        if (isLast)
          index = context + "size() - 1";

        cardinality = cardinality == RuleGraphHelper.Cardinality.AT_LEAST_ONE
                      && index.equals("0") ? RuleGraphHelper.Cardinality.REQUIRED : RuleGraphHelper.Cardinality.AT_LEAST_ONE;
        totalNullable |= cardinality.optional();
      }
    }
    String subtreeName = StringUtil.decapitalize(getBeautifulName(methodInfo.name));
    String subtreeGet = ParserGeneratorUtil.toIdentifier(methodInfo.name, "");
    boolean isRequired = !cardinality.many() && cardinality == RuleGraphHelper.Cardinality.REQUIRED && !totalNullable;
    boolean hasSeveralElements = false;
    boolean isEverywhereSuitable = true;
    return new Subtree(subtreeName, StringUtil.capitalize(subtreeGet), isRequired, isEverywhereSuitable, hasSeveralElements,
                       ParserGeneratorUtil.toIdentifier(targetRule.getName(), ""));
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
      LOG.error(e);
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
