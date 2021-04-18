package org.example.processors;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import org.example.annotations.Name;
import org.example.annotations.ToString;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static javax.lang.model.element.ElementKind.FIELD;

@SupportedAnnotationTypes("org.example.annotations.ToString")
@AutoService(Processor.class)
@SupportedOptions({"hello"})
public class ToStringProcessor extends AbstractProcessor {

  private Messager messager;
  private Filer filer;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    messager = processingEnv.getMessager();
    filer = processingEnv.getFiler();
    //   System.out.println(processingEnv.getOptions().get("hello"));
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    List<TypeElement> typeElements =
        roundEnv.getElementsAnnotatedWith(ToString.class).stream()
            .filter(element -> element.getKind() == ElementKind.CLASS)
            .map(TypeElement.class::cast)
            .collect(Collectors.toList());

    try {
      if (!typeElements.isEmpty()) {
        generateCode(typeElements);
      }
    } catch (IOException ioException) {
      messager.printMessage(Diagnostic.Kind.NOTE, "generate method " + ioException);
    }

    return true;
  }

  private void generateCode(List<TypeElement> typeElements) throws IOException {
    List<MethodSpec> listBuffer = new ArrayList<>();
    typeElements.forEach(typeElement -> listBuffer.add(generateMethod(typeElement)));

    TypeSpec toStringFactory =
        TypeSpec.classBuilder("ToStringFactory")
            .addModifiers(Modifier.PUBLIC)
            .addMethods(listBuffer)
            .build();

    messager.printMessage(Diagnostic.Kind.NOTE, "generate method " + toStringFactory);
    JavaFile javaFile = JavaFile.builder("com.lucia.usecase", toStringFactory).build();
    // bug new File("F:\\little\\tomcat\\demo\\addLucia\\src\\main\\java")
    javaFile.writeTo(filer);
  }

  private MethodSpec generateMethod(TypeElement typeElement) {
    MethodSpec.Builder methodBuilder =
        MethodSpec.methodBuilder("toString")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .returns(String.class)
            .addParameter(TypeName.get(typeElement.asType()), "arg");

    CodeBlock.Builder strBlockBuilder =
        CodeBlock.builder().addStatement("String str = \"$T{ \"", typeElement);
    boolean isFirst = true;
    for (Element element : typeElement.getEnclosedElements()) {
      if (element.getKind() == FIELD && element.getAnnotation(Name.class) != null) {

        if (isFirst) {
          strBlockBuilder.addStatement(
              "str  = str + \"$L=\" + arg.get$L()",
              element,
              firstToUpperCase(element.getSimpleName().toString()));
          isFirst = false;
        } else {
          strBlockBuilder.addStatement(
              "str  = str + \", $L=\" + arg.get$L()",
              element,
              firstToUpperCase(element.getSimpleName().toString()));
        }
      }
    }
    strBlockBuilder.addStatement("str = str + \" }\"").addStatement("return str");

    return methodBuilder.addCode(strBlockBuilder.build()).build();
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  private String firstToUpperCase(String toString) {
    return Character.toUpperCase(toString.charAt(0)) + toString.substring(1);
  }
}
