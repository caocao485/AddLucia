package org.example.processors;

import com.google.auto.service.AutoService;
import org.example.FilerUtil;
import org.example.annotations.AutoServ;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.SimpleAnnotationValueVisitor9;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Processes {@link AutoServ} annotations and generates the service provider configuration files
 * described in {@link java.util.ServiceLoader}
 *
 * <p>Processor Options:
 *
 * <ul>
 *   <li>debug - turns on debug statements
 *   <li>verify - turn on verify implementations
 * </ul>
 */
@SupportedOptions({"debug", "verify"})
@AutoService(Processor.class)
public class AutoServProcessor extends AbstractProcessor {

  /**
   * Maps the classes names of service provider interfaces to the class name of the concrete classes
   * which implement them.
   *
   * <p>For example {@code "org.example.TestInterface.java"} -> {@code "com.lucia.usecase.Bit"}
   */
  private final Map<String, Set<String>> providers = new HashMap<>();

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return Set.of(AutoServ.class.getName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  /**
   *
   *
   * <ol>
   *   <li>For each class annotated with {@link AutoServ}
   *       <ul>
   *         <li>Verify the {@link AutoServ} interface value is correct *
   *         <li>Categorize the class by its service interface *
   *       </ul>
   *   <li>For each {@link AutoServ} interface
   *       <ul>
   *         <li>Create a file named {@code META-INF/services/<interface>}
   *         <li>For each {@link AutoServ} annotated class for this interface
   *             <ul>
   *               <li>Create an entry in the file
   *             </ul>
   *       </ul>
   * </ol>
   */
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

    try {
      if (roundEnv.processingOver()) {
        generateConfigFiles();
      } else {
        processAnnotation(annotations, roundEnv);
      }
      return true;
    } catch (Exception e) {
      StringWriter writer = new StringWriter();
      e.printStackTrace(new PrintWriter(writer));
      fatalError(writer.toString());
      return true;
    }
  }

  private void processAnnotation(
      Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(AutoServ.class);

    log(annotations.toString());
    log(elements.toString());

    for (Element element : elements) {
      TypeElement implementerElement = (TypeElement) element;
      List<? extends AnnotationMirror> mirrors = implementerElement.getAnnotationMirrors();
      AnnotationMirror mirror = this.getAnnotationMirror(mirrors, AutoServ.class).get();
      Set<DeclaredType> interfaceTypes = this.getValueFieldOfClass(mirror, "value");

      if (interfaceTypes.isEmpty()) {
        error("No service interfaces provided for element!", element, mirror);
        continue;
      }

      for (DeclaredType interfaceType : interfaceTypes) {
        TypeElement interfaceTypeElement = (TypeElement) interfaceType.asElement();

        log("provider interface: " + interfaceTypeElement.getQualifiedName());
        log("provider implementer: " + implementerElement.getQualifiedName());

        if (checkImplementer(implementerElement, interfaceTypeElement, mirror)) {
          String interfaceQualifiedName =
              this.getBinaryName(
                  interfaceTypeElement, interfaceTypeElement.getSimpleName().toString());
          String implementerQualifiedName =
              this.getBinaryName(implementerElement, implementerElement.getSimpleName().toString());
          this.addToProviders(interfaceQualifiedName, implementerQualifiedName);

        } else {
          String message =
              "ServiceProviders must implement their service provider interface. "
                  + implementerElement.getQualifiedName()
                  + " does not implement "
                  + interfaceTypeElement.getQualifiedName();
          error(message, implementerElement, mirror);
        }
      }
    }
    //    for(Element element : roundEnv.getElementsAnnotatedWith(AutoServ.class)){
    //      if(element.getKind().isClass()){
    //        TypeElement typeElement  = (TypeElement)element;
    //        AutoServ autoServ = typeElement.getAnnotation(AutoServ.class);
    //        TypeMirror interfaceType = null;
    //        try{
    //        Class<?> interfaceClass = autoServ.value()[0];
    //        }catch (MirroredTypesException exception){
    //          interfaceType = exception.getTypeMirrors().get(0);
    //        }
    //
    //        if(processingEnv.getTypeUtils().isSubtype(element.asType(),interfaceType)){
    //          configs.add(typeElement.getQualifiedName().toString());
    //        }
    //
    //      }
    //    }
  }

  private void addToProviders(String interfaceQualifiedName, String implementerElementName) {
    if (providers.containsKey(interfaceQualifiedName)) {
      Set<String> implies = providers.get(interfaceQualifiedName);
      implies.add(implementerElementName);
    } else {
      Set<String> implies = new HashSet<>();
      implies.add(implementerElementName);
      providers.put(interfaceQualifiedName, implies);
    }
  }

  private void generateConfigFiles() {
    Filer fileUtil = processingEnv.getFiler();

    for (String interfaceName : providers.keySet()) {
      String resourceFile = "META-INF/services/" + interfaceName;

      log("Working on resource file: " + resourceFile);

      Set<String> allServices = new HashSet<>();

      try {
        FileObject existingFileObject =
            fileUtil.getResource(StandardLocation.CLASS_OUTPUT, "", resourceFile);
        log("Looking for existing resource file at " + existingFileObject);
        Collection<String> oldServices =
            FilerUtil.readServiceFile(existingFileObject.openInputStream());
        log("Existing service entries: " + oldServices);
        allServices.addAll(oldServices);
      } catch (IOException ioException) {
        log("Resource file is not exist");
      }

      Set<String> newServices = providers.get(interfaceName);
      if (allServices.containsAll(newServices)) {
        log("All services are existed in resource file");
        return;
      }

      allServices.addAll(newServices);
      log("New service file contents: " + allServices);
      try {
        FileObject outFileObject =
            fileUtil.createResource(StandardLocation.CLASS_OUTPUT, "", resourceFile);

        try (OutputStream outputStream = outFileObject.openOutputStream()) {
          FilerUtil.writeServiceFile(allServices, outputStream);
        }

        log("Wrote to: " + outFileObject.toUri());
      } catch (IOException ioException) {
        fatalError("Unable to create " + resourceFile + ", " + ioException);
        return;
      }
    }
    //    String servicePath = "META-INF/services";
    //    FileObject fileObject =
    //        processingEnv
    //            .getFiler()
    //            .createResource(
    //                StandardLocation.CLASS_OUTPUT,
    //                "",
    //                    servicePath + "/org.example.TestInterface");
    //    try (Writer writer = fileObject.openWriter()) {
    //      for(String string : configMaps.get("org.example.TestInterface")){
    //        writer.append(string);
    //      }
    //
    //    }
  }

  private String getBinaryName(TypeElement typeElement, String className) {
    Element fatherElement = typeElement.getEnclosingElement();
    if (fatherElement instanceof PackageElement) {
      return ((PackageElement) fatherElement).getQualifiedName() + "." + className;
    }

    TypeElement fatherTypeElement = (TypeElement) fatherElement;

    return getBinaryName(fatherTypeElement, fatherTypeElement.getSimpleName() + "$" + className);
  }

  private boolean checkImplementer(
      TypeElement implementerElement,
      TypeElement interfaceTypeElement,
      AnnotationMirror annotationMirror) {
    if (!processingEnv.getOptions().containsKey("verify")) {
      return true;
    }

    Types typeUtil = processingEnv.getTypeUtils();
    if (typeUtil.isSubtype(implementerElement.asType(), interfaceTypeElement.asType())) {
      return true;
    }

    if (typeUtil.isSubtype(
        implementerElement.asType(), typeUtil.erasure(interfaceTypeElement.asType()))) {
      if (!rawTypesSuppressed(implementerElement)) {
        warning(
            "Service provider "
                + interfaceTypeElement
                + " is generic, so it can't be named exactly by @AutoService."
                + " If this is OK, add @SuppressWarnings(\"rawtypes\").",
            implementerElement,
            annotationMirror);
      }
      return true;
    }

    return false;
  }

  private boolean rawTypesSuppressed(Element element) {
    for (; element != null; element = element.getEnclosingElement()) {
      SuppressWarnings suppress = element.getAnnotation(SuppressWarnings.class);
      if (suppress != null && Arrays.asList(suppress.value()).contains("rawtypes")) {
        return true;
      }
    }
    return false;
  }

  private Optional<AnnotationMirror> getAnnotationMirror(
      Collection<? extends AnnotationMirror> annotationMirrors,
      Class<? extends Annotation> annotationClass) {
    String className = annotationClass.getCanonicalName();

    for (AnnotationMirror annotationMirror : annotationMirrors) {
      TypeElement typeElement = (TypeElement) annotationMirror.getAnnotationType().asElement();
      if (typeElement.getQualifiedName().toString().equals(className)) {
        return Optional.of(annotationMirror);
      }
    }
    return Optional.empty();
  }

  private Set<DeclaredType> getValueFieldOfClass(AnnotationMirror annotationMirror, String name) {
    AnnotationValue annotationValue = null;
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        annotationMirror.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().toString().equals(name)) {
        annotationValue = entry.getValue();
        break;
      }
    }

    if (annotationValue == null) {
      fatalError(annotationMirror.getAnnotationType() + "does not define an element" + name);
    }

    return annotationValue.accept(
        new SimpleAnnotationValueVisitor9<Set<DeclaredType>, Void>() {
          @Override
          public Set<DeclaredType> visitType(TypeMirror t, Void unused) {
            return Set.of((DeclaredType) t);
          }

          @Override
          public Set<DeclaredType> visitArray(List<? extends AnnotationValue> vals, Void unused) {
            return vals.stream()
                .flatMap(value -> value.accept(this, null).stream())
                .collect(Collectors.toSet());
          }
        },
        null);
  }

  private void log(String message) {
    if (!processingEnv.getOptions().containsKey("debug")) {
      return;
    }
    processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
  }

  private void warning(String msg, Element element, AnnotationMirror annotation) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, msg, element, annotation);
  }

  private void error(String msg, Element element, AnnotationMirror annotationMirror) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, msg, element, annotationMirror);
  }

  private void fatalError(String msg) {
    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "FatalError: " + msg);
  }
}
