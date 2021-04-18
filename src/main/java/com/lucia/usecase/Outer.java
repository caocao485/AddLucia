package com.lucia.usecase;

import org.example.annotations.Setter;

@Setter
public class Outer {
  private static String staticField;
  private final String FINAL_NAME_ = "NOT_SETTER";
  private boolean isLucia;
  private String name;

  public void MethodHasLocalClass() {
    @Setter
    class Local {
      private boolean localBool;
    }
  }

  @Setter
  enum EnumObject {
    ONE
  }

  @Setter
  interface InterfaceObject {
    String interfaceName = "";
  }

  @Setter
  class inner {
    private boolean isInInner;
    private String innerName;
  }
}
