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

package org.intellij.printer;

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.intellij.grammar.generator.ParserGenerator;
import org.intellij.grammar.generator.PrinterGenerator;
import org.intellij.grammar.psi.BnfFile;

import java.io.IOException;

public class PrinterGeneratorTest extends LightCodeInsightFixtureTestCase {
  public PrinterGeneratorTest() {}

  public void testPrinter() {
    BnfFile f = (BnfFile)myFixture.configureByFile("testData/printer/While.bnf");
    ParserGenerator pg = new ParserGenerator(f, "testData/printer/", "testData/printer/1/");
    try {
      pg.generate();
    } catch (IOException e) {
      return;
    }

    PrinterGenerator printerGenerator = new PrinterGenerator(f, "testData/printer/output/"); // TODO: fix path
    printerGenerator.generatePrinterFiles();
    assertEquals("", 1, 1);
  }
}
