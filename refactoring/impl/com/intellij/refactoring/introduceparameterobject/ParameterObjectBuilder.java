package com.intellij.refactoring.introduceparameterobject;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.javadoc.PsiDocComment;
import org.jetbrains.annotations.NonNls;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class ParameterObjectBuilder {
    private String className = null;
    private String packageName = null;
    private final List<ParameterSpec> fields = new ArrayList<ParameterSpec>(5);
    private final List<PsiTypeParameter> typeParams = new ArrayList<PsiTypeParameter>();
    private CodeStyleSettings settings = null;

    public void setClassName(String className) {
        this.className = className;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void addField(PsiParameter variable, boolean setterRequired) {
        final ParameterSpec field = new ParameterSpec(variable, setterRequired);
        fields.add(field);
    }

    public void setTypeArguments(List<PsiTypeParameter> typeParams) {
        this.typeParams.clear();
        this.typeParams.addAll(typeParams);
    }

    public void setCodeStyleSettings(CodeStyleSettings settings) {
        this.settings = settings;
    }

    public String buildBeanClass() throws IOException {
        @NonNls final StringBuffer out = new StringBuffer(1024);
        if (packageName.length() > 0) out.append("package " + packageName + ';');
        out.append('\n');
        out.append("public class " + className);
        if (!typeParams.isEmpty()) {
            out.append('<');
            boolean first = true;
            for (PsiTypeParameter typeParam : typeParams) {
                if (!first) {
                    out.append(',');
                }
                out.append(typeParam.getText());
                first = false;
            }
            out.append('>');
        }
        out.append('\n');

        out.append('{');
        outputFields(out);
        outputConstructor(out);
        outputGetters(out);
        outputSetters(out);
        out.append("}\n");
        return out.toString();
    }

    private void outputSetters(StringBuffer out) {
        for (final ParameterSpec field : fields) {
            outputSetter(field, out);
        }
    }

    private void outputGetters(StringBuffer out) {
        for (final ParameterSpec field : fields) {
            outputGetter(field, out);
        }
    }

    private void outputFields(StringBuffer out) {
        for (final ParameterSpec field : fields) {
            outputField(field, out);
        }
    }

    private void outputSetter(ParameterSpec field, @NonNls StringBuffer out) {
        if (!field.isSetterRequired()) {
            return;
        }
        final PsiParameter parameter = field.getParameter();
        final PsiType type = parameter.getType();
        final String typeText;
        if (parameter.isVarArgs()) {
            typeText = ((PsiArrayType) type).getComponentType().getCanonicalText() + "...";
        } else {
            typeText = type.getCanonicalText();
        }
        final String name = calculateStrippedName(parameter);
        final String capitalizedName = StringUtil.capitalize(name);
        final String parameterName =
                settings.PARAMETER_NAME_PREFIX + name + settings.PARAMETER_NAME_SUFFIX;

        out.append("\tpublic void set" + capitalizedName + '(');
        outputAnnotationString(parameter, out);
        out.append(settings.GENERATE_FINAL_PARAMETERS?"final " : "");
        out.append(' ' +typeText + ' ' + parameterName + ")\n");
        out.append("\t{\n");
        final String prefix = settings.FIELD_NAME_PREFIX;
        final String suffix = settings.FIELD_NAME_SUFFIX;
        final String fieldName = prefix + name + suffix;
      generateFieldAssignment(out, parameterName, fieldName);
      out.append("\t}\n");
    }

  private static void generateFieldAssignment(final StringBuffer out, final String parameterName, final String fieldName) {
    if (fieldName.equals(parameterName)) {
        out.append("\t\tthis." + fieldName + " = " + parameterName + ";\n");
    } else {
        out.append("\t\t" + fieldName + " = " + parameterName + ";\n");
    }
  }

  @NonNls
    private String calculateStrippedName(PsiParameter parameter) {
        @NonNls String name = parameter.getName();
        if (name.startsWith(settings.PARAMETER_NAME_PREFIX)) {
            name = name.substring(settings.PARAMETER_NAME_PREFIX.length());
        }
        if (name.endsWith(settings.PARAMETER_NAME_SUFFIX)) {
            name = name.substring(0, name.length() - settings.PARAMETER_NAME_SUFFIX.length());
        }
        return name;
    }

    private void outputGetter(ParameterSpec field, @NonNls StringBuffer out) {
        final PsiParameter parameter = field.getParameter();
        final PsiType type = parameter.getType();
        final String typeText;
        if (parameter.isVarArgs()) {
            typeText = ((PsiArrayType) type).getComponentType().getCanonicalText() + "[]";
        } else {
            typeText = type.getCanonicalText();
        }
        final String name = calculateStrippedName(parameter);
        final String capitalizedName = StringUtil.capitalize(name);
        if (PsiType.BOOLEAN.equals(type)) {
            out.append('\t');
            outputAnnotationString(parameter, out);
            out.append(" public "+ typeText + " is" + capitalizedName + "()\n");
        } else {
            out.append('\t');
            outputAnnotationString(parameter, out);
            out.append(" public " +typeText + " get" + capitalizedName + "()\n");
        }
        out.append("\t{\n");
        final String prefix;
        final String suffix;
        if (parameter.hasModifierProperty(PsiModifier.STATIC)) {
            prefix = settings.STATIC_FIELD_NAME_PREFIX;
            suffix = settings.STATIC_FIELD_NAME_SUFFIX;
        } else {
            prefix = settings.FIELD_NAME_PREFIX;
            suffix = settings.FIELD_NAME_SUFFIX;
        }
        final String fieldName = prefix + name + suffix;
        out.append("\t\treturn " + fieldName + ";\n");
        out.append("\t}\n");
    }

    private void outputConstructor(@NonNls StringBuffer out) {
        out.append("\tpublic " + className + '(');
        for (Iterator<ParameterSpec> iterator = fields.iterator(); iterator.hasNext();) {
            final ParameterSpec field = iterator.next();
            final PsiParameter parameter = field.getParameter();
            final PsiType type = parameter.getType();
            final String typeText;
            if (parameter.isVarArgs()) {
                typeText = ((PsiArrayType) type).getComponentType().getCanonicalText() + "...";
            } else {
                typeText = type.getCanonicalText();
            }
            final String name = calculateStrippedName(parameter);
            final String parameterName =
                    settings.PARAMETER_NAME_PREFIX + name + settings.PARAMETER_NAME_SUFFIX;
            outputAnnotationString(parameter, out);
            out.append(settings.GENERATE_FINAL_PARAMETERS ? "final " : "");
            out.append(' ' +typeText + ' ' + parameterName);
            if (iterator.hasNext()) {
                out.append(", ");
            }
        }
        out.append(")\n");
        out.append("\t{\n");
        for (final ParameterSpec field : fields) {
            final PsiParameter parameter = field.getParameter();
            final String prefix;
            final String suffix;
            if (parameter.hasModifierProperty(PsiModifier.STATIC)) {
                prefix = settings.STATIC_FIELD_NAME_PREFIX;
                suffix = settings.STATIC_FIELD_NAME_SUFFIX;
            } else {
                prefix = settings.FIELD_NAME_PREFIX;
                suffix = settings.FIELD_NAME_SUFFIX;
            }
            final String name = calculateStrippedName(parameter);
            final String fieldName = prefix + name + suffix;
            final String parameterName =
                    settings.PARAMETER_NAME_PREFIX + name + settings.PARAMETER_NAME_SUFFIX;
          generateFieldAssignment(out, parameterName, fieldName);
        }
        out.append("\t}\n");
    }

    private void outputField(ParameterSpec field, StringBuffer out) {
        final PsiParameter parameter = field.getParameter();
        final PsiDocComment docComment = getJavadocForVariable(parameter);
        if (docComment != null) {
            out.append(docComment.getText());
            out.append('\n');
        }
        final PsiType type = parameter.getType();
        final String typeText;
        if (parameter.isVarArgs()) {
            final PsiType componentType = ((PsiArrayType) type).getComponentType();
            typeText = componentType.getCanonicalText() + "[]";
        } else {
            typeText = type.getCanonicalText();
        }
        final String name = calculateStrippedName(parameter);
        @NonNls String modifierString = "private ";
        final String prefix = settings.FIELD_NAME_PREFIX;
        final String suffix = settings.FIELD_NAME_SUFFIX;
        if (!field.isSetterRequired()) {
            modifierString += "final ";
        }
        outputAnnotationString(parameter, out);
        out.append('\t' + modifierString + typeText + ' ' + prefix + name + suffix + ";\n");
    }

    private void outputAnnotationString(PsiParameter parameter, StringBuffer out) {
        final PsiModifierList modifierList = parameter.getModifierList();
        final PsiAnnotation[] annotations = modifierList.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
            final PsiJavaCodeReferenceElement reference = annotation.getNameReferenceElement();
            if (reference == null) {
                continue;
            }
            final PsiClass annotationClass = (PsiClass) reference.resolve();
            if (annotationClass != null) {
                final PsiAnnotationParameterList parameterList = annotation.getParameterList();
                final String annotationText = '@' + annotationClass.getQualifiedName() + parameterList.getText();
                out.append(annotationText);
            }
        }
    }

    private static PsiDocComment getJavadocForVariable(PsiVariable variable) {
        final PsiElement[] children = variable.getChildren();
        for (PsiElement child : children) {
            if (child instanceof PsiDocComment) {
                return (PsiDocComment) child;
            }
        }
        return null;
    }

}

