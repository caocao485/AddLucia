//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.lucia.usecase;

public class ToStringFact {
  public ToStringFact() {}

  public static String toString(Bit arg) {
    String str = "Bit{ ";
    str = str + "field=" + arg.getField();
    str = str + ", intField=" + arg.getIntField();
    str = str + " }";
    return str;
  }

  public static String toString(NormalClass arg) {
    String str = "NormalClass{ ";
    str = str + "stringField=" + arg.getStringField();
    str = str + ", intField=" + arg.getIntField();
    str = str + " }";
    return str;
  }
}
