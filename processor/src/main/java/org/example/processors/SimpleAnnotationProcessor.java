package org.example.processors;

import com.google.auto.service.AutoService;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeTranslator;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

@AutoService(Processor.class)
@SupportedAnnotationTypes("org.example.annotations.Immutable")
@SupportedOptions({"policy"})
public class SimpleAnnotationProcessor extends AbstractProcessor {

  private ProcessingEnvironment processingEnvironment;
  private Trees trees;
  private JavacTrees javacTrees;
  private Policy currentPolicy;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.processingEnvironment = processingEnv;
    this.trees = Trees.instance(processingEnv);
    this.javacTrees = JavacTrees.instance(processingEnv);
    if (processingEnv.getOptions().containsKey("policy")) {
      this.currentPolicy = Policy.valueOf(processingEnv.getOptions().get("policy"));
    } else {
      this.currentPolicy = Policy.ANA;
    }
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    this.getSupportedAnnotationTypes().stream()
        .map(processingEnvironment.getElementUtils()::getTypeElement)
        .map(roundEnv::getElementsAnnotatedWith)
        .flatMap(Set::stream)
        .filter(element -> element.getKind() == ElementKind.CLASS) // find typeElement
        .map(TypeElement.class::cast)
        .forEach(this::processByPolicy);
    return true;
  }

  private void processByPolicy(TypeElement typeElement) {
    switch (this.currentPolicy) {
      case ANA:
        this.scanDefs(typeElement);
        break;
      case GEN:
        this.generateCode(typeElement);
        break;
      case MOD:
        this.modifyCode(typeElement);
        break;
    }
  }

  private void modifyCode(TypeElement typeElement) {
    TreePath treePath = trees.getPath(typeElement);

    final TreePathScanner<Object, CompilationUnitTree> scanner =
        new TreePathScanner<>() {
          @Override
          public Object visitClass(ClassTree node, CompilationUnitTree compilationUnitTree) {
            if (compilationUnitTree instanceof JCTree.JCCompilationUnit) {
              final JCTree.JCCompilationUnit jcCompilationUnit =
                  (JCTree.JCCompilationUnit) compilationUnitTree;
              if (jcCompilationUnit.sourcefile.getKind() == JavaFileObject.Kind.SOURCE) {
                jcCompilationUnit.accept(
                    new TreeTranslator() {
                      @Override
                      public void visitVarDef(JCTree.JCVariableDecl tree) {
                        super.visitVarDef(tree);
                        if ((tree.mods.flags & Flags.FINAL) == 0) {
                          tree.mods.flags |= Flags.FINAL;
                        }
                      }
                    });
              }
            }
            return trees;
          }
        };

    scanner.scan(treePath, treePath.getCompilationUnit());
  }

  private void modCode(TypeElement typeElement) {
    javacTrees
        .getTree(typeElement)
        .accept(
            new TreeTranslator() {
              @Override
              public void visitVarDef(JCTree.JCVariableDecl tree) {
                super.visitVarDef(tree);
                if ((tree.mods.flags & Flags.FINAL) == 0) {
                  tree.mods.flags |= Flags.FINAL;
                }
              }
            });
  }

  private void generateCode(TypeElement typeElement) {
    String className = typeElement.getSimpleName() + "Immutable";
    String packageName =
        ((PackageElement) typeElement.getEnclosingElement()).getQualifiedName().toString();
    String qualifiedClassName = packageName + "." + className;
    try {
      JavaFileObject javaFileObject =
          processingEnvironment.getFiler().createSourceFile(qualifiedClassName);
      try (Writer writer = javaFileObject.openWriter()) {
        writer.append("package ").append(packageName).append(";");
        writer.append("\n\n");
        writer.append("class ").append(className).append(" {");
        writer.append("\n");
        writer.append("}");
      }
    } catch (IOException ioException) {
      processingEnvironment
          .getMessager()
          .printMessage(Diagnostic.Kind.ERROR, ioException.getMessage());
    }
  }

  private void scanDefs(TypeElement typeElement) {
    for (Element element : typeElement.getEnclosedElements()) {
      if (element.getKind().isField() && !element.getModifiers().contains(Modifier.FINAL)) {
        processingEnvironment
            .getMessager()
            .printMessage(
                Diagnostic.Kind.WARNING,
                String.format(
                    "Class '%s' is annotated by @Immutable, but field '%s' is not declared by final",
                    typeElement.getSimpleName(), element.getSimpleName()));
      }
    }
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  enum Policy {
    GEN,
    ANA,
    MOD
  }
}
