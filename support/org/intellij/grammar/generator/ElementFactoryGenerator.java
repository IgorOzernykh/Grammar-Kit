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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.intellij.grammar.KnownAttribute;
import org.intellij.grammar.psi.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import static java.lang.String.format;

/**
 * Igor Ozernykh on 27.08.2015.
 */

public class ElementFactoryGenerator {
  private final BnfFile myFile;
  private final RuleGraphHelper myGraphHelper;
  private final ExpressionHelper myExpressionHelper;
  private final Map<String, String> mySimpleTokens;
  private final Map<String, String> myReverseTokenMap;

  private final Map<String, BnfRule> mySignificantRules;
  public Map<String, BnfRule> getSignificantRules() {
    return mySignificantRules;
  }

  final GenOptions G;
  private final RuleMethodsHelper myRuleMethodsHelper;
  private PrintWriter myOut;
  private final String mySourcePath;
  private final String myOutputPath;
  private int myOffset = 0;
  private final String psiPrefix;
  private final BnfRule myGrammarRoot;

  private final List<String> myCheckedRules;

  public ElementFactoryGenerator(BnfFile f, String sourcePath, String outputPath) {
    myFile = f;
    myGraphHelper = RuleGraphHelper.getCached(myFile);
    myExpressionHelper = new ExpressionHelper(myFile, myGraphHelper, true);
    mySimpleTokens = ContainerUtil.newLinkedHashMap(RuleGraphHelper.getTokenMap(myFile));
    myReverseTokenMap = ContainerUtil.newLinkedHashMap();
    for (Map.Entry<String, String> entry : mySimpleTokens.entrySet()) {
      myReverseTokenMap.put(entry.getValue(), entry.getKey());
    }
    G = new GenOptions(myFile);
    myRuleMethodsHelper = new RuleMethodsHelper(myGraphHelper, myExpressionHelper, mySimpleTokens, G);
    mySignificantRules = new LinkedHashMap<String, BnfRule>();
    myGrammarRoot = myFile.getRules().get(0);
    mySourcePath = sourcePath;
    myOutputPath = outputPath;
    psiPrefix = ParserGeneratorUtil.getPsiClassPrefix(myFile);
    myCheckedRules = new ArrayList<String>();
    createRuleMap();
  }

  private void openOutput(String className) throws IOException {
    File file = new File(myOutputPath, className.replace('.', File.separatorChar) + ".java");
    myOut = openOutputInner(file);
  }

  protected PrintWriter openOutputInner(File file) throws IOException {
    file.getParentFile().mkdirs();
    return new PrintWriter(new FileOutputStream(file));
  }

  private Map<String, BnfRule> createRuleMap() {
    Map<String, BnfRule> psiRules = new LinkedHashMap<String, BnfRule>();
    psiRules.put(myGrammarRoot.getName(), myGrammarRoot);
    for (BnfRule rule : myFile.getRules()) {
      if (!RuleGraphHelper.shouldGeneratePsi(rule, true)) continue;
      if (ParserGeneratorUtil.Rule.isLeft(rule)) continue;
      String elementType = ParserGeneratorUtil.getElementType(rule, G.generateElementCase);
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

  // TODO: remove duplicate code
  private void closeOutput() {
    myOut.close();
  }

  public void out(String s, Object... args) {
    out(format(s, args));
  }

  public void out(String s) {
    int length = s.length();
    if (length == 0) {
      myOut.println();
      return;
    }
    boolean isComment = s.startsWith("//");
    boolean newStatement = true;
    for (int start = 0, end; start < length; start = end + 1) {
      end = StringUtil.indexOf(s, '\n', start, length);
      if (end == -1) end = length;
      String substring = s.substring(start, end);
      if (!isComment && substring.startsWith("}")) myOffset--;
      if (myOffset > 0) {
        myOut.print(StringUtil.repeat("  ", newStatement ? myOffset : myOffset + 1));
      }
      if (!isComment && substring.endsWith("{")) myOffset++;
      myOut.println(substring);
      newStatement = substring.endsWith(";") || substring.endsWith("{") || substring.endsWith("}");
    }
  }

  private void printFactoryHeader() {
    out("package org.intellij."
        + StringUtil.decapitalize(myFile.getName().replace(".bnf", "")) + "Lang;\n\n");
    out("import com.intellij.openapi.project.Project;");
    out("import com.intellij.psi.PsiFileFactory;");

    String psiPackage = ParserGeneratorUtil.getAttribute(myGrammarRoot, KnownAttribute.PSI_PACKAGE);
    out("import " + psiPackage + ".*;\n\n");
  }

  public void printFactory() {
    final String lang = myFile.getName().replace(".bnf", "");
    try {
      openOutput("WhileElementFactory");
      printFactoryHeader();
      out("public class " + lang + "ElementFactory {");
      out("private Project project;");
      out("public " + lang + "ElementFactory(Project project) { this.project = project; }");
      createFile("l", lang);

      for (BnfRule rule : myFile.getRules()) {
        createFactoryMethods(rule);
      }
      out("}");
      closeOutput();
    } catch (IOException e) {
      closeOutput();
    }
  }

  private void createFile(String extension, String lang) {
    out("public " + lang + "File createFileFromText(String text) {");
    out("return (" + lang + "File)PsiFileFactory.getInstance(project).createFileFromText(\"tmp." + extension + "\", " +
        lang + "Language.INSTANCE, text);");
    out("}");
  }

  private void createFactoryMethods(BnfRule rule) {
    if (!myRuleMethodsHelper.containMethod(rule)) return;
    Collection<RuleMethodsHelper.MethodInfo> methodInfoList = myRuleMethodsHelper.getFor(rule);
    if (methodInfoList.isEmpty()) {
        createMethodTextMap(rule);
    } else {
      for (RuleMethodsHelper.MethodInfo methodInfo : methodInfoList) {
          createMethodTextMap(methodInfo, rule);
      }
    }
  }

  private boolean shouldGenerateMethod(BnfRule rule) {
    if (rule == null) return false;
    if (isRuleChecked(rule)) return false;

    MultiMap<BnfRule, BnfRule> map = myGraphHelper.getRuleExtendsMap();
    if (map.containsKey(rule)) {
      for (BnfRule entry : map.get(rule)) {
        if (isRuleSignificant(entry))
          return true;
      }
    }
    return isRuleSignificant(rule);
  }



  private boolean isRuleSignificant(BnfRule rule) {
    return mySignificantRules.containsValue(rule);
  }

  private void createMethodTextMap(RuleMethodsHelper.MethodInfo methodInfo, BnfRule parent) {
    final BnfRule rule = methodInfo.rule;
    if (!shouldGenerateMethod(rule)) return;

    final String ruleName = methodInfo.name;
    String methodType = makeBeautifulName(ruleName);
    String methodName = ".get" + methodType + (methodInfo.cardinality.many()? "List().get(0);" : "();");
    final String parentName = parent == myGrammarRoot? "File" : makeBeautifulName(parent.getName());
    printMethodHeader(methodType);
    String[] context = getContext(parent, rule).split(ruleName);
    if (context.length < 2) {
      out("return create" + parentName + "FromText(text)" + methodName);
    } else {
      out("return create" + parentName + "FromText(\"" + context[0] + "\" + text + \"" + context[1] + "\")" + methodName);
    }
    out("}");
    myCheckedRules.add(ruleName);
  }

  private boolean isRuleChecked(BnfRule rule) {
    return myCheckedRules.contains(rule.getName());
  }

  private void printMethodHeader(String methodType) {
    out("public " + psiPrefix + methodType + " create" + methodType + "FromText(String text) {");
  }

  private String getContext(BnfRule parent, BnfRule rule) {
    String[] ruleText = parent.getExpression().getText().split(" |\\|");
    String result = "";
    for (String txt : ruleText) {
      if (txt.equals("")) continue;
      if (txt.equals("'('")) {
        result += "(";
      } else if (txt.equals("')'")) {
        result += ")";
      } else if (rule.getName().equals(txt)) {
        result += txt;
      } else if (myReverseTokenMap.containsKey(txt)) {
        String str = myReverseTokenMap.get(txt);
        // TODO: remove hardcode
        if (str.equals("regexp:\\d+(\\.\\d*)?")) {
          result += " 0";
        } else if (str.equals("regexp:\\p{Alpha}\\w*")) {
          result += " a";
        } else {
          result += " " + str;
        }
      }
    }
    return result;
  }

  private void createMethodTextMap(BnfRule parent) { // rule is a parent
    final String parentType = parent == myGrammarRoot? "File" : makeBeautifulName(parent.getName());

    MultiMap<BnfRule, BnfRule> map = myGraphHelper.getRuleExtendsMap();
    if (!map.containsKey(parent)) { return; }
    for (BnfRule rule : map.get(parent)) {
      if (!shouldGenerateMethod(rule)) continue;

      final String methodType = makeBeautifulName(rule.getName());
      printMethodHeader(methodType);
      String[] context = getContext(parent, rule).split(rule.getName());
      if (context.length < 2) {
        out("return (" + psiPrefix + methodType + ")create" + parentType + "FromText(text);");
      } else {
        out(
          "return (" + psiPrefix + methodType + ")create" + parentType + "FromText(\"" + context[0] + "\" + text + \"" + context[1] + "\");");
      }
      out("}");
      myCheckedRules.add(rule.getName());

    }
  }

  public static String makeBeautifulName(String s) {
    return StringUtil.capitalizeWords(s, "_", true, true).replace("_", "");
  }

  private Map<String, BnfExpression> createExprMapForRule(BnfRule rule) {
    return createExprMapForExpr(rule.getExpression());
  }

  private static Map<String, BnfExpression> createExprMapForExpr(BnfExpression expr) {
    Map<String, BnfExpression> map = new HashMap<String, BnfExpression>();
    if (expr instanceof BnfSequence) {
      for (BnfExpression exp : ((BnfSequence)expr).getExpressionList()) {
        map.putAll(createExprMapForExpr(exp));
      }
    } else if (expr instanceof BnfChoice) {
      for (BnfExpression exp : ((BnfChoice)expr).getExpressionList()) {
        map.putAll(createExprMapForExpr(exp));
      }
    } else if (expr instanceof BnfQuantified) {
      map.putAll(createExprMapForExpr(((BnfQuantified)expr).getExpression()));
    } else if (expr instanceof BnfReferenceOrToken) {
      map.put(((BnfReferenceOrToken)expr).getId().getText(), expr);
    }
    return map;
  }
}
