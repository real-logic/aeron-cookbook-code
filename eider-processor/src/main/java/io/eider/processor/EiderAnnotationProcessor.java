/*
 *
 *  * Copyright 2019-2020 eleventy7
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  * https://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package io.eider.processor;

import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import io.eider.annotation.EiderObject;
import io.eider.sample.EiderSerializable;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;

@SupportedAnnotationTypes( {
    "io.eider.annotation.EiderObject"
})
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class EiderAnnotationProcessor extends AbstractProcessor
{
    private int sequence = 0;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv)
    {
        super.init(processingEnv);
        writeNote(processingEnv, "Eider is ready");
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
    {
        if (roundEnv.processingOver() || annotations.isEmpty())
        {
            return false;
        }

        for (Element el : roundEnv.getElementsAnnotatedWith(EiderObject.class))
        {
            boolean continueProcessing = false;
            if (el instanceof TypeElement)
            {
                continueProcessing = true;
            }

            if (!continueProcessing)
            {
                break;
            }

            TypeElement element = (TypeElement) el;
            processObject(processingEnv, element);
        }

        return true;
    }

    private void processObject(ProcessingEnvironment processingEnv, TypeElement typeElement)
    {
        final String objectType = typeElement.getSimpleName().toString();
        final String classNameInput = typeElement.getSimpleName().toString();
        final String classNameGen = classNameInput + "Eider";
        final String packageName = typeElement.getQualifiedName().toString();
        final String packageNameGen = packageName.replace(classNameInput, "gen");
        sequence += 1;
        writeNote(processingEnv, "Eider is processing " + packageName + " - item: " + sequence);

        MethodSpec read = MethodSpec.methodBuilder("read")
            .addModifiers(Modifier.PUBLIC)
            .returns(void.class)
            .addParameter(DirectBuffer.class, "buffer")
            .addParameter(int.class, "offset")
            .addStatement("$T.out.println($S)", System.class, "Hello, JavaPoet!")
            .build();

        MethodSpec write = MethodSpec.methodBuilder("write")
            .addModifiers(Modifier.PUBLIC)
            .returns(void.class)
            .addParameter(MutableDirectBuffer.class, "buffer")
            .addParameter(int.class, "offset")
            .addStatement("$T.out.println($S)", System.class, "Hello, JavaPoet!")
            .build();

        MethodSpec eiderId = MethodSpec.methodBuilder("eiderId")
            .addModifiers(Modifier.PUBLIC)
            .returns(int.class)
            .addStatement("return " + sequence)
            .build();

        TypeSpec generated = TypeSpec.classBuilder(classNameGen)
            .addSuperinterface(EiderSerializable.class)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addMethod(read)
            .addMethod(write)
            .addMethod(eiderId)
            .build();

        JavaFile javaFile = JavaFile.builder(packageNameGen, generated)
            .build();

        try
        { // write the file
            JavaFileObject source = processingEnv.getFiler()
                .createSourceFile(packageNameGen + "." + classNameGen);
            Writer writer = source.openWriter();
            javaFile.writeTo(writer);
            writer.flush();
            writer.close();
        } catch (IOException e)
        {
            // Note: calling e.printStackTrace() will print IO errors
            // that occur from the file already existing after its first run, this is normal
        }
    }

    @Override
    public Set<String> getSupportedOptions()
    {
        final Set<String> options = new HashSet<>();
        return options;
    }

    private void writeNote(ProcessingEnvironment pe, String note)
    {
        pe.getMessager().printMessage(Diagnostic.Kind.NOTE, note);
    }

}
