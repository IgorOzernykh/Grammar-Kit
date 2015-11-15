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
import org.intellij.grammar.psi.BnfFile;
import org.intellij.grammar.psi.BnfRule;

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
    languageName = myFile.getName();
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

  public void generatePrinterFiles(String printerPackage, String factoryPackage, String fileExtension) {

  }

  public String printerText() {
    return "";
  }



}
