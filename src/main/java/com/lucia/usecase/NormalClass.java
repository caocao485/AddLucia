package com.lucia.usecase;

import org.example.AnotherInterface;
import org.example.annotations.Name;
import org.example.annotations.ToString;

@ToString
public class NormalClass {

  @Name private String stringField;

  @Name private int intField;

  private boolean aBoolean;

  public String getStringField() {
    return stringField;
  }

  public int getIntField() {
    return intField;
  }

  //    @AutoService(AnotherInterface.class)
  class serviceClass implements AnotherInterface {}
}
