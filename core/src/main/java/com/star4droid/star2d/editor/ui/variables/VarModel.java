package com.star4droid.star2d.editor.ui.variables;

import java.util.Objects;

public class VarModel {

  private String accessModifer = "", type = "", name = "", value = "";
  public final String SPACE = " ", EQUAL = "=", SEMICOLON = ";";

  public VarModel setAccessModifer(String s) {
    accessModifer = s == null ? "" : s;
    return this;
  }

  public VarModel setType(String s) {
    type = s == null ? "" : s;
    return this;
  }

  public VarModel setName(String s) {
    name = s == null ? "" : s;
    return this;
  }

  public VarModel setValue(String s) {
    value = s == null ? "" : s;
    return this;
  }

  public String getAccessModifer() {
    return accessModifer;
  }

  public String getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public String getValue() {
    return value;
  }

  public String getCode() {
    return (accessModifer.equals("") ? (accessModifer + SPACE) : "")
        + type
        + SPACE
        + name
        + SPACE
        + (value.equals("") ? "" : EQUAL + SPACE + value)
        + SEMICOLON;
  }

  @Override
  public String toString() {
    return getCode();
  }

  @Override
  public int hashCode() {
    return Objects.hash(accessModifer, type, name, value);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;
    VarModel other = (VarModel) obj;
    return accessModifer.equals(other.accessModifer)
        && type.equals(other.type)
        && name.equals(other.name)
        && value.equals(other.value);
  }
}
