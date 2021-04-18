package com.lucia.usecase;

import com.google.auto.service.AutoService;
import org.example.AnotherInterface;
import org.example.TestInterface;
import org.example.annotations.Immutable;
import org.example.annotations.Name;

@Immutable
@AutoService(value = {TestInterface.class, AnotherInterface.class})
public class Bit implements TestInterface, AnotherInterface {

  @Name private String field = "";

  @Name private int intField = 1;

  private boolean aBoolean = false;

  public String getField() {
    return field;
  }

  public int getIntField() {
    return intField;
  }
}
