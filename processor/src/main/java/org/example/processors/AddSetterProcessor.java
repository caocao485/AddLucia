package org.example.processors;

import com.google.auto.service.AutoService;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.Set;

@SupportedAnnotationTypes("org.example.annotations.Setter")
@AutoService(Processor.class)
public class AddSetterProcessor extends AbstractProcessor {
  private Elements elementsUtil;
  private JavacTrees treesUtil;
  private TreeTranslator treeTranslator;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    elementsUtil = processingEnv.getElementUtils();
    treesUtil = JavacTrees.instance(processingEnv);
    treeTranslator =
        new AddSetterTreeTranslator(((JavacProcessingEnvironment) processingEnv).getContext());
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    this.getSupportedAnnotationTypes().stream()
        .map(elementsUtil::getTypeElement)
        .map(roundEnv::getElementsAnnotatedWith)
        .flatMap(Set::stream)
        .filter(element -> element.getKind() == ElementKind.CLASS)
        .map(TypeElement.class::cast)
        .map(treesUtil::getTree)
        .forEach(treeTranslator::visitClassDef);
    return true;
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }
}

class AddSetterTreeTranslator extends TreeTranslator {
  private final TreeMaker treeMaker;
  private final Names names;

  AddSetterTreeTranslator(Context context) {
    treeMaker = TreeMaker.instance(context);
    names = Names.instance(context);
  }

  @Override
  public void visitClassDef(JCTree.JCClassDecl tree) {
    //        super.visitClassDef(tree);
    if (tree.getKind() != Tree.Kind.CLASS) {
      return;
    }
    ListBuffer<JCTree> methodDecls =
        tree.defs.stream()
            .filter(decl -> decl.hasTag(JCTree.Tag.VARDEF))
            .map(JCTree.JCVariableDecl.class::cast)
            .filter(
                jcVariableDecl -> {
                  Set<Modifier> modifiers = jcVariableDecl.getModifiers().getFlags();
                  return !modifiers.contains(Modifier.FINAL)
                      && !modifiers.contains(Modifier.STATIC);
                })
            .map(this::generateMethodDecl)
            .collect(ListBuffer::new, ListBuffer::add, ListBuffer::addAll);
    tree.defs = tree.defs.appendList(methodDecls);
  }

  private JCTree.JCMethodDecl generateMethodDecl(JCTree.JCVariableDecl jcVariableDecl) {
    Name varName = jcVariableDecl.getName();

    JCTree.JCExpression returnType = treeMaker.TypeIdent(TypeTag.VOID);

    Name methodName = names.fromString("set" + firstToUpperCase(varName.toString()));

    JCTree.JCVariableDecl paramDecl =
        treeMaker.VarDef(
            treeMaker.Modifiers(Flags.PARAMETER),
            varName,
            (JCTree.JCExpression) jcVariableDecl.getType(),
            null);

    JCTree.JCAssign thisSetter =
        treeMaker.Assign(
            treeMaker.Select(treeMaker.Ident(names.fromString("this")), varName),
            treeMaker.Ident(paramDecl.getName()));
    JCTree.JCBlock body = treeMaker.Block(0, List.of(treeMaker.Exec(thisSetter)));

    return treeMaker.MethodDef(
        treeMaker.Modifiers(Flags.PUBLIC),
        methodName,
        returnType,
        List.nil(),
        List.of(paramDecl),
        List.nil(),
        body,
        null);
  }

  private String firstToUpperCase(String toString) {
    return Character.toUpperCase(toString.charAt(0)) + toString.substring(1);
  }
}
